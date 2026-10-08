package com.aicompose.feature.pose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aicompose.core.vision.pose.AdviceLevel
import com.aicompose.core.vision.pose.DetectedPose
import com.aicompose.core.vision.pose.PoseAdvice
import com.aicompose.core.vision.pose.PoseCoach
import com.aicompose.core.vision.pose.PoseTemplate

private val POSE_COLOR = Color(0xFF0FDC78)
private val TARGET_COLOR = Color(0xFFFFC857)

/**
 * 把归一化坐标映射到绘制区域。
 *
 * @param fillCenter true = 相机预览的 FILL_CENTER (会裁切); false = 图片 Fit 显示 (不裁切)
 */
private fun mapper(
    contentW: Int,
    contentH: Int,
    viewW: Float,
    viewH: Float,
    fillCenter: Boolean,
): (Float, Float) -> Offset {
    if (contentW <= 0 || contentH <= 0 || viewW <= 0f || viewH <= 0f) {
        return { _, _ -> Offset.Zero }
    }
    val scale = if (fillCenter) {
        maxOf(viewW / contentW, viewH / contentH)
    } else {
        minOf(viewW / contentW, viewH / contentH)
    }
    val dx = (viewW - contentW * scale) / 2f
    val dy = (viewH - contentH * scale) / 2f
    return { x, y -> Offset(dx + x * contentW * scale, dy + y * contentH * scale) }
}

/**
 * 骨架叠加层: 绿色是当前姿势, 琥珀色虚线是参考姿势 (照着摆)。
 */
@Composable
fun PoseOverlay(
    pose: DetectedPose?,
    target: PoseTemplate?,
    contentWidth: Int,
    contentHeight: Int,
    fillCenter: Boolean,
    drawThirds: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (drawThirds) {
            val third = Color.White.copy(alpha = 0.35f)
            val w = size.width
            val h = size.height
            drawLine(third, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 2f)
            drawLine(third, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), strokeWidth = 2f)
            drawLine(third, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 2f)
            drawLine(third, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), strokeWidth = 2f)
        }

        if (contentWidth <= 0 || contentHeight <= 0) return@Canvas
        val map = mapper(contentWidth, contentHeight, size.width, size.height, fillCenter)

        // 参考姿势 (先画, 垫在下面)
        if (target != null) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
            PoseCoach.templateSegments(pose, target).forEach { (a, b) ->
                drawLine(
                    color = TARGET_COLOR.copy(alpha = 0.85f),
                    start = map(a.x, a.y),
                    end = map(b.x, b.y),
                    strokeWidth = 7f,
                    pathEffect = dash,
                )
            }
        }

        // 当前姿势
        if (pose != null) {
            PoseCoach.poseSegments(pose).forEach { (a, b) ->
                drawLine(
                    color = POSE_COLOR,
                    start = map(a.x, a.y),
                    end = map(b.x, b.y),
                    strokeWidth = 7f,
                )
            }
            pose.visible.forEach { kp ->
                drawCircle(
                    color = POSE_COLOR,
                    radius = 6f,
                    center = map(kp.x, kp.y),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

/** 建议列表 */
@Composable
fun AdviceList(advice: List<PoseAdvice>, modifier: Modifier = Modifier) {
    if (advice.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        advice.forEach { item ->
            val color = when (item.level) {
                AdviceLevel.WARN -> Color(0xFFFF6B6B)
                AdviceLevel.TIP -> Color(0xFFFFD166)
                AdviceLevel.GOOD -> Color(0xFF7BE495)
            }
            Text(
                text = "· ${item.text}",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp),
            )
        }
    }
}

/** 半透明底衬, 保证叠加在画面上也能看清文字 */
@Composable
fun AdvicePanel(advice: List<PoseAdvice>, modifier: Modifier = Modifier) {
    if (advice.isEmpty()) return
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        AdviceList(advice)
    }
}