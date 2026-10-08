package com.aicompose.core.vision.scene

import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit 图像标签场景识别 —— 完全端侧运行, 不用联网也不用 API Key。
 *
 * 把识别到的英文标签映射到 [Scene] 上: 每个场景维护一份命中词表,
 * 取「置信度最高的命中词」所在场景作为结果; 都没命中就返回 null。
 */
class SceneDetector {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    /**
     * 实时取景用: 直接吃 [ImageProxy] 的 MediaImage, 省掉一次 Bitmap 转换。
     * [onComplete] 在 ML Kit 处理结束后回调, 此时才可以安全关闭 MediaImage。
     */
    fun classifyAsync(
        mediaImage: Image,
        rotationDegrees: Int,
        onResult: (Scene?) -> Unit,
        onComplete: () -> Unit,
    ) {
        val input = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        labeler.process(input)
            .addOnSuccessListener { labels -> onResult(labels.toScene()) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { onComplete() }
    }

    /** 单张图片用 */
    suspend fun classify(bitmap: Bitmap): Scene? = suspendCancellableCoroutine { cont ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels -> cont.resume(labels.toScene()) }
            .addOnFailureListener { cont.resume(null) }
    }

    fun close() {
        labeler.close()
    }

    private fun List<com.google.mlkit.vision.label.ImageLabel>.toScene(): Scene? {
        var best: Scene? = null
        var bestScore = 0f
        for (label in this) {
            val text = label.text.lowercase()
            for (scene in Scene.entries) {
                if (text in scene.labels && label.confidence > bestScore) {
                    bestScore = label.confidence
                    best = scene
                }
            }
        }
        return best
    }
}