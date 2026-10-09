package com.aicompose.core.guide

/**
 * Places365 标签的中文名与解析。
 *
 * Places365 是 MIT CSAIL 的国外数据集, 古镇、茶园、网红店这类国内场景没有对应类别,
 * 会被归到 courtyard / palace / restaurant 等近似标签上。
 */
object SceneLabels {

    /** categories_places365.txt 每行形如 `/f/forest/broadleaf 150`, 取去掉 `/x/` 前缀后的名字 */
    fun parse(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val path = line.substringBefore(' ')
            if (path.length > 3 && path[0] == '/') path.substring(3) else path
        }
        .toList()

    private val ZH: Map<String, String> = mapOf(
        "beach" to "海滩", "beach_house" to "海边小屋", "ocean" to "大海", "coast" to "海岸",
        "wave" to "海浪", "islet" to "小岛", "lagoon" to "潟湖", "lake/natural" to "湖泊",
        "river" to "河流", "creek" to "小溪", "waterfall" to "瀑布", "pond" to "池塘",
        "canal/natural" to "河道", "canal/urban" to "城市运河", "fishpond" to "鱼池",
        "pier" to "码头栈桥", "boardwalk" to "木栈道", "harbor" to "港口", "berth" to "泊位",
        "swimming_hole" to "天然泳池", "swimming_pool/outdoor" to "室外泳池", "hot_spring" to "温泉",
        "water_park" to "水上乐园", "dam" to "水坝", "marsh" to "沼泽", "swamp" to "湿地",
        "moat/water" to "护城河", "underwater/ocean_deep" to "水下",

        "mountain" to "山地", "mountain_path" to "山路", "mountain_snowy" to "雪山",
        "valley" to "山谷", "cliff" to "悬崖", "canyon" to "峡谷", "butte" to "孤峰",
        "badlands" to "荒原", "volcano" to "火山", "rock_arch" to "石拱", "crevasse" to "冰裂缝",
        "tundra" to "苔原", "glacier" to "冰川", "desert/sand" to "沙漠", "desert/vegetation" to "荒漠",

        "forest/broadleaf" to "阔叶林", "forest_path" to "林间小道", "forest_road" to "林间公路",
        "rainforest" to "雨林", "bamboo_forest" to "竹林", "tree_farm" to "林地",
        "tree_house" to "树屋", "orchard" to "果园", "grove" to "树丛",

        "field/wild" to "野花地", "field/cultivated" to "农田", "field_road" to "田野小路",
        "wheat_field" to "麦田", "corn_field" to "玉米地", "hayfield" to "干草地",
        "vineyard" to "葡萄园", "rice_paddy" to "稻田", "pasture" to "牧场", "lawn" to "草坪",
        "farm" to "农场", "vegetable_garden" to "菜园", "greenhouse/outdoor" to "温室",
        "greenhouse/indoor" to "温室", "botanical_garden" to "植物园", "formal_garden" to "规则花园",
        "japanese_garden" to "日式庭院", "zen_garden" to "枯山水", "topiary_garden" to "造型花园",
        "park" to "公园", "picnic_area" to "野餐区", "playground" to "游乐场",

        "street" to "街道", "alley" to "小巷", "plaza" to "广场", "downtown" to "市中心",
        "crosswalk" to "斑马线", "highway" to "公路", "driveway" to "车道",
        "promenade" to "滨水步道", "residential_neighborhood" to "住宅区",
        "market/outdoor" to "露天集市", "bazaar/outdoor" to "露天市集", "shopfront" to "店铺门面",
        "building_facade" to "建筑外立面", "doorway/outdoor" to "门廊", "skyscraper" to "摩天楼",
        "bridge" to "桥", "viaduct" to "高架桥", "tower" to "塔", "lighthouse" to "灯塔",
        "windmill" to "风车", "wind_farm" to "风电场", "water_tower" to "水塔",
        "industrial_area" to "工业区", "construction_site" to "工地", "junkyard" to "废品场",
        "parking_lot" to "停车场", "parking_garage/outdoor" to "停车楼",

        "snowfield" to "雪原", "ski_resort" to "滑雪场", "ski_slope" to "雪道",
        "ice_floe" to "浮冰", "iceberg" to "冰山", "igloo" to "冰屋",
        "ice_skating_rink/outdoor" to "室外冰场", "ice_shelf" to "冰架",

        "coffee_shop" to "咖啡馆", "cafeteria" to "食堂", "restaurant" to "餐厅",
        "restaurant_patio" to "餐厅露台", "restaurant_kitchen" to "后厨", "bakery/shop" to "面包店",
        "ice_cream_parlor" to "冰淇淋店", "bookstore" to "书店", "library/indoor" to "图书馆",
        "library/outdoor" to "图书馆外", "bar" to "酒吧", "pub/indoor" to "酒馆",
        "beer_garden" to "啤酒花园", "beer_hall" to "啤酒厅", "food_court" to "美食广场",
        "pizzeria" to "披萨店", "sushi_bar" to "寿司店", "delicatessen" to "熟食店",
        "supermarket" to "超市", "market/indoor" to "室内市场", "bazaar/indoor" to "室内市集",
        "florist_shop/indoor" to "花店", "gift_shop" to "礼品店", "clothing_store" to "服装店",
        "shopping_mall/indoor" to "商场", "department_store" to "百货", "toyshop" to "玩具店",
        "shoe_shop" to "鞋店", "jewelry_shop" to "珠宝店", "hardware_store" to "五金店",
        "drugstore" to "药店", "candy_store" to "糖果店", "museum/indoor" to "博物馆",
        "museum/outdoor" to "博物馆外", "art_gallery" to "画廊", "science_museum" to "科技馆",
        "natural_history_museum" to "自然博物馆", "office" to "办公室",
        "office_building" to "办公楼", "hotel/outdoor" to "酒店外", "hotel_room" to "酒店房间",
        "inn/outdoor" to "旅馆", "youth_hostel" to "青旅", "bedroom" to "卧室",
        "living_room" to "客厅", "kitchen" to "厨房", "dining_room" to "餐厅包间",
        "dining_hall" to "大食堂", "lobby" to "大堂", "corridor" to "走廊",
        "entrance_hall" to "门厅", "staircase" to "楼梯", "elevator_lobby" to "电梯厅",
        "hospital" to "医院", "classroom" to "教室", "lecture_room" to "阶梯教室",
        "auditorium" to "礼堂", "ballroom" to "舞厅", "banquet_hall" to "宴会厅",
        "movie_theater/indoor" to "影院", "bowling_alley" to "保龄球馆", "sauna" to "桑拿房",
        "jacuzzi/indoor" to "按摩浴缸", "basement" to "地下室", "attic" to "阁楼",
        "garage/indoor" to "车库", "laundromat" to "洗衣房", "pharmacy" to "药房",
        "pet_shop" to "宠物店", "beauty_salon" to "美发店", "archive" to "档案室",
        "waiting_room" to "候车室", "reception" to "前台", "art_studio" to "画室",
        "music_studio" to "录音棚", "television_studio" to "演播室", "artists_loft" to "工作室",
        "dorm_room" to "宿舍", "childs_room" to "儿童房", "recreation_room" to "活动室",

        "temple/asia" to "亚洲庙宇", "palace" to "宫殿", "pagoda" to "宝塔", "castle" to "城堡",
        "church/outdoor" to "教堂外", "church/indoor" to "教堂内", "mosque/outdoor" to "清真寺",
        "synagogue/outdoor" to "犹太会堂", "courtyard" to "庭院", "arch" to "拱门",
        "ruin" to "遗迹", "kasbah" to "古城堡", "medina" to "老城区", "village" to "村落",
        "mausoleum" to "陵墓", "catacomb" to "地下墓穴", "burial_chamber" to "墓室",
        "aqueduct" to "渡槽", "alcove" to "壁龛", "pavilion" to "亭子",
        "gazebo/exterior" to "凉亭", "amphitheater" to "露天剧场", "fountain" to "喷泉",
        "archaelogical_excavation" to "考古现场", "excavation" to "发掘现场",
        "monument" to "纪念碑", "mansion" to "宅邸", "cottage" to "乡间小屋",
        "cabin/outdoor" to "木屋", "chalet" to "木屋", "house" to "住宅",
        "apartment_building/outdoor" to "公寓楼", "porch" to "门廊", "patio" to "露台",
        "roof_garden" to "屋顶花园", "balcony/exterior" to "阳台", "yard" to "院子",
        "cemetery" to "墓园", "grotto" to "石窟",

        "campus" to "校园", "schoolhouse" to "校舍", "athletic_field/outdoor" to "运动场",
        "stadium/soccer" to "足球场", "stadium/football" to "橄榄球场", "stadium/baseball" to "棒球场",
        "soccer_field" to "足球场", "football_field" to "球场", "baseball_field" to "棒球场",
        "basketball_court/indoor" to "篮球馆", "volleyball_court/outdoor" to "排球场",
        "golf_course" to "高尔夫球场", "racecourse" to "赛马场", "raceway" to "赛道",
        "gymnasium/indoor" to "体育馆", "martial_arts_gym" to "武术馆", "playroom" to "游戏室",
        "kindergarden_classroom" to "幼儿园教室", "nursery" to "育儿室",

        "car_interior" to "车内", "train_interior" to "火车内", "bus_interior" to "公交车内",
        "subway_station/platform" to "地铁站台", "train_station/platform" to "火车站台",
        "bus_station/indoor" to "汽车站", "airplane_cabin" to "机舱", "cockpit" to "驾驶舱",
        "airport_terminal" to "机场航站楼", "airfield" to "停机坪", "runway" to "跑道",

        "amusement_park" to "游乐园", "discotheque" to "迪厅", "carrousel" to "旋转木马",
        "ball_pit" to "球池", "ticket_booth" to "售票亭",
        "sky" to "天空", "campsite" to "营地", "shed" to "棚屋", "stable" to "马厩",
        "corral" to "畜栏", "kennel/outdoor" to "犬舍", "landfill" to "垃圾场",
        "oilrig" to "钻井平台", "hangar/indoor" to "机库", "heliport" to "直升机坪",
        "fire_station" to "消防站", "courthouse" to "法院", "embassy" to "使馆",
        "legislative_chamber" to "议事厅", "throne_room" to "王座厅", "bullring" to "斗牛场",
        "boxing_ring" to "拳台", "arena/performance" to "演出场馆", "arena/hockey" to "冰球场",
        "arena/rodeo" to "竞技场", "stage/indoor" to "室内舞台", "stage/outdoor" to "室外舞台",
        "general_store/indoor" to "杂货店", "general_store/outdoor" to "杂货铺",
        "flea_market/indoor" to "跳蚤市场", "butchers_shop" to "肉铺", "fabric_store" to "布料店",
        "gas_station" to "加油站", "garage/outdoor" to "修理厂", "auto_showroom" to "汽车展厅",
        "auto_factory" to "汽车工厂", "assembly_line" to "流水线", "loading_dock" to "装卸台",
        "fire_escape" to "消防梯", "elevator/door" to "电梯门", "escalator/indoor" to "扶梯",
        "phone_booth" to "电话亭", "bow_window/indoor" to "凸窗", "storage_room" to "储藏室",
        "utility_room" to "杂物间", "shower" to "淋浴间", "bathroom" to "浴室",
        "pantry" to "食品间", "bedchamber" to "寝宫", "dressing_room" to "更衣室",
        "locker_room" to "更衣室", "home_office" to "家庭办公室", "home_theater" to "家庭影院",
        "television_room" to "电视房", "computer_room" to "机房", "server_room" to "服务器机房",
        "biology_laboratory" to "生物实验室", "chemistry_lab" to "化学实验室",
        "physics_laboratory" to "物理实验室", "clean_room" to "洁净室", "operating_room" to "手术室",
        "hospital_room" to "病房", "nursing_home" to "养老院", "jail_cell" to "牢房",
        "bank_vault" to "金库", "office_cubicles" to "格子间", "conference_room" to "会议室",
        "conference_center" to "会议中心", "mezzanine" to "夹层", "atrium/public" to "中庭",
        "engine_room" to "机舱房", "boat_deck" to "甲板", "landing_deck" to "着陆甲板", "raft" to "木筏",
        "rope_bridge" to "索桥", "trench" to "沟渠", "desert_road" to "荒漠公路",
        "railroad_track" to "铁道", "repair_shop" to "修理铺", "sandbox" to "沙池",
        "watering_hole" to "水塘", "lock_chamber" to "船闸", "hunting_lodge/outdoor" to "狩猎小屋",
    )

    /** 取中文名, 找不到就回退到原始标签 */
    fun zh(label: String): String {
        ZH[label]?.let { return it }
        ZH[label.substringAfterLast('/')]?.let { return it }
        return label.substringAfterLast('/').replace('_', ' ')
    }
}
