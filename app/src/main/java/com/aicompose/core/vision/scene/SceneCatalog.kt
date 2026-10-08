package com.aicompose.core.vision.scene

/**
 * 拍摄场景。
 *
 * [labels] 是 ML Kit 图像标签 (英文, 小写) 中能命中该场景的关键词表。
 * ML Kit 默认模型输出的标签可能是多词短语 (如 "Coffee shop"), 所以匹配时
 * 会把标签文本按词拆分, 只要有一个分词命中关键词表就算命中, 见 [SceneDetector]。
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
        labels = setOf(
            "beach", "sea", "ocean", "coast", "shore", "sand", "water", "lake",
            "sky", "wave", "seaside", "sunset", "surf", "swimming", "summer", "seashore",
        ),
    ),
    STREET(
        key = "street", displayName = "街道", emoji = "🏙️",
        hint = "用街道纵深和店面当背景，人往前走别停下",
        labels = setOf(
            "street", "road", "building", "city", "alley", "sidewalk", "urban",
            "architecture", "walkway", "downtown", "traffic", "avenue", "cars",
            "store", "shop", "brick", "streetlight",
        ),
    ),
    CAFE(
        key = "cafe", displayName = "咖啡厅", emoji = "☕",
        hint = "坐下来更松弛，借窗边的自然光",
        labels = setOf(
            "coffee", "cafe", "restaurant", "food", "drink", "cup", "table",
            "interior", "chair", "café", "bar", "dining", "snack", "coffeehouse",
            "waiter", "dish",
        ),
    ),
    PARK(
        key = "park", displayName = "公园绿地", emoji = "🌳",
        hint = "绿植当背景，走动抓拍最自然",
        labels = setOf(
            "park", "grass", "garden", "tree", "forest", "plant", "lawn",
            "nature", "flower", "path", "leaf", "botanical", "woodland",
            "hiking", "bush",
        ),
    ),
    SNOW(
        key = "snow", displayName = "雪山高原", emoji = "🏔️",
        hint = "大场面适合张开手臂或留个背影",
        labels = setOf(
            "mountain", "snow", "hill", "glacier", "valley", "peak", "landscape",
            "ice", "alpine", "winter", "ski", "frozen", "summit", "cliff",
        ),
    ),
    NIGHT(
        key = "night", displayName = "城市夜景", emoji = "🌃",
        hint = "霓虹灯当背景，走起来更有电影感",
        labels = setOf(
            "night", "skyline", "cityscape", "neon", "darkness", "light",
            "lamp", "moon", "stars", "evening", "dusk", "illumination",
            "glow", "streetlight",
        ),
    );

    companion object {
        fun byKey(key: String?): Scene? = entries.firstOrNull { it.key == key }

        /** 关键词是否命中标签文本: 按词拆分后做整词比较, 兼容 "Coffee shop" 这类多词标签。 */
        fun keywordHits(labelText: String): List<Pair<Scene, String>> {
            val words = labelText.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            val hits = mutableListOf<Pair<Scene, String>>()
            for (scene in entries) {
                for (w in words) {
                    if (w in scene.labels) {
                        hits += scene to w
                    }
                }
            }
            return hits
        }
    }
}

/**
 * 一个姿势模板。
 *
 * [id] 同时也是素材文件名 —— assets/poses/<id>.png 是人物剪影 (取景框叠加用),
 * assets/poses/<id>.jpg 是参考缩略图 (卡片展示用)。
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
        // 海边 (5)
        PoseCard("beach_side", Scene.BEACH, "侧身 45°", "身体侧转，显瘦又有立体感，看海或看镜头都行"),
        PoseCard("beach_sit", Scene.BEACH, "坐沙滩", "弯腰坐姿、腿自然伸展，显得随意又放松"),
        PoseCard("beach_walk", Scene.BEACH, "迎风走动", "迎着海风走，裙摆和头发飘起来最有氛围"),
        PoseCard("beach_jump", Scene.BEACH, "跳跃抓拍", "趁浪花打来跳起来，连拍最出片"),
        PoseCard("beach_armup", Scene.BEACH, "张开双臂", "背对海张开双臂，画面开阔有呼吸感"),
        // 街道 (5)
        PoseCard("street_wall", Scene.STREET, "靠墙站", "背靠砖墙、手插口袋，简单又有型"),
        PoseCard("street_walk", Scene.STREET, "走向镜头", "朝镜头走过来，脚步别停，抓拍最自然"),
        PoseCard("street_step", Scene.STREET, "坐台阶", "台阶上单手托腮，城市街拍感十足"),
        PoseCard("street_lean", Scene.STREET, "倚柱回望", "靠在店门边回看镜头，慵懒随性"),
        PoseCard("street_cross", Scene.STREET, "交叉腿站", "重心放一侧腿，另一腿微交叉，显腿长"),
        // 咖啡厅 (5)
        PoseCard("cafe_sit", Scene.CAFE, "坐吧台凳", "侧坐高脚凳、双手捧杯，暖暖的咖啡厅氛围"),
        PoseCard("cafe_lean", Scene.CAFE, "靠窗持杯", "靠在窗边拿杯咖啡，一条腿交叉，气质感"),
        PoseCard("cafe_book", Scene.CAFE, "低头看书", "坐窗边低头看书，安静又有故事感"),
        PoseCard("cafe_stand", Scene.CAFE, "插兜侧站", "吧台边单手插兜，随意等咖啡的瞬间"),
        PoseCard("cafe_wave", Scene.CAFE, "举杯示意", "举起咖啡杯向镜头示意，轻松自然"),
        // 公园 (5)
        PoseCard("park_walk", Scene.PARK, "小径回眸", "林荫小道上边走边拨头发，逆光很温柔"),
        PoseCard("park_sit", Scene.PARK, "草地坐姿", "草地上侧坐收腿、双手放膝，显得乖巧"),
        PoseCard("park_squat", Scene.PARK, "蹲下逗花", "蹲在花丛边低头看，自然不摆拍"),
        PoseCard("park_lean", Scene.PARK, "倚树而立", "背靠大树双手插兜，很放松的状态"),
        PoseCard("park_armup", Scene.PARK, "树荫舒展", "在树影下张开手臂，迎接斑驳阳光"),
        // 雪山 (5)
        PoseCard("snow_stand", Scene.SNOW, "张臂看山", "背对镜头张开手臂，大场面更有气势"),
        PoseCard("snow_look", Scene.SNOW, "眺望雪山", "侧身望向远山，安静又有故事感"),
        PoseCard("snow_walk", Scene.SNOW, "雪中行走", "在雪地里走起来，留下一串脚印"),
        PoseCard("snow_jump", Scene.SNOW, "雪地跳跃", "面对雪山跳起来，动态感十足"),
        PoseCard("snow_squat", Scene.SNOW, "蹲捧雪", "蹲下捧一把雪，和雪地互动很可爱"),
        // 夜景 (5)
        PoseCard("night_stand", Scene.NIGHT, "街口站立", "站在街口双手插兜，霓虹灯光当背景"),
        PoseCard("night_walk", Scene.NIGHT, "回眸街拍", "走两步回头看镜头，夜景抓拍很有电影感"),
        PoseCard("night_cross", Scene.NIGHT, "交叉腿靠灯", "路灯下交叉腿站立，光影氛围感"),
        PoseCard("night_lean", Scene.NIGHT, "倚栏看景", "靠在栏杆上看夜景，留个侧影"),
        PoseCard("night_sit", Scene.NIGHT, "街边坐姿", "坐在街边长椅，霓虹灯下很惬意"),
    )

    fun of(scene: Scene): List<PoseCard> = ALL.filter { it.scene == scene }

    fun byId(id: String?): PoseCard? = ALL.firstOrNull { it.id == id }
}
