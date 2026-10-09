package com.aicompose.core.guide

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** 可测量的关节特征, 单位: ABDUCT/ELBOW/KNEE/TORSO_LEAN 为度, LEG_SPREAD 为踝间距/髋宽×100 */
enum class AngleKey(val zh: String) {
    ARM_L_ABDUCT("画面左臂抬起"),
    ARM_R_ABDUCT("画面右臂抬起"),
    ELBOW_L("画面左肘"),
    ELBOW_R("画面右肘"),
    KNEE_L("画面左膝"),
    KNEE_R("画面右膝"),
    TORSO_LEAN("上身倾斜"),
    LEG_SPREAD("两脚张开");

    /** 左右互换时对应的另一项; TORSO_LEAN / LEG_SPREAD 自对称 */
    fun mirrored(): AngleKey = when (this) {
        ARM_L_ABDUCT -> ARM_R_ABDUCT
        ARM_R_ABDUCT -> ARM_L_ABDUCT
        ELBOW_L -> ELBOW_R
        ELBOW_R -> ELBOW_L
        KNEE_L -> KNEE_R
        KNEE_R -> KNEE_L
        else -> this
    }

    /** 镜像时数值需要取反的项 (倾斜有方向性, 张开和弯折没有) */
    val flipsOnMirror: Boolean get() = this == TORSO_LEAN

    companion object {
        fun parse(name: String): AngleKey? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** 把角度偏差翻译成一句人话。delta = 实际值 − 目标值 */
fun AngleKey.coach(delta: Float): String = when (this) {
    AngleKey.ARM_L_ABDUCT -> if (delta > 0) "画面左边的手臂放低一点" else "画面左边的手臂再抬高一点"
    AngleKey.ARM_R_ABDUCT -> if (delta > 0) "画面右边的手臂放低一点" else "画面右边的手臂再抬高一点"
    AngleKey.ELBOW_L -> if (delta > 0) "画面左边的手肘再弯一点" else "画面左边的手臂伸直一点"
    AngleKey.ELBOW_R -> if (delta > 0) "画面右边的手肘再弯一点" else "画面右边的手臂伸直一点"
    AngleKey.KNEE_L -> if (delta > 0) "画面左边的腿再弯一点" else "画面左边的腿伸直一点"
    AngleKey.KNEE_R -> if (delta > 0) "画面右边的腿再弯一点" else "画面右边的腿伸直一点"
    AngleKey.TORSO_LEAN -> if (delta > 0) "上身向画面左边回一点" else "上身向画面右边倾一点"
    AngleKey.LEG_SPREAD -> if (delta > 0) "两脚收拢一点" else "两脚再分开一点"
}

// MediaPipe Pose 关键点索引
private const val NOSE = 0
private const val SHOULDER_L = 11
private const val SHOULDER_R = 12
private const val ELBOW_L = 13
private const val ELBOW_R = 14
private const val WRIST_L = 15
private const val WRIST_R = 16
private const val HIP_L = 23
private const val HIP_R = 24
private const val KNEE_L = 25
private const val KNEE_R = 26
private const val ANKLE_L = 27
private const val ANKLE_R = 28

/**
 * 模板的**可验证部分**: 「人应该怎么摆」是给人看的文案, 「摆对了之后关节角是多少」
 * 才是给机器比的数值 —— 必须有数值才能进闭环。
 *
 * 与 aicamera 不同的是, 这里**从模板骨架几何直接推导**目标角度 (模板本身就是标准答案),
 * 不需要手工维护 pose_specs.json, 也不会出现 id 对不上的问题。
 * `verified` 恒为 false: 初值未经真人采样校准, 提示里会如实标注。
 */
data class PoseSpec(
    val templateId: String,
    /** 目标重心 x (画面归一化) */
    val targetCx: Float,
    /** 目标脚线 y (画面归一化, y 向下) */
    val targetFootY: Float,
    /** 目标人物高度占比 */
    val bboxHRatio: Float,
    /** 模板包围盒的视觉宽高比 (用于画站位框) */
    val boxAspect: Float = 0.45f,
    val angles: Map<AngleKey, Float>,
    val tol: Map<AngleKey, Float>,
    val weights: Map<AngleKey, Float>,
    /** 左右互换算不算对 (大多数姿势左右都行) */
    val mirrored: Boolean = true,
    /** 角度值是否经过真人采样验证 */
    val verified: Boolean = false,
) {
    fun tolOf(key: AngleKey): Float = tol[key] ?: DEFAULT_TOLS.getValue(key)
    fun weightOf(key: AngleKey): Float = weights[key] ?: 1f

    companion object {
        private val DEFAULT_TOLS = mapOf(
            AngleKey.ARM_L_ABDUCT to 25f,
            AngleKey.ARM_R_ABDUCT to 25f,
            AngleKey.ELBOW_L to 25f,
            AngleKey.ELBOW_R to 25f,
            AngleKey.KNEE_L to 28f,
            AngleKey.KNEE_R to 28f,
            AngleKey.TORSO_LEAN to 12f,
            AngleKey.LEG_SPREAD to 30f,
        )
    }
}

/** 模板 → 角度规格 (进程内缓存) */
object PoseSpecs {

    private val cache = HashMap<String, PoseSpec>()

    fun derive(template: PoseTemplate): PoseSpec = synchronized(cache) {
        cache[template.id]?.let { return it }

        val pts = template.joints.mapValues { (_, j) -> j.x to j.y }
        val angles = computeAngles(pts)

        val xs = template.joints.values.map { it.x }
        val ys = template.joints.values.map { it.y }
        val boxW = (xs.max() - xs.min()).coerceAtLeast(0.05f)
        val boxH = (ys.max() - ys.min()).coerceAtLeast(0.05f)
        val spec = PoseSpec(
            templateId = template.id,
            targetCx = ((xs.min() + xs.max()) / 2f).coerceIn(0.15f, 0.85f),
            // 参考画布里的脚线贴近底边, 作为画面目标时压到 0.88 以内, 否则会一直提示往下压
            targetFootY = ys.max().coerceIn(0.3f, 0.88f),
            bboxHRatio = template.targetPersonRatio,
            boxAspect = boxW / boxH,
            angles = angles,
            tol = emptyMap(),
            weights = emptyMap(),
        )
        cache[template.id] = spec
        spec
    }
}

object PoseSpecMatcher {

    /** 某一个关节的偏差情况。exceeded = 超出容差的部分 (未超出为 0), 越大越严重 */
    data class Diff(
        val key: AngleKey,
        val target: Float,
        val actual: Float,
        val delta: Float,
        val exceeded: Float,
        val ok: Boolean,
    )

    fun diffs(person: Map<Int, DetectedJoint>, spec: PoseSpec): List<Diff> {
        val actual = personAngles(person)
        if (actual.isEmpty()) return emptyList()
        val direct = rawDiffs(actual, spec, mirror = false)
        val flipped = if (spec.mirrored) rawDiffs(actual, spec, mirror = true) else null
        return listOfNotNull(direct, flipped)
            .minByOrNull { d -> d.sumOf { it.exceeded.toDouble() } } ?: emptyList()
    }

    /** 当前最该改的那一个关节 */
    fun worst(person: Map<Int, DetectedJoint>, spec: PoseSpec): Diff? =
        diffs(person, spec).maxByOrNull { it.exceeded }?.takeIf { !it.ok }

    /** 0..1, 给 UI 做「变绿」进度 */
    fun score(person: Map<Int, DetectedJoint>, spec: PoseSpec): Float {
        val list = diffs(person, spec)
        if (list.isEmpty()) return -1f
        val total = list.sumOf { it.exceeded.toDouble() }.toFloat()
        return 1f / (1f + total / SCORE_SCALE)
    }

    private fun rawDiffs(
        actual: Map<AngleKey, Float>,
        spec: PoseSpec,
        mirror: Boolean,
    ): List<Diff> {
        val out = ArrayList<Diff>(spec.angles.size)
        for ((key, target) in spec.angles) {
            val liveKey = if (mirror) key.mirrored() else key
            val v = actual[liveKey] ?: continue
            val signed = if (mirror && key.flipsOnMirror) -v else v
            val delta = signed - target
            val gap = abs(delta) - spec.tolOf(key)
            out += Diff(
                key = key,
                target = target,
                actual = v,
                delta = delta,
                exceeded = if (gap > 0f) gap * spec.weightOf(key) else 0f,
                ok = gap <= 0f,
            )
        }
        return out
    }

    private const val SCORE_SCALE = 40f
}

/** 实时骨架 (可见度 ≥ 阈值) → 角度集合; 关键点缺失的角度直接不参与比对 */
private fun personAngles(joints: Map<Int, DetectedJoint>): Map<AngleKey, Float> {
    val pts = HashMap<Int, Pair<Float, Float>>(joints.size)
    for ((id, j) in joints) {
        if (j.visibility >= VIS_MIN) pts[id] = j.x to j.y
    }
    return computeAngles(pts)
}

private const val VIS_MIN = 0.4f

/** 从 2D 关键点算关节角; 缺点的角度跳过 (返回的 map 里没有这一项) */
private fun computeAngles(pts: Map<Int, Pair<Float, Float>>): Map<AngleKey, Float> {
    val out = HashMap<AngleKey, Float>(8)

    fun angle(key: AngleKey, a: Int, vertex: Int, b: Int) {
        val pa = pts[a] ?: return
        val pv = pts[vertex] ?: return
        val pb = pts[b] ?: return
        out[key] = included(pa, pv, pb)
    }

    angle(AngleKey.ARM_L_ABDUCT, HIP_L, SHOULDER_L, ELBOW_L)
    angle(AngleKey.ARM_R_ABDUCT, HIP_R, SHOULDER_R, ELBOW_R)
    angle(AngleKey.ELBOW_L, SHOULDER_L, ELBOW_L, WRIST_L)
    angle(AngleKey.ELBOW_R, SHOULDER_R, ELBOW_R, WRIST_R)
    angle(AngleKey.KNEE_L, HIP_L, KNEE_L, ANKLE_L)
    angle(AngleKey.KNEE_R, HIP_R, KNEE_R, ANKLE_R)

    val shL = pts[SHOULDER_L]
    val shR = pts[SHOULDER_R]
    val hipL = pts[HIP_L]
    val hipR = pts[HIP_R]
    if (shL != null && shR != null && hipL != null && hipR != null) {
        val shoulderMid = (shL.first + shR.first) / 2f to (shL.second + shR.second) / 2f
        val hipMid = (hipL.first + hipR.first) / 2f to (hipL.second + hipR.second) / 2f
        // 上身倾斜: 躯干方向与「垂直向下」的夹角, 正 = 上身向画面右侧倾
        val torsoDx = shoulderMid.first - hipMid.first
        val torsoDy = shoulderMid.second - hipMid.second
        // atan2(Float, Float) 返回 Float, Math.toDegrees 只收 Double —— 必须显式转换
        out[AngleKey.TORSO_LEAN] =
            Math.toDegrees(atan2(torsoDx, torsoDy).toDouble()).toFloat()

        val hipW = dist(hipL, hipR)
        val ankleL = pts[ANKLE_L]
        val ankleR = pts[ANKLE_R]
        if (hipW > 1e-4f && ankleL != null && ankleR != null) {
            out[AngleKey.LEG_SPREAD] = abs(ankleL.first - ankleR.first) / hipW * 100f
        }
    }
    return out
}

/** 三点夹角, 顶点是中间那个点: 180° = 完全伸直, 越小越弯 */
private fun included(a: Pair<Float, Float>, vertex: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val v1x = a.first - vertex.first
    val v1y = a.second - vertex.second
    val v2x = b.first - vertex.first
    val v2y = b.second - vertex.second
    val n1 = sqrt(v1x * v1x + v1y * v1y)
    val n2 = sqrt(v2x * v2x + v2y * v2y)
    if (n1 < 1e-4f || n2 < 1e-4f) return 180f
    val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cos).toDouble()).toFloat()
}

private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
    val dx = a.first - b.first
    val dy = a.second - b.second
    return sqrt(dx * dx + dy * dy)
}
