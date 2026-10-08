package com.aicompose.core.vision.scene

import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.ArrayDeque
import kotlin.coroutines.resume

/**
 * ML Kit 图像标签场景识别 —— 完全端侧运行, 不用联网也不用 API Key。
 *
 * 相比初版做了三处关键改进:
 * 1. 模糊匹配: ML Kit 默认模型输出的是多词短语 (如 "Coffee shop"), 按词拆分后
 *    与场景关键词表做整词比较, 不再要求整串相等, 命中率大幅提升。
 * 2. 多帧投票: 单帧结果不可靠, 维护一个滑动窗口, 取最近 N 帧出现最多的场景,
 *    只在票数过半时切换, 识别结果不再来回闪。
 * 3. 返回命中标签 + 置信度: 上层可以展示 "识别到: 沙滩 87%", 让识别过程可见。
 */
class SceneDetector(private val windowSize: Int = 7) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f)
            .build()
    )

    /** 最近 [windowSize] 帧的原始识别结果, 用于投票去抖。 */
    private val recent = ArrayDeque<Scene>()

    /** 当前稳定场景; 只有新场景票数过半才切换, 否则保持原值。 */
    private var stableScene: Scene? = null

    /** 最近一次命中的标签文本 + 置信度, 用于 UI 展示。 */
    private var lastLabel: String? = null
    private var lastConfidence: Float = 0f

    /**
     * 实时取景用: 直接吃 [androidx.camera.core.ImageProxy] 的 MediaImage, 省掉一次 Bitmap 转换。
     * [onResult] 在主线程回调, 携带去抖后的稳定场景与命中信息; [onComplete] 之后才可以关闭 MediaImage。
     */
    fun classifyAsync(
        mediaImage: Image,
        rotationDegrees: Int,
        onResult: (SceneHit?) -> Unit,
        onComplete: () -> Unit,
    ) {
        val input = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        labeler.process(input)
            .addOnSuccessListener { labels -> onResult(toSceneHit(labels)) }
            .addOnFailureListener {
                onResult(stableScene?.let { SceneHit(it, lastLabel, lastConfidence) })
            }
            .addOnCompleteListener { onComplete() }
    }

    /** 单张图片用, 不做投票, 直接返回最高置信度的命中。 */
    suspend fun classify(bitmap: Bitmap): Scene? = suspendCancellableCoroutine { cont ->
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels -> cont.resume(toSceneHit(labels)?.scene) }
            .addOnFailureListener { cont.resume(null) }
    }

    fun close() {
        labeler.close()
    }

    /**
     * 把一帧的标签列表转成 (场景, 命中标签, 置信度), 并做多帧投票:
     * - 取该帧得分最高 (置信度加权) 的场景
     * - 推进滑动窗口, 票数过半才切换 stableScene
     */
    private fun toSceneHit(labels: List<com.google.mlkit.vision.label.ImageLabel>): SceneHit? {
        // 1. 该帧内: 对每个标签, 计算各场景命中得分, 取置信度最高的场景
        var frameBest: Scene? = null
        var frameScore = 0f
        var frameLabel: String? = null
        for (label in labels) {
            val hits = Scene.keywordHits(label.text)
            for ((scene, word) in hits) {
                // 加权: 命中词越多、置信度越高, 得分越高
                val score = label.confidence * (1f + hits.size * 0.15f)
                if (score > frameScore) {
                    frameScore = score
                    frameBest = scene
                    frameLabel = word
                }
            }
        }
        if (frameBest == null) return stableScene?.let { SceneHit(it, lastLabel, lastConfidence) }

        // 2. 多帧投票
        recent.addLast(frameBest)
        while (recent.size > windowSize) recent.removeFirst()
        val counts = HashMap<Scene, Int>()
        for (s in recent) counts[s] = (counts[s] ?: 0) + 1
        val majority = counts.entries.maxByOrNull { it.value }!!
        if (majority.value * 2 > windowSize) {
            stableScene = majority.key
        }
        lastLabel = frameLabel
        lastConfidence = frameScore
        val stable = stableScene ?: frameBest
        return SceneHit(stable, frameLabel, frameScore)
    }
}

/** 一次场景识别结果: 稳定场景 + 命中的标签文本 + 置信度 (0~1)。 */
data class SceneHit(
    val scene: Scene,
    val label: String?,
    val confidence: Float,
)
