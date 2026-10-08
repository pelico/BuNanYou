package com.aicompose.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * 基于 GAIC (Grid-Anchor-based Image Cropping) ONNX 模型的本地构图分析器。
 *
 * 模型输入:
 *   - image: float32 [1, 3, H, W]  (NCHW, ImageNet 归一化)
 *   - rois:  float32 [N, 5]         (batch_idx, x1, y1, x2, y2)
 * 输出:
 *   - scores: float32 [N, 1]
 */
class GaicOnnxAnalyzer(
    context: Context,
    private val modelAssetName: String = "gaic.onnx",
) : CompositionAnalyzer {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        // 启用 NNAPI 加速 (Android 8.1+), 失败自动回退 CPU
        options.addNnapi()
        session = env.createSession(loadModelFromAssets(context, modelAssetName), options)
    }

    override suspend fun analyze(bitmap: Bitmap): CompositionResult = withContext(Dispatchers.Default) {
        val pre = ImagePreprocessor.preprocess(bitmap)
        val bboxes = ImagePreprocessor.generateBboxes(pre.resizedHeight, pre.resizedWidth)
        if (bboxes.isEmpty()) {
            return@withContext CompositionResult()
        }

        // 构造 rois tensor: [N, 5] = (batch_idx, x1, y1, x2, y2)
        val roiCount = bboxes.size
        val roiArray = FloatArray(roiCount * 5)
        for ((i, b) in bboxes.withIndex()) {
            roiArray[i * 5] = 0f       // batch_idx
            roiArray[i * 5 + 1] = b[1] // x1
            roiArray[i * 5 + 2] = b[0] // y1
            roiArray[i * 5 + 3] = b[3] // x2
            roiArray[i * 5 + 4] = b[2] // y2
        }

        val imageShape = longArrayOf(1, 3, pre.resizedHeight.toLong(), pre.resizedWidth.toLong())
        val roiShape = longArrayOf(roiCount.toLong(), 5)

        val imageTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(pre.chwFloat), imageShape)
        val roiTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(roiArray), roiShape)

        val inputs = mapOf(
            "image" to imageTensor,
            "rois" to roiTensor,
        )

        val start = System.currentTimeMillis()
        val output = session.run(inputs)
        val elapsed = System.currentTimeMillis() - start

        val scores = output.use { result ->
            @Suppress("UNCHECKED_CAST")
            val arr = result.get(0).value as Array<FloatArray>
            FloatArray(arr.size) { arr[it][0] }
        }

        imageTensor.close()
        roiTensor.close()

        // 取最高分对应的框, 映射回原图坐标
        var bestIdx = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in scores.indices) {
            if (scores[i] > bestScore) {
                bestScore = scores[i]
                bestIdx = i
            }
        }

        val best = bboxes[bestIdx]
        val cropY1 = best[0] * pre.scaleHeight
        val cropX1 = best[1] * pre.scaleWidth
        val cropY2 = best[2] * pre.scaleHeight
        val cropX2 = best[3] * pre.scaleWidth

        CompositionResult(
            bestCrop = RectF(cropX1, cropY1, cropX2, cropY2),
            score = bestScore,
            inferenceMs = elapsed,
        )
    }

    fun close() {
        session.close()
        env.close()
    }

    private fun loadModelFromAssets(context: Context, name: String): ByteArray {
        context.assets.open(name).use { input ->
            return input.readBytes()
        }
    }
}
