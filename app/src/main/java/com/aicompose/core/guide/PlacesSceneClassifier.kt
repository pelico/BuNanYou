package com.aicompose.core.guide

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** Places365 top-k 预测 */
data class ScenePrediction(val label: String, val prob: Float)

/**
 * Places365-ResNet18 场景分类 (LiteRT fp16, 365 类, 21.7 MB)。
 *
 * 预处理必须与模型卡一致: 中心裁剪正方形 → 224×224 → /255 → ImageNet 归一化 → NCHW。
 * 场景变化很慢, 调用方按 ~1.5s 喂一帧即可, 延迟不是瓶颈。
 */
class PlacesSceneClassifier private constructor(
    private val interpreter: Interpreter,
    val labels: List<String>,
) {

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * 3 * SIZE * SIZE).order(ByteOrder.nativeOrder())
    private val output: Array<FloatArray>

    init {
        val shape = interpreter.getOutputTensor(0).shape()
        output = Array(1) { FloatArray(shape[1]) }
    }

    /** @return 概率从高到低的 top-k 场景预测 */
    fun classify(bitmap: Bitmap, topK: Int = 5): List<ScenePrediction> {
        val square = centerCrop(bitmap)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled !== square) scaled.recycle()
        if (square !== bitmap) square.recycle()

        inputBuffer.rewind()
        for (c in 0..2) {
            val shift = 16 - 8 * c
            val mean = MEAN[c]
            val std = STD[c]
            for (p in pixels) {
                val v = ((p shr shift) and 0xFF) / 255f
                inputBuffer.putFloat((v - mean) / std)
            }
        }

        interpreter.run(inputBuffer, output)

        val logits = output[0]
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0f
        val probs = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - max).toDouble()).toFloat()
            probs[i] = e
            sum += e
        }
        for (i in probs.indices) probs[i] /= sum

        val n = labels.size.coerceAtMost(probs.size)
        return probs.indices
            .filter { it < n }
            .sortedByDescending { probs[it] }
            .take(topK)
            .map { ScenePrediction(labels[it], probs[it]) }
    }

    /** 中心裁剪成正方形, 避免直接拉伸破坏比例影响分类准确率 */
    private fun centerCrop(source: Bitmap): Bitmap {
        val s = minOf(source.width, source.height)
        if (s == source.width && s == source.height) return source
        val left = (source.width - s) / 2
        val top = (source.height - s) / 2
        return Bitmap.createBitmap(source, left, top, s, s)
    }

    fun close() {
        runCatching { interpreter.close() }
    }

    /** 模型缺失/内存不足时返回 null, 调用方回落到 ML Kit 标签 */
    companion object {
        const val MODEL_FILE = "places_fp16.tflite"
        const val LABEL_FILE = "categories_places365.txt"
        const val SIZE = 224
        private const val INFER_THREADS = 4

        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun create(context: Context): PlacesSceneClassifier? = try {
            val modelBytes = context.assets.open(MODEL_FILE).readBytes()
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
            modelBuffer.put(modelBytes)
            modelBuffer.rewind()

            val interpreter = Interpreter(
                modelBuffer,
                Interpreter.Options().apply { setNumThreads(INFER_THREADS) },
            )
            val labels = SceneLabels.parse(
                context.assets.open(LABEL_FILE).bufferedReader().use { it.readText() },
            )
            PlacesSceneClassifier(interpreter, labels)
        } catch (_: Throwable) {
            null
        }
    }
}
