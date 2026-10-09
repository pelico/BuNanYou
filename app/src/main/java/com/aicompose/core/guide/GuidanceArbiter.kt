package com.aicompose.core.guide

import kotlin.math.abs

/** 画面里**能测出来**的人体属性 (画面归一化坐标) */
data class PersonMetrics(
    /** 核心关键点的可见比例, 低说明人被挡住或没完全进画面 */
    val visibleRatio: Float,
    /** 包围盒是否触碰画面边缘 —— 人可能被切掉了 */
    val cropped: Boolean,
    val centerX: Float,
    val footY: Float,
    /** 鼻子-踝的高度占比 (与模板 targetPersonRatio 同口径), 测不出返回 null */
    val heightRatio: Float?,
)

/** 指令严重程度, 决定 UI 颜色 */
enum class Severity { BLOCKER, WARN, HINT, OK }

/**
 * 一条给用户的可执行指令。**一次只产出一条** —— 三条并列的结果是用户一条都不看。
 * [priority] 数字越小越先说。
 */
data class Guidance(
    val text: String,
    val detail: String? = null,
    val severity: Severity = Severity.HINT,
    val priority: Int,
    val progress: Float = 0f,
    val key: String,
)

/**
 * 决策层: 把所有「可测量的偏差」收敛成**一条**指令 (移植自 aicamera)。
 *
 * 优先级: 5 场景不宜 → 10 没检到人 → 20 人被挡/没进全 → 30 水平 → 32 俯仰
 * → 40 景别 → 45 无模板景别引导 → 50 站位左右 → 52 站位上下 → 60 姿势角度
 * → 65 骨架跟随 → 90 场景偏弱 → 100 可以拍了
 *
 * 刻意不给的建议: 机位高度、表情、眼神 —— 这些测不出来, 给就是编的。
 */
object GuidanceArbiter {

    fun decide(
        person: Map<Int, DetectedJoint>?,
        template: PoseTemplate?,
        spec: PoseSpec?,
        sim: CompositionEngine.Similarity?,
        rollDeg: Float,
        pitchDeg: Float,
        usability: SceneUsability = SceneUsability.OK,
        sceneZh: String? = null,
    ): Guidance {
        val metrics = person?.let { CompositionEngine.metrics(it) }
        val out = ArrayList<Guidance>(8)

        // 场景本身不适合拍人像时, 说什么站位姿势都没意义, 直接拦在最前面
        if (usability == SceneUsability.POOR) {
            out += Guidance(
                text = "这里不建议拍人像",
                detail = (sceneZh?.let { "识别为$it · " } ?: "") + SceneUsability.POOR.advice,
                severity = Severity.WARN,
                priority = 5,
                progress = 0.2f,
                key = "scene_poor",
            )
            return out.minByOrNull { it.priority }!!
        }

        if (metrics == null) {
            out += Guidance(
                text = "画面里没检测到人",
                detail = "把人放进画面；光线过暗、完全背身、衣物过于宽松时都可能漏检",
                severity = Severity.BLOCKER,
                priority = 10,
                key = "no_person",
            )
            return out.minByOrNull { it.priority }!!
        }

        if (metrics.visibleRatio < 0.75f || metrics.cropped) {
            out += Guidance(
                text = if (metrics.cropped) "人被画面边缘切到了，退后一点让人进全"
                else "人被挡住了一部分，换个角度或往前站",
                detail = "关键点在画面内的比例 %.0f%%".format(metrics.visibleRatio * 100),
                severity = Severity.BLOCKER,
                priority = 20,
                progress = metrics.visibleRatio,
                key = "occluded",
            )
        }

        val roll = abs(rollDeg)
        if (roll > TILT_TOL) {
            out += Guidance(
                text = "端平手机：现在歪了 %.1f°".format(roll),
                detail = if (rollDeg > 0) "把画面左边抬高一点" else "把画面右边抬高一点",
                severity = if (roll > TILT_WARN) Severity.WARN else Severity.HINT,
                priority = 30,
                progress = (1f - roll / 10f).coerceIn(0f, 1f),
                key = "level",
            )
        }

        if (template?.shotType == ShotType.FULL_BODY && pitchDeg > 5f) {
            out += Guidance(
                text = "把手机放平，避免把人拍矮",
                detail = "现在俯角 %.0f°".format(pitchDeg),
                severity = Severity.HINT,
                priority = 32,
                key = "pitch",
            )
        }

        if (template == null || spec == null) {
            // 还没选模板: 只按当前景别给基础构图建议
            metrics.heightRatio?.let { h ->
                out += when {
                    h > 0.65f -> Guidance("全身像：双脚贴近画面底边，机位下沉到腰部",
                        priority = 45, key = "shot_full")
                    h >= 0.35f -> Guidance("半身像：眼睛对齐画面上 1/3 线",
                        priority = 45, key = "shot_half")
                    else -> Guidance("特写：把人物放在左右三分交点上",
                        priority = 45, key = "shot_close")
                }
            }
        } else {
            val targetRatio = spec.bboxHRatio
            val h = metrics.heightRatio
            if (h != null) {
                val gap = h - targetRatio
                if (abs(gap) > RATIO_TOL) {
                    out += Guidance(
                        text = if (gap < 0) "走近一点，人物再大一点" else "退远一点，人物快装不下了",
                        detail = "人占画面高度 %.0f%%，目标 %.0f%%".format(h * 100, targetRatio * 100),
                        severity = if (abs(gap) > RATIO_WARN) Severity.WARN else Severity.HINT,
                        priority = 40,
                        progress = (1f - abs(gap) / 0.3f).coerceIn(0f, 1f),
                        key = "shot_size",
                    )
                }
            }

            val dx = cxDelta(metrics.centerX, spec.targetCx, spec.mirrored)
            if (abs(dx) > CX_TOL) {
                out += Guidance(
                    text = if (dx > 0) "人往画面左边挪一点" else "人往画面右边挪一点",
                    detail = "目标让重心落在画面 %.0f%% 处".format(spec.targetCx * 100),
                    severity = Severity.HINT,
                    priority = 50,
                    progress = (1f - abs(dx) / 0.25f).coerceIn(0f, 1f),
                    key = "position_x",
                )
            }

            val dy = metrics.footY - spec.targetFootY
            if (abs(dy) > FOOT_TOL) {
                out += Guidance(
                    text = if (dy > 0) "手机往上抬一点，脚下留太多空白了"
                    else "手机往下压一点，脚要留出一点边",
                    detail = "当前脚线 %.0f%%，目标 %.0f%%".format(metrics.footY * 100, spec.targetFootY * 100),
                    severity = Severity.HINT,
                    priority = 52,
                    progress = (1f - abs(dy) / 0.25f).coerceIn(0f, 1f),
                    key = "position_y",
                )
            }

            // metrics 出得来说明 person 一定非空 (上面已 return)
            val diffs = PoseSpecMatcher.diffs(person, spec)
            if (diffs.isEmpty()) {
                // 关键点不足, 角度测不出来 → 退回骨架跟随
                addSkeletonFallback(out, sim, template)
            } else {
                val diff = diffs.maxByOrNull { it.exceeded }?.takeIf { !it.ok }
                if (diff != null) {
                    // 角度目标是从模板骨架推的初值, 没经真人采样校准前如实标注
                    val calibrated = if (spec.verified) "" else " · 目标未校准"
                    out += Guidance(
                        text = diff.key.coach(diff.delta),
                        detail = "现在 %.0f，目标 %.0f（±%.0f）%s".format(
                            diff.actual, diff.target, spec.tolOf(diff.key), calibrated,
                        ),
                        severity = Severity.HINT,
                        priority = 60,
                        progress = PoseSpecMatcher.score(person, spec),
                        key = "angle_${diff.key.name}",
                    )
                }
            }
        }

        if (usability == SceneUsability.WEAK) {
            out += Guidance(
                text = "这里能拍，但要挑角度",
                detail = SceneUsability.WEAK.advice,
                severity = Severity.HINT,
                priority = 90,
                progress = 0.6f,
                key = "scene_weak",
            )
        }

        if (out.isEmpty()) {
            out += ready(spec)
        }
        return out.minByOrNull { it.priority }!!
    }

    private fun addSkeletonFallback(
        out: ArrayList<Guidance>,
        sim: CompositionEngine.Similarity?,
        template: PoseTemplate?,
    ) {
        val s = sim?.score ?: -1f
        if (s < 0f) {
            // 连骨架相似度都算不出来 (骨骼太少), 只能给模板自带的参考文案
            val text = template?.guideText
            if (!text.isNullOrBlank()) {
                out += Guidance(text = text, severity = Severity.HINT, priority = 65, key = "guide_text")
            }
            return
        }
        if (s >= 0.80f) return
        out += Guidance(
            text = if (s >= 0.60f) "接近了！注意调整${sim?.worstBone ?: "姿势"}"
            else "跟随虚线骨架，调整${sim?.worstBone ?: "姿势"}",
            severity = Severity.HINT,
            priority = 65,
            progress = s,
            key = "skeleton",
        )
    }

    private fun ready(spec: PoseSpec?): Guidance = Guidance(
        text = "可以拍了 ✓",
        detail = when {
            spec == null -> "基础构图检查已通过"
            spec.verified -> "站位、景别、角度都在容差内"
            else -> "站位与景别已到位；角度目标是未经校准的初值"
        },
        severity = Severity.OK,
        priority = 100,
        progress = 1f,
        key = "ready",
    )

    /** 允许左右镜像时, 取离目标更近的那条三分线 */
    private fun cxDelta(actual: Float, target: Float, mirrored: Boolean): Float {
        val direct = actual - target
        if (!mirrored) return direct
        val flipped = actual - (1f - target)
        return if (abs(flipped) < abs(direct)) flipped else direct
    }

    private const val TILT_TOL = 3f
    private const val TILT_WARN = 6f
    private const val RATIO_TOL = 0.06f
    private const val RATIO_WARN = 0.15f
    private const val CX_TOL = 0.08f
    private const val FOOT_TOL = 0.10f
}
