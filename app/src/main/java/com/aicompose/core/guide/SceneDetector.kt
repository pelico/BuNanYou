package com.aicompose.core.guide

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * 粗场景 3 大类 —— 驱动底部推荐的硬过滤 (与景别/细分类独立)。
 * [poseTags] 是该大类下可推荐的姿势标签集合。
 */
enum class CoarseScene(
    val displayName: String,
    val emoji: String,
    val poseTags: Set<String>,
    val keywords: Set<String>,
) {
    OPEN(
        "开阔自然", "🏔️",
        poseTags = setOf("海边", "绿地", "雪山"),
        keywords = setOf(
            "sky", "cloud", "sun", "sunrise", "sunset", "moon", "star",
            "sea", "ocean", "beach", "coast", "shore", "water", "sand",
            "wave", "lake", "horizon", "river", "waterfall", "island",
            "tree", "grass", "plant", "garden", "forest", "leaf", "meadow",
            "field", "nature", "wood", "flower", "bush", "leaf",
            "mountain", "snow", "ice", "peak", "rock", "cliff", "hill",
            "valley", "canyon", "desert", "landscape", "outdoor", "country",
            "sunlight", "rainbow", "fog", "meadow", "pasture",
        ),
    ),
    STREET(
        "街景建筑", "🏙️",
        poseTags = setOf("街景", "夜景"),
        keywords = setOf(
            "building", "architecture", "street", "road", "city", "urban",
            "sidewalk", "downtown", "facade", "wall", "bridge", "house",
            "window", "tower", "traffic", "vehicle", "car", "bus", "truck",
            "pedestrian", "alley", "sign", "skyscraper", "town", "metropolis",
            "monument", "statue", "stadium", "shop", "market",
        ),
    ),
    INDOOR(
        "室内", "☕",
        poseTags = setOf("室内"),
        keywords = setOf(
            "coffee", "café", "cafe", "cup", "mug", "restaurant", "indoor",
            "room", "table", "chair", "interior", "desk", "office", "book",
            "shelf", "kitchen", "sofa", "furniture", "food", "meal", "dinner",
            "espresso", "bar", "lobby", "studio", "classroom",
        ),
    ),
    ;

    companion object {
        /** 从 ML Kit 标签粗打分: 词级匹配 + 细分类归属加分, 取最高且过阈值者 */
        fun from(labels: List<Pair<String, Float>>, fine: SceneTag?): CoarseScene? {
            val scores = HashMap<CoarseScene, Float>()
            for ((text, conf) in labels) {
                val words = text.lowercase().split(Regex("[\\s,/]+"))
                for (scene in entries) {
                    if (words.any { it in scene.keywords }) {
                        scores[scene] = (scores[scene] ?: 0f) + conf
                    }
                }
            }
            // 细分类已归属的大类额外加分, 让细分类结果能带出粗分类
            val fineOwner = when (fine) {
                SceneTag.BEACH, SceneTag.PARK, SceneTag.SNOW -> OPEN
                SceneTag.STREET, SceneTag.NIGHT -> STREET
                SceneTag.CAFE -> INDOOR
                null -> null
            }
            if (fineOwner != null) {
                scores[fineOwner] = (scores[fineOwner] ?: 0f) + 0.6f
            }
            val best = scores.maxByOrNull { it.value } ?: return null
            return best.takeIf { it.value >= 0.4f }?.key
        }
    }
}

/** 细分类标签 —— 仅用于同大类内的姿势微调, 不再单独驱动推荐 */
enum class SceneTag(val displayName: String, val emoji: String, val keywords: Set<String>) {
    BEACH("海边", "🌊", setOf("sea", "ocean", "beach", "coast", "shore", "water", "sand", "wave", "seaside", "lake", "horizon")),
    STREET("街景", "🏙️", setOf("building", "architecture", "street", "road", "city", "urban", "sidewalk", "downtown", "facade", "bridge")),
    CAFE("室内", "☕", setOf("coffee", "café", "cafe", "cup", "mug", "restaurant", "indoor", "table", "chair", "drink", "espresso", "interior")),
    PARK("绿地", "🌳", setOf("tree", "grass", "plant", "garden", "forest", "leaf", "field", "park", "meadow", "nature", "wood")),
    SNOW("雪山", "🏔️", setOf("snow", "mountain", "ice", "glacier", "winter", "peak", "alps", "frost")),
    NIGHT("夜景", "🌃", setOf("night", "dark", "evening", "dusk", "neon", "midnight", "twilight")),
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

/** 场景识别结果: 粗大类 (硬过滤) + 细分类 (微调, 可空) */
data class SceneVerdict(val coarse: CoarseScene?, val fine: SceneTag?)

/**
 * 场景识别 (ML Kit 端侧, 离线) + 多帧投票去抖。
 *
 * 调用方按 ~1.5s 间隔喂数据, 粗/细各自做 5 票投票, ≥2 票一致才输出,
 * 避免识别结果闪烁。粗分类只影响推荐池, 识别不准也不阻断拍照。
 */
class SceneDetector(context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
    )

    private val coarseVotes = ArrayDeque<CoarseScene?>()
    private val fineVotes = ArrayDeque<SceneTag?>()
    private var busy = false

    fun classifyAsync(bitmap: Bitmap, onResult: (SceneVerdict) -> Unit) {
        if (busy) return
        busy = true
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                val pairs = labels.map { it.text to it.confidence }
                push(SceneTag.bestMatch(pairs), pairs)
            }
            .addOnFailureListener { push(null, emptyList()) }
            .addOnCompleteListener {
                busy = false
                onResult(stable())
            }
    }

    private fun push(fine: SceneTag?, pairs: List<Pair<String, Float>>) {
        val coarse = CoarseScene.from(pairs, fine)
        if (coarseVotes.size >= VOTE_WINDOW) coarseVotes.removeFirst()
        if (fineVotes.size >= VOTE_WINDOW) fineVotes.removeFirst()
        coarseVotes.addLast(coarse)
        fineVotes.addLast(fine)
    }

    private fun stable(): SceneVerdict = SceneVerdict(
        coarse = voteMajority(coarseVotes),
        fine = voteMajority(fineVotes),
    )

    private fun <T> voteMajority(votes: ArrayDeque<T?>): T? {
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
