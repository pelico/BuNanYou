package com.aicompose.core.vision.scene

/**
 * 拍摄场景。
 *
 * [labels] 是 ML Kit 图像标签 (英文, 小写) 中能命中该场景的词表 —— 端侧识别到
 * 这些标签就把画面判为该场景, 再推荐对应姿势模板。
 */
enum class Scene(
    val key: String,
    val displayName: String,
    val emoji: String,
    val hint: String,
    val labels: Set<String>,
) {
    BEACH(
        key = "beach", displayName = "海边", emoji = "🌊",
        hint = "海天一线是大背景，动作可以放开一点",
        labels = setOf("beach", "sea", "ocean", "coast", "shore", "sand", "water", "lake", "sky"),
    ),
    STREET(
        key = "street", displayName = "街道", emoji = "🏙️",
        hint = "用街道纵深和店面当背景，人往前走别停下",
        labels = setOf("street", "road", "building", "city", "alley", "sidewalk", "urban", "architecture", "walkway"),
    ),
    CAFE(
        key = "cafe", displayName = "咖啡厅", emoji = "☕",
        hint = "坐下来更松弛，借窗边的自然光",
        labels = setOf("coffee", "cafe", "restaurant", "food", "drink", "cup", "table", "interior", "chair"),
    ),
    PARK(
        key = "park", displayName = "公园绿地", emoji = "🌳",
        hint = "绿植当背景，走动抓拍最自然",
        labels = setOf("park", "grass", "garden", "tree", "forest", "plant", "lawn", "nature", "flower", "path"),
    ),
    SNOW(
        key = "snow", displayName = "雪山高原", emoji = "🏔️",
        hint = "大场面适合张开手臂或留个背影",
        labels = setOf("mountain", "snow", "hill", "glacier", "valley", "peak", "landscape", "ice"),
    ),
    NIGHT(
        key = "night", displayName = "城市夜景", emoji = "🌃",
        hint = "霓虹灯当背景，走起来更有电影感",
        labels = setOf("night", "skyline", "cityscape", "neon", "darkness", "street light", "light"),
    );

    companion object {
        fun byKey(key: String?): Scene? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 一个姿势模板。
 *
 * [id] 同时也是素材文件名 —— assets/poses/<id>.png 是人物剪影 (取景框叠加用),
 * assets/poses/<id>.jpg 是 AI 参考缩略图 (卡片展示用)。
 */
data class PoseCard(
    val id: String,
    val scene: Scene,
    val name: String,
    val summary: String,
)

/** 姿势模板库: 场景 → 推荐姿势。 */
object PoseCatalog {

    val ALL: List<PoseCard> = listOf(
        // 海边
        PoseCard("beach_side", Scene.BEACH, "侧身 45°", "身体侧转，显瘦又有立体感，看海或看镜头都行"),
        PoseCard("beach_sit", Scene.BEACH, "坐沙滩", "弯腰坐姿、腿自然伸展，显得随意又放松"),
        PoseCard("beach_walk", Scene.BEACH, "迎风走动", "迎着海风走，裙摆和头发飘起来最有氛围"),
        // 街道
        PoseCard("street_wall", Scene.STREET, "靠墙站", "背靠砖墙、手插口袋，简单又有型"),
        PoseCard("street_walk", Scene.STREET, "走向镜头", "朝镜头走过来，脚步别停，抓拍最自然"),
        PoseCard("street_step", Scene.STREET, "坐台阶", "台阶上单手托腮，城市街拍感十足"),
        // 咖啡厅
        PoseCard("cafe_sit", Scene.CAFE, "坐吧台凳", "侧坐高脚凳、双手捧杯，暖暖的咖啡厅氛围"),
        PoseCard("cafe_lean", Scene.CAFE, "靠窗持杯", "靠在窗边拿杯咖啡，一条腿交叉，气质感"),
        // 公园
        PoseCard("park_walk", Scene.PARK, "小径回眸", "林荫小道上边走边拨头发，逆光很温柔"),
        PoseCard("park_sit", Scene.PARK, "草地坐姿", "草地上侧坐收腿、双手放膝，显得乖巧"),
        // 雪山
        PoseCard("snow_stand", Scene.SNOW, "张臂看山", "背对镜头张开手臂，大场面更有气势"),
        PoseCard("snow_look", Scene.SNOW, "眺望雪山", "侧身望向远山，安静又有故事感"),
        // 夜景
        PoseCard("night_stand", Scene.NIGHT, "街口站立", "站在街口双手插兜，霓虹灯光当背景"),
        PoseCard("night_walk", Scene.NIGHT, "回眸街拍", "走两步回头看镜头，夜景抓拍很有电影感"),
    )

    fun of(scene: Scene): List<PoseCard> = ALL.filter { it.scene == scene }

    fun byId(id: String?): PoseCard? = ALL.firstOrNull { it.id == id }
}