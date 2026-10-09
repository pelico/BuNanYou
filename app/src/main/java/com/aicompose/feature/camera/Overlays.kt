package com.aicompose.feature.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.aicompose.core.guide.AlignmentState
import com.aicompose.core.guide.CompositionEngine
import com.aicompose.core.guide.Joint
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
 * - 模板参考虚线骨架: 居中方形区域, 形状引导 (镜像开关支持左右对调)
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
        // 用户实时骨架
        if (userJoints != null && frameWidth > 0f && frameHeight > 0f) {
            val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
            val dx = (size.width - frameWidth * scale) / 2f
            val dy = (size.height - frameHeight * scale) / 2f
            drawSkeleton(
                joints = userJoints.mapValues { Joint(it.key, it.value.x, it.value.y) },
                transform = { x, y -> Offset(dx + x * frameWidth * scale, dy + y * frameHeight * scale) },
                color = color.copy(alpha = 0.95f),
                strokeWidth = 5f,
                dashed = false,
                headRadius = 10f,
            )
        }
        // 模板参考虚线骨架 (居中方框)
        if (template != null) {
            val side = min(size.width, size.height * 0.82f) * 0.92f
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f - size.height * 0.03f
            val pts = if (mirrored) {
                template.joints.mapValues { Joint(it.key, 1f - it.value.x, it.value.y) }
            } else {
                template.joints
            }
            drawSkeleton(
                joints = pts,
                transform = { x, y -> Offset(left + x * side, top + y * side) },
                color = color.copy(alpha = 0.6f),
                strokeWidth = 3.5f,
                dashed = true,
                headRadius = 9f,
            )
        }
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
