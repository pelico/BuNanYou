package com.aicompose.core.guide

import android.content.Context
import org.json.JSONObject

/** 场景适宜度: 这个场景值不值得拍人像 */
enum class SceneUsability(val zh: String, val advice: String) {
    GREAT("适合拍", ""),
    OK("可以拍", ""),
    WEAK("能拍，但要挑角度", "背景杂乱，换背景干净的角度或用大光圈压掉"),
    POOR("不建议在这里拍人像", "换个地方；这类场景出片率低，AI 给姿势也没用"),
}

/** 本项目的 6 个姿势分组 (与 pose_templates.json 的标签前缀一一对应) */
enum class SceneGroup(val key: String) {
    BEACH("beach"),
    SNOW("snow"),
    PARK("park"),
    STREET("street"),
    NIGHT("night"),
    CAFE("cafe"),
    GENERAL("general"),
    ;

    /** 该分组驱动的粗大类与细分类标签 */
    val coarse: CoarseScene?
        get() = when (this) {
            BEACH, SNOW, PARK -> CoarseScene.OPEN
            STREET, NIGHT -> CoarseScene.STREET
            CAFE -> CoarseScene.INDOOR
            GENERAL -> null
        }

    val tag: SceneTag?
        get() = when (this) {
            BEACH -> SceneTag.BEACH
            SNOW -> SceneTag.SNOW
            PARK -> SceneTag.PARK
            STREET -> SceneTag.STREET
            NIGHT -> SceneTag.NIGHT
            CAFE -> SceneTag.CAFE
            GENERAL -> null
        }

    companion object {
        fun of(key: String): SceneGroup =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

/**
 * 场景亲和表 (assets/scene_affinity.json):
 * - `map`: Places365 标签 → 该场景推荐的模板 id (按优先级排好)
 * - `groups`: 同源的 标签 → 分组, 保证「推荐池」和「亲和表」永远一致
 * - `poor` / `weak`: 不适合/勉强适合拍人像的标签
 *
 * 为什么需要显式表: 靠 tag 命中打分时, 同组模板分数完全相同, 首条推荐实际由
 * 库里的排列顺序决定 —— 推荐不是「匹配」出来的。这张表把结论写死。
 * 表缺失时静默降级到旧的标签匹配, 不会崩。
 */
object SceneAffinity {

    private const val FILE = "scene_affinity.json"

    @Volatile
    private var loaded = false
    private var map: Map<String, List<String>> = emptyMap()
    private var groups: Map<String, SceneGroup> = emptyMap()
    private var fallback: List<String> = emptyList()
    private var poor: Set<String> = emptySet()
    private var weak: Set<String> = emptySet()

    var loadError: String? = null
        private set

    val isReady: Boolean get() = loaded && map.isNotEmpty()

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val text = context.assets.open(FILE).bufferedReader().use { it.readText() }
            val root = JSONObject(text)

            fallback = root.optJSONArray("fallback")?.toStringList() ?: emptyList()
            poor = root.optJSONArray("poor")?.toStringList()?.map { it.lowercase() }?.toSet()
                ?: emptySet()
            weak = root.optJSONArray("weak")?.toStringList()?.map { it.lowercase() }?.toSet()
                ?: emptySet()

            root.optJSONObject("groups")?.let { obj ->
                val m = HashMap<String, SceneGroup>()
                val it = obj.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    m[key.lowercase()] = SceneGroup.of(obj.optString(key))
                }
                groups = m
            }

            val m = LinkedHashMap<String, List<String>>()
            root.optJSONObject("map")?.let { obj ->
                val it = obj.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    val arr = obj.optJSONArray(key) ?: continue
                    val ids = (0 until arr.length()).map { arr.getString(it) }
                    if (ids.isNotEmpty()) m[key.lowercase()] = ids
                }
            }
            map = m
        } catch (e: Exception) {
            loadError = "亲和表加载失败：${e.message}"
        }
    }

    /** 该标签所属分组; 表里查不到时返回 [SceneGroup.GENERAL] */
    fun groupOf(label: String): SceneGroup = groups[label.lowercase()] ?: SceneGroup.GENERAL

    /** 该场景推荐的模板 id (按优先级排好), 查不到返回 null */
    fun templatesFor(label: String): List<String>? = map[label.lowercase()]

    fun fallbackIds(): List<String> = fallback

    fun usabilityOf(label: String): SceneUsability {
        val l = label.lowercase()
        if (poor.any { l.contains(it) }) return SceneUsability.POOR
        if (weak.any { l.contains(it) }) return SceneUsability.WEAK
        return SceneUsability.OK
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
