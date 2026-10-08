package com.aicompose.feature.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** 剪影填充色 —— 取高饱和青绿, 冷暖背景上都看得清 */
private val SILHOUETTE_FILL = Color(0xFF2BE08C)

/** 剪影描边色, 垫在填充下面, 亮背景上也能分辨轮廓 */
private val SILHOUETTE_EDGE = Color(0xCC0B3B2A)

/**
 * 画面引导叠加层。
 *
 * 没有人体检测 —— 只把「参考姿势的剪影」半透明盖在取景框中央, 被拍者把身体
 * 对进剪影里就行, 本质是让「景」和「人」配合。
 */
@Composable
fun SilhouetteOverlay(
    silhouette: ImageBitmap?,
    drawThirds: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (drawThirds) drawThirds()

        val img = silhouette ?: return@Canvas
        // 按「高度不超过 84% 画布」摆放, 但坐姿剪影很宽 (宽高比可到 1.5),
        // 必须再用宽度 90% 画布兜底, 否则会横向溢出屏幕。
        val scale = minOf(size.height * 0.84f / img.height, size.width * 0.90f / img.width)
        val w = img.width * scale
        val h = img.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f

        drawSilhouette(img, left, top, w, h)
    }
}

private fun DrawScope.drawSilhouette(
    img: ImageBitmap,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
) {
    // 先画一圈放大的深色描边, 再把填充盖上, 轮廓更清楚
    val edge = 5f
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset((left - edge).roundToInt(), (top - edge).roundToInt()),
        dstSize = IntSize((w + edge * 2).roundToInt(), (h + edge * 2).roundToInt()),
        alpha = 0.55f,
        colorFilter = ColorFilter.tint(SILHOUETTE_EDGE),
    )
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = 0.5f,
        colorFilter = ColorFilter.tint(SILHOUETTE_FILL),
    )
}

private fun DrawScope.drawThirds() {
    val color = Color.White.copy(alpha = 0.30f)
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 2f)
    drawLine(color, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), strokeWidth = 2f)
    drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 2f)
    drawLine(color, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), strokeWidth = 2f)
}