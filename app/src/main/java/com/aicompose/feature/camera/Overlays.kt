package com.aicompose.feature.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aicompose.core.guide.AlignmentState
import com.aicompose.core.guide.CompositionEngine
import com.aicompose.core.guide.Joint
import com.aicompose.core.guide.PoseSpec
import com.aicompose.core.guide.PoseTemplate
import com.aicompose.core.guide.Tilt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 引导配色: 白 (未就绪) → 琥珀 (接近) → 翡翠绿 (已对齐) */
object GuideColors {
    val IDLE = Color.White.copy(alpha = 0.55f)
    val NEAR = Color(0xFFFFB300)
    val MATCHED = Color(0xFF00E676)

    fun of(state: AlignmentState): Color = when (state) {
        AlignmentState.IDLE -> IDLE
        AlignmentState.NEAR -> NEAR
        AlignmentState.MATCHED -> MATCHED
    }
}

/** 经典九宫格三分线 */
@Composable
fun GridThirds(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.32f),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (i in 1..2) {
            drawLine(color, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 1.5f)
            drawLine(color, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1.5f)
        }
    }
}

/**
 * 物理水平仪 —— 参考线随手机 roll 旋转, 中间留缺口不遮挡主体。
 * |roll| < 1.5° 时变绿。
 */
@Composable
fun LevelLineOverlay(tilt: Tilt, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = center
        val len = size.width * 0.55f
        val gap = 26f
        val color = if (tilt.level) GuideColors.MATCHED else Color.White.copy(alpha = 0.85f)
        val theta = Math.toRadians(tilt.rollDeg.toDouble())
        val dx = cos(theta).toFloat()
        val dy = sin(theta).toFloat()
        drawLine(
            color,
            Offset(c.x - dx * len / 2f, c.y - dy * len / 2f),
            Offset(c.x - dx * gap, c.y - dy * gap),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(c.x + dx * gap, c.y + dy * gap),
            Offset(c.x + dx * len / 2f, c.y + dy * len / 2f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round,
        )
        drawCircle(color, radius = 3f, center = c)
    }
}

/**
 * 骨架叠加层:
 * - 用户实时骨架: 分析帧坐标经 FILL_CENTER 裁剪映射到屏幕
 * - 模板参考虚线骨架: 动态锚定到被摄者 —— 髋关节对齐参考框中心,
 *   肩宽等比缩放 (1:1 姿势比对), 无人时回退居中方框
 */
@Composable
fun PoseSkeletonOverlay(
    userJoints: Map<Int, com.aicompose.core.guide.DetectedJoint>?,
    frameWidth: Float,
    frameHeight: Float,
    template: PoseTemplate?,
    mirrored: Boolean,
    state: AlignmentState,
    modifier: Modifier = Modifier,
) {
    val color = GuideColors.of(state)
    Canvas(modifier = modifier) {
        // 分析帧 → 屏幕坐标 (FILL_CENTER) 的变换
        var userTransform: ((Float, Float) -> Offset)? = null
        if (userJoints != null && frameWidth > 0f && frameHeight > 0f) {
            val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
            val dx = (size.width - frameWidth * scale) / 2f
            val dy = (size.height - frameHeight * scale) / 2f
            val tx: (Float, Float) -> Offset = { x, y ->
                Offset(dx + x * frameWidth * scale, dy + y * frameHeight * scale)
            }
            userTransform = tx
            drawSkeleton(
                joints = userJoints.mapValues { Joint(it.key, it.value.x, it.value.y) },
                transform = tx,
                color = color.copy(alpha = 0.95f),
                strokeWidth = 5f,
                dashed = false,
                headRadius = 10f,
            )
        }

        // 模板参考虚线骨架 (随选中卡片动态变化)
        if (template != null) {
            val pts = if (mirrored) {
                template.joints.mapValues { Joint(it.key, 1f - it.value.x, it.value.y) }
            } else {
                template.joints
            }

            // 自适应: 髋中心锚点 + 肩宽比例缩放
            var side: Float? = null
            var left: Float? = null
            var top: Float? = null
            val tx = userTransform
            if (userJoints != null && tx != null) {
                val vis = 0.4f
                val hipL = userJoints[23]?.takeIf { it.visibility >= vis }
                val hipR = userJoints[24]?.takeIf { it.visibility >= vis }
                val shL = userJoints[11]?.takeIf { it.visibility >= vis }
                val shR = userJoints[12]?.takeIf { it.visibility >= vis }
                val tplShL = pts[11]
                val tplShR = pts[12]
                if (hipL != null && hipR != null && shL != null && shR != null &&
                    tplShL != null && tplShR != null
                ) {
                    // 屏幕空间肩宽
                    val a = tx(shL.x, shL.y)
                    val b = tx(shR.x, shR.y)
                    val shoulderPx = kotlin.math.hypot(a.x - b.x, a.y - b.y)
                    // 模板空间肩宽 (归一化单位)
                    val tplShoulder = kotlin.math.hypot(tplShL.x - tplShR.x, tplShL.y - tplShR.y)
                    if (shoulderPx > 24f && tplShoulder > 1e-3f) {
                        side = (shoulderPx / tplShoulder)
                            .coerceIn(size.minDimension * 0.30f, size.minDimension * 1.6f)
                        // 模板髋中心
                        val hcx = (pts[23]!!.x + pts[24]!!.x) / 2f
                        val hcy = (pts[23]!!.y + pts[24]!!.y) / 2f
                        // 用户髋中心 (屏幕坐标)
                        val hipCenter = tx((hipL.x + hipR.x) / 2f, (hipL.y + hipR.y) / 2f)
                        left = hipCenter.x - hcx * side
                        top = hipCenter.y - hcy * side
                    }
                }
            }
            // 无人/关键点不足 → 居中默认框
            if (side == null || left == null || top == null) {
                side = min(size.width, size.height * 0.82f) * 0.92f
                left = (size.width - side) / 2f
                top = (size.height - side) / 2f - size.height * 0.03f
            }
            val s = side
            val l = left
            val t = top
            drawSkeleton(
                joints = pts,
                transform = { x, y -> Offset(l + x * s, t + y * s) },
                color = color.copy(alpha = 0.6f),
                strokeWidth = 3.5f,
                dashed = true,
                headRadius = 9f,
            )
        }
    }
}

/**
 * 站位框 (移植自 aicamera): 按 PoseSpec 的目标重心/脚线/人物占比画出「人该站哪儿」。
 * 虚线框 + 顶部目标点; 人走进框里时随 [state] 一起变绿。
 */
@Composable
fun PositionBoxOverlay(
    spec: PoseSpec?,
    frameWidth: Float,
    frameHeight: Float,
    mirrored: Boolean,
    state: AlignmentState,
    modifier: Modifier = Modifier,
) {
    if (spec == null) return
    val color = GuideColors.of(state)
    Canvas(modifier = modifier) {
        // 分析帧 → 屏幕 (FILL_CENTER), 与 PoseSkeletonOverlay 同一套变换
        val fw = frameWidth
        val fh = frameHeight
        val tx: ((Float, Float) -> Offset)? = if (fw > 0f && fh > 0f) {
            val scale = maxOf(size.width / fw, size.height / fh)
            val dx = (size.width - fw * scale) / 2f
            val dy = (size.height - fh * scale) / 2f
            { x: Float, y: Float -> Offset(dx + x * fw * scale, dy + y * fh * scale) }
        } else null

        val cx = if (mirrored) 1f - spec.targetCx else spec.targetCx
        val left: Float
        val top: Float
        val boxW: Float
        val boxH: Float
        if (tx != null) {
            // 脚线 → 头顶 的屏幕距离即目标人物高度 (占比与帧同口径)
            val foot = tx(0f, spec.targetFootY)
            val head = tx(0f, (spec.targetFootY - spec.bboxHRatio).coerceAtLeast(0f))
            boxH = kotlin.math.abs(foot.y - head.y).coerceAtLeast(40f)
            boxW = boxH * spec.boxAspect
            val c = tx(cx, 0f)
            left = c.x - boxW / 2f
            top = foot.y - boxH
        } else {
            boxH = (spec.bboxHRatio * size.height).coerceAtLeast(40f)
            boxW = boxH * spec.boxAspect
            left = cx * size.width - boxW / 2f
            top = spec.targetFootY * size.height - boxH
        }

        drawRoundRect(
            color = color.copy(alpha = 0.75f),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
            ),
        )
        // 顶部目标点: 人物重心该落的位置
        drawCircle(
            color = color.copy(alpha = 0.9f),
            radius = 7f,
            center = Offset(left + boxW / 2f, top),
        )
    }
}

/** 通用骨架绘制 (叠加层与模板卡片共用) */
internal fun DrawScope.drawSkeleton(
    joints: Map<Int, Joint>,
    transform: (Float, Float) -> Offset,
    color: Color,
    strokeWidth: Float,
    dashed: Boolean,
    headRadius: Float,
) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
    for ((a, b) in CompositionEngine.DRAW_BONES) {
        val ja = joints[a] ?: continue
        val jb = joints[b] ?: continue
        drawLine(
            color,
            transform(ja.x, ja.y),
            transform(jb.x, jb.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = effect,
        )
    }
    // 头部圆圈
    joints[0]?.let { nose ->
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = transform(nose.x, nose.y),
                    radius = headRadius,
                )
            )
        }
        drawPath(path, color, style = Stroke(width = strokeWidth))
    }
}
