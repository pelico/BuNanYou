package com.aicompose.core.vision

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 图像预处理 —— 与 Python 端 gaic_infer.py 保持完全一致,
 * 保证 ONNX 输入分布与训练时相同。
 */
object ImagePreprocessor {

    private val RGB_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val RGB_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** 缩放后的目标短边 */
    const val TARGET_SHORT_SIDE = 256f

    data class Preprocessed(
        /** NCHW float 数组, 长度 = 3 * h * w */
        val chwFloat: FloatArray,
        val resizedWidth: Int,
        val resizedHeight: Int,
        /** 原图高 / 缩放后高 */
        val scaleHeight: Float,
        /** 原图宽 / 缩放后宽 */
        val scaleWidth: Float,
    )

    /**
     * 缩放 + 归一化 + HWC->CHW。
     */
    fun preprocess(bitmap: Bitmap): Preprocessed {
        val scale = TARGET_SHORT_SIDE / minOf(bitmap.width, bitmap.height).toFloat()
        val h = (roundToNearest32(bitmap.height * scale)).coerceAtLeast(32)
        val w = (roundToNearest32(bitmap.width * scale)).coerceAtLeast(32)

        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)
        if (resized != bitmap) resized.recycle()

        // HWC RGB -> 归一化 -> CHW
        val chw = FloatArray(3 * h * w)
        val planeSize = h * w
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 256f
            val g = ((px shr 8) and 0xFF) / 256f
            val b = (px and 0xFF) / 256f
            chw[i] = (r - RGB_MEAN[0]) / RGB_STD[0]
            chw[planeSize + i] = (g - RGB_MEAN[1]) / RGB_STD[1]
            chw[2 * planeSize + i] = (b - RGB_MEAN[2]) / RGB_STD[2]
        }

        return Preprocessed(
            chwFloat = chw,
            resizedWidth = w,
            resizedHeight = h,
            scaleHeight = bitmap.height.toFloat() / h,
            scaleWidth = bitmap.width.toFloat() / w,
        )
    }

    /**
     * 生成候选裁剪框 (与 GAIC generate_bboxes 一致)。
     * 12x12 网格, 只取左上角 0~3 格 与 右下角 8~11 格组成的框,
     * 并过滤面积过小 / 比例失衡的。
     *
     * 返回: List<[y1, x1, y2, x2]> (缩放图坐标)
     */
    fun generateBboxes(resizedHeight: Int, resizedWidth: Int): List<FloatArray> {
        val bins = 12f
        val stepH = resizedHeight / bins
        val stepW = resizedWidth / bins
        val result = ArrayList<FloatArray>(256)

        for (x1 in 0 until 4) {
            for (y1 in 0 until 4) {
                for (x2 in 8 until 12) {
                    for (y2 in 8 until 12) {
                        val dx = x2 - x1
                        val dy = y2 - y1
                        if (dx * dy <= 0.4999f * bins * bins) continue
                        val ratio = dy * stepW / dx / stepH
                        if (ratio <= 0.5f || ratio >= 2.0f) continue
                        result.add(
                            floatArrayOf(
                                stepH * (0.5f + x1),
                                stepW * (0.5f + y1),
                                stepH * (0.5f + x2),
                                stepW * (0.5f + y2),
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun roundToNearest32(v: Float): Int =
        floor(v / 32f).roundToInt() * 32
}
