package com.aicompose.core.guide

import android.content.Context
import org.json.JSONArray

/** 景别分类 —— 由人物在画面中的高度占比决定 */
enum class ShotType(val displayName: String) {
    FULL_BODY("全身"),
    HALF_BODY("半身"),
    CLOSE_UP("特写");

    companion object {
        fun from(raw: String?): ShotType = when (raw?.uppercase()) {
            "FULL_BODY" -> FULL_BODY
            "CLOSE_UP" -> CLOSE_UP
            else -> HALF_BODY
        }
    }
}

/** 模板骨架关节 (归一化坐标 0~1, y 向下) */
data class Joint(val id: Int, val x: Float, val y: Float)

/**
 * 姿势模板 (对应 assets/pose_templates.json 中的一项)。
 *
 * [joints] 按 MediaPipe landmark id 索引: 0 鼻子, 11/12 肩, 13/14 肘,
 * 15/16 腕, 23/24 髋, 25/26 膝, 27/28 踝。
 */
data class PoseTemplate(
    val id: String,
    val name: String,
    val shotType: ShotType,
    val tags: List<String>,
    val guideText: String,
    /** 推荐俯仰角, 度 (负值 = 略仰拍) */
    val cameraPitchRecommended: Float,
    /** 目标人物高度占比 (0~1) */
    val targetPersonRatio: Float,
    val joints: Map<Int, Joint>,
)

/** 模板库: 从 assets/pose_templates.json 加载, 进程内缓存。 */
object PoseTemplateCatalog {

    @Volatile
    private var cached: List<PoseTemplate>? = null

    fun load(context: Context): List<PoseTemplate> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            return parse(json).also { cached = it }
        }
    }

    private const val ASSET = "pose_templates.json"

    /** 用 org.json 手工解析, 避免为 36KB 配置引入序列化插件依赖 */
    private fun parse(json: String): List<PoseTemplate> {
        val arr = JSONArray(json)
        val out = ArrayList<PoseTemplate>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val joints = HashMap<Int, Joint>()
            val lm = o.getJSONArray("target_landmarks")
            for (j in 0 until lm.length()) {
                val p = lm.getJSONObject(j)
                val id = p.getInt("id")
                joints[id] = Joint(id, p.getDouble("x").toFloat(), p.getDouble("y").toFloat())
            }
            val tags = ArrayList<String>()
            val tg = o.getJSONArray("tags")
            for (t in 0 until tg.length()) tags.add(tg.getString(t))
            out.add(
                PoseTemplate(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    shotType = ShotType.from(o.optString("shot_type")),
                    tags = tags,
                    guideText = o.optString("guide_text"),
                    cameraPitchRecommended = o.getDouble("camera_pitch_recommended").toFloat(),
                    targetPersonRatio = o.getDouble("target_person_ratio").toFloat(),
                    joints = joints,
                )
            )
        }
        return out
    }
}
