package com.aicompose.core.guide

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * 粗粒度场景标签 —— 仅用于姿势推荐的排序/自动推荐 (软信号),
 * 不做硬路由, 识别不准也不影响拍照主流程。
 */
enum class SceneTag(val displayName: String, val emoji: String, val keywords: Set<String>) {
    BEACH("海边", "🌊", setOf("sea", "ocean", "beach", "coast", "shore", "water", "sand", "wave", "seaside", "lake", "horizon")),
    STREET("街景", "🏙️", setOf("building", "architecture", "street", "road", "city", "urban", "sidewalk", "downtown", "facade", "wall", "bridge")),
    CAFE("室内", "☕", setOf("coffee", "café", "cafe", "cup", "mug", "restaurant", "indoor", "table", "chair", "drink", "espresso", "interior")),
    PARK("绿地", "🌳", setOf("tree", "grass", "plant", "garden", "forest", "leaf", "field", "park", "meadow", "nature", "wood")),
    SNOW("雪山", "🏔️", setOf("snow", "mountain", "ice", "glacier", "winter", "peak", "alps", "frost")),
    NIGHT("夜景", "🌃", setOf("night", "dark", "evening", "dusk", "neon", "lamp", "midnight", "sunset", "twilight")),
    ;

    companion object {
        /** ML Kit 输出多为多词标签 (如 "Coffee shop"), 按词拆分模糊匹配, 置信度累加 */
        fun bestMatch(labels: List<Pair<String, Float>>): SceneTag? {
            val scores = HashMap<SceneTag, Float>()
            for ((text, conf) in labels) {
                val words = text.lowercase().split(Regex("[\\s,/]+"))
                for (scene in entries) {
                    if (words.any { it in scene.keywords }) {
                        scores[scene] = (scores[scene] ?: 0f) + conf
                    }
                }
            }
            val best = scores.maxByOrNull { it.value } ?: return null
            return best.takeIf { it.value >= 0.4f }?.key
        }
    }
}

/**
 * 场景识别 (ML Kit 端侧, 离线) + 多帧投票去抖。
 *
 * 调用方按 ~1.5s 间隔喂数据, [classifyAsync] 回调最近投票的多数场景
 * (5 票中出现 ≥2 次才算稳定), 避免识别结果闪烁。
 */
class SceneDetector(context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
    )

    private val votes = ArrayDeque<SceneTag?>()
    private var busy = false

    fun classifyAsync(bitmap: Bitmap, onResult: (SceneTag?) -> Unit) {
        if (busy) return
        busy = true
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                push(SceneTag.bestMatch(labels.map { it.text to it.confidence }))
            }
            .addOnFailureListener { push(null) }
            .addOnCompleteListener {
                busy = false
                onResult(stable())
            }
    }

    private fun push(vote: SceneTag?) {
        if (votes.size >= VOTE_WINDOW) votes.removeFirst()
        votes.addLast(vote)
    }

    private fun stable(): SceneTag? {
        val counts = votes.filterNotNull().groupingBy { it }.eachCount()
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.takeIf { it.value >= 2 }?.key
    }

    fun close() {
        labeler.close()
    }

    private companion object {
        const val VOTE_WINDOW = 5
    }
}
