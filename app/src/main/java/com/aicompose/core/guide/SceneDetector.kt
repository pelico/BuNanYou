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
            "field", "nature", "wood", "flower", "bush",
            "mountain", "snow", "ice", "peak", "rock", "cliff", "hill",
            "valley", "canyon", "desert", "landscape", "outdoor", "country",
            "sunlight", "rainbow", "fog", "pasture",
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
        /** 从 ML Kit 标签粗打分 (Places365 不可用时的兜底): 词级匹配 + 细分类归属加分 */
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

/**
 * 场景识别结果。
 *
 * 主路径是 Places365 (365 类场景模型): [label] 为 top-1 标签, [labelZh] 是中文名,
 * 粗/细分类都由它推导。Places365 加载失败时回落 ML Kit 标签, 此时只有 coarse/fine。
 */
data class SceneVerdict(
    val coarse: CoarseScene?,
    val fine: SceneTag?,
    val label: String? = null,
    val labelZh: String? = null,
    val usability: SceneUsability = SceneUsability.OK,
)

/**
 * 场景识别 (Places365 端侧 TFLite, 离线) + 多帧投票去抖。
 *
 * 调用方按 ~1.5s 间隔喂数据, 标签做 5 票投票, ≥2 票一致才输出, 避免闪烁。
 * 另外用画面平均亮度判断夜景 (Places365 没有"夜晚"这一类)。
 */
class SceneDetector(context: Context) {

    private val appContext = context.applicationContext

    /** 主路径: Places365; 模型缺失/内存不足时为 null, 回落 ML Kit */
    private val places = PlacesSceneClassifier.create(appContext)

    private val labeler = if (places == null) {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build(),
        )
    } else null

    private val labelVotes = ArrayDeque<String>()
    private val darkVotes = ArrayDeque<Boolean>()
    private val coarseVotes = ArrayDeque<CoarseScene?>()
    private val fineVotes = ArrayDeque<SceneTag?>()
    private var busy = false

    init {
        SceneAffinity.load(appContext)
    }

    fun classifyAsync(bitmap: Bitmap, onResult: (SceneVerdict) -> Unit) {
        if (busy) return
        val model = places
        if (model == null) {
            classifyWithMlKit(bitmap, onResult)
            return
        }
        // Places365 是同步推理 (~几十 ms), 调用方已在后台分析线程上, 直接算完回调
        busy = true
        try {
            val top = model.classify(bitmap, 3)
            pushPlaces(top, isDark(bitmap))
        } catch (_: Throwable) {
            // 单次推理失败按"没识别"计入投票, 不打断流程
        }
        busy = false
        onResult(stable())
    }

    private fun classifyWithMlKit(bitmap: Bitmap, onResult: (SceneVerdict) -> Unit) {
        val labeler = labeler ?: run {
            onResult(stable())
            return
        }
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

    private fun pushPlaces(top: List<ScenePrediction>, dark: Boolean) {
        val label = top.firstOrNull()?.label
        if (label != null) {
            labelVotes.addLast(label.lowercase())
            if (labelVotes.size >= VOTE_WINDOW) labelVotes.removeFirst()
        }
        darkVotes.addLast(dark)
        if (darkVotes.size >= VOTE_WINDOW) darkVotes.removeFirst()
    }

    private fun push(fine: SceneTag?, pairs: List<Pair<String, Float>>) {
        val coarse = CoarseScene.from(pairs, fine)
        if (coarseVotes.size >= VOTE_WINDOW) coarseVotes.removeFirst()
        if (fineVotes.size >= VOTE_WINDOW) fineVotes.removeFirst()
        coarseVotes.addLast(coarse)
        fineVotes.addLast(fine)
    }

    private fun stable(): SceneVerdict {
        val label = voteMajority(labelVotes)
        if (label != null) {
            var group = SceneAffinity.groupOf(label)
            // 室外 + 画面偏暗 → 按夜景处理 (夜景推荐比"这是什么景"更重要)
            val dark = voteMajority(darkVotes) == true
            if (dark && group != SceneGroup.CAFE && group != SceneGroup.GENERAL) {
                group = SceneGroup.NIGHT
            }
            return SceneVerdict(
                coarse = group.coarse,
                fine = group.tag,
                label = label,
                labelZh = SceneLabels.zh(label),
                usability = SceneAffinity.usabilityOf(label),
            )
        }
        return SceneVerdict(
            coarse = voteMajority(coarseVotes),
            fine = voteMajority(fineVotes),
        )
    }

    private fun <T> voteMajority(votes: ArrayDeque<T>): T? {
        val counts = votes.groupingBy { it }.eachCount()
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.takeIf { it.value >= 2 }?.key
    }

    /** 画面平均亮度 0..1 (缩到 8×8 采样, 开销可忽略) */
    private fun isDark(bitmap: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val px = IntArray(64)
        small.getPixels(px, 0, 8, 0, 0, 8, 8)
        if (small !== bitmap) small.recycle()
        var sum = 0.0
        for (p in px) {
            val r = (p shr 16 and 0xFF)
            val g = (p shr 8 and 0xFF)
            val b = (p and 0xFF)
            sum += 0.299 * r + 0.587 * g + 0.114 * b
        }
        return sum / px.size / 255.0 < DARK_THRESHOLD
    }

    fun close() {
        places?.close()
        labeler?.close()
    }

    private companion object {
        const val VOTE_WINDOW = 5
        const val DARK_THRESHOLD = 0.22
    }
}
