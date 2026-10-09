package com.aicompose.core.guide

import kotlin.math.sqrt

/** 检测到的实时关节 (MediaPipe 归一化坐标 + 可见度) */
data class DetectedJoint(val x: Float, val y: Float, val visibility: Float)

/** 姿态对齐状态 (驱动骨架颜色与文案) */
enum class AlignmentState { IDLE, NEAR, MATCHED }

/** 一根骨骼: 起止 landmark id + 中文名 (用于生成"调整左小臂"这类提示) */
data class Bone(val from: Int, val to: Int, val name: String)

/**
 * 启发式构图决策中枢。
 *
 * 1. 景别计算: 人物高度占比 (踝 - 鼻) → 全身 / 半身 / 特写
 * 2. 姿态相似度: 质心对齐 + 尺度归一后的骨骼向量余弦
 * 3. 引导文案: 按状态机输出一句话指引
 */
object CompositionEngine {

    /** 骨架绘制连线 (MediaPipe id) */
    val DRAW_BONES: List<Pair<Int, Int>> = listOf(
        11 to 12,   // 肩线
        11 to 13, 13 to 15,   // 左臂
        12 to 14, 14 to 16,   // 右臂
        23 to 24,   // 髋线
        11 to 23, 12 to 24,   // 躯干两侧
        23 to 25, 25 to 27,   // 左腿
        24 to 26, 26 to 28,   // 右腿
    )

    /** 打分用的方向向量骨骼 (躯干用肩中点→髋中点合成) */
    private val SCORE_BONES = listOf(
        Bone(11, 12, "肩膀"),
        Bone(11, 13, "左大臂"), Bone(13, 15, "左小臂"),
        Bone(12, 14, "右大臂"), Bone(14, 16, "右小臂"),
        Bone(23, 24, "胯部"),
        Bone(23, 25, "左大腿"), Bone(25, 27, "左小腿"),
        Bone(24, 26, "右大腿"), Bone(26, 28, "右小腿"),
    )

    private const val VIS_MIN = 0.4f

    // ---------------- 景别判定 ----------------

    /**
     * 人物高度占比 H = max(左踝y, 右踝y) - 鼻y (归一化坐标, y 向下)。
     * 无有效人体返回 null。
     */
    fun personHeight(joints: Map<Int, DetectedJoint>): Float? {
        val nose = joints[0] ?: return null
        if (nose.visibility < VIS_MIN) return null
        val ankles = listOfNotNull(
            joints[27]?.takeIf { it.visibility >= VIS_MIN },
            joints[28]?.takeIf { it.visibility >= VIS_MIN },
        )
        val ankleY = ankles.maxOfOrNull { it.y } ?: return null
        return (ankleY - nose.y).coerceAtLeast(0f)
    }

    fun detectShotType(joints: Map<Int, DetectedJoint>): ShotType? {
        val h = personHeight(joints) ?: return null
        return when {
            h > 0.65f -> ShotType.FULL_BODY
            h >= 0.35f -> ShotType.HALF_BODY
            else -> ShotType.CLOSE_UP
        }
    }

    /** 判断「人有没有被挡住 / 被切掉」时看的核心点 */
    private val CORE_JOINTS = intArrayOf(0, 11, 12, 23, 24, 25, 26, 27, 28)

    /** 站位/遮挡所需的可测属性; 没有一个核心点可见时返回 null */
    fun metrics(joints: Map<Int, DetectedJoint>): PersonMetrics? {
        val visible = CORE_JOINTS.toList().mapNotNull { id ->
            joints[id]?.takeIf { it.visibility >= VIS_MIN }
        }
        if (visible.isEmpty()) return null
        val minX = visible.minOf { it.x }
        val maxX = visible.maxOf { it.x }
        val maxY = visible.maxOf { it.y }
        val cropped = visible.any {
            it.x <= 0.02f || it.x >= 0.98f || it.y <= 0.02f || it.y >= 0.98f
        }
        return PersonMetrics(
            visibleRatio = visible.size / CORE_JOINTS.size.toFloat(),
            cropped = cropped,
            centerX = (minX + maxX) / 2f,
            footY = maxY,
            heightRatio = personHeight(joints),
        )
    }

    // ---------------- 姿态相似度 ----------------

    data class Similarity(
        /** 0~1; -1 表示有效骨骼不足, 无法打分 */
        val score: Float,
        /** 偏差最大 (余弦最小) 的骨骼名, 用于生成提示 */
        val worstBone: String?,
    )

    /**
     * 骨骼向量余弦相似度:
     * - 用户关键点先按 [aspect] 校正纵横失真 (归一化坐标 x 需乘宽高比才是度量空间)
     * - 减髋部中心 (去平移)、除肩宽 (去尺度), 消除身高与景深差异
     * - 每根骨骼取 (起→止) 方向向量, 与模板对应向量求 cos, 取 max(0, cos) 平均
     */
    fun similarity(
        user: Map<Int, DetectedJoint>,
        template: PoseTemplate,
        aspect: Float = 1f,
    ): Similarity {
        // —— 用户骨架: 归一化 ——
        val uHip = midpointVisible(user, 23, 24) ?: return Similarity(-1f, null)
        val uShL = user[11]?.takeIf { it.visibility >= VIS_MIN }
        val uShR = user[12]?.takeIf { it.visibility >= VIS_MIN }
        val shoulderW = if (uShL != null && uShR != null) {
            val dx = (uShL.x - uShR.x) * aspect
            val dy = uShL.y - uShR.y
            sqrt(dx * dx + dy * dy)
        } else 0f
        if (shoulderW < 1e-4f) return Similarity(-1f, null)

        fun un(id: Int): Pair<Float, Float>? {
            val j = user[id] ?: return null
            return ((j.x * aspect - uHip.first) / shoulderW) to ((j.y - uHip.second) / shoulderW)
        }

        // —— 模板骨架: 同样归一化 (方形画布, aspect=1) ——
        val tj = template.joints
        val tHip = midpoint(tj[23], tj[24]) ?: return Similarity(-1f, null)
        val tShL = tj[11] ?: return Similarity(-1f, null)
        val tShR = tj[12] ?: return Similarity(-1f, null)
        val tShoulderW = sqrt((tShL.x - tShR.x).let { it * it } + (tShL.y - tShR.y).let { it * it })
        if (tShoulderW < 1e-4f) return Similarity(-1f, null)

        fun tn(id: Int): Pair<Float, Float>? {
            val j = tj[id] ?: return null
            return ((j.x - tHip.first) / tShoulderW) to ((j.y - tHip.second) / tShoulderW)
        }

        // —— 逐骨骼打分 ——
        var sum = 0f
        var count = 0
        var worstCos = Float.MAX_VALUE
        var worstBone: String? = null

        fun scoreBone(b: Bone) {
            val ua = un(b.from) ?: return
            val ub = un(b.to) ?: return
            val ta = tn(b.from) ?: return
            val tb = tn(b.to) ?: return
            val vux = ua.first - ub.first; val vuy = ua.second - ub.second
            val vtx = ta.first - tb.first; val vty = ta.second - tb.second
            val lu = sqrt(vux * vux + vuy * vuy)
            val lt = sqrt(vtx * vtx + vty * vty)
            if (lu < 1e-4f || lt < 1e-4f) return   // 骨骼长度为 0 (遮挡/重合), 跳过
            val cos = ((vux * vtx + vuy * vty) / (lu * lt)).coerceIn(-1f, 1f)
            sum += cos.coerceAtLeast(0f)
            count++
            if (cos < worstCos) { worstCos = cos; worstBone = b.name }
        }

        for (b in SCORE_BONES) scoreBone(b)
        // 躯干: 肩中点 → 髋中点
        val uShMid = midpointVisible(user, 11, 12)
        val tShMid = midpoint(tj[11], tj[12])
        if (uShMid != null && tShMid != null) {
            // 构造虚拟骨骼: 手动打分
            val vux = uShMid.first - uHip.first; val vuy = uShMid.second - uHip.second
            val vtx = tShMid.first - tHip.first; val vty = tShMid.second - tHip.second
            val lu = sqrt(vux * vux + vuy * vuy)
            val lt = sqrt(vtx * vtx + vty * vty)
            if (lu > 1e-4f && lt > 1e-4f) {
                val cos = ((vux * vtx + vuy * vty) / (lu * lt)).coerceIn(-1f, 1f)
                sum += cos.coerceAtLeast(0f)
                count++
                if (cos < worstCos) { worstCos = cos; worstBone = "躯干" }
            }
        }

        if (count < 4) return Similarity(-1f, null)   // 骨骼太少, 不可信
        return Similarity(score = sum / count, worstBone = worstBone)
    }

    private fun midpointVisible(
        m: Map<Int, DetectedJoint>, a: Int, b: Int,
    ): Pair<Float, Float>? {
        val ja = m[a]?.takeIf { it.visibility >= VIS_MIN } ?: return null
        val jb = m[b]?.takeIf { it.visibility >= VIS_MIN } ?: return null
        return ((ja.x + jb.x) / 2f) to ((ja.y + jb.y) / 2f)
    }

    private fun midpoint(a: Joint?, b: Joint?): Pair<Float, Float>? {
        if (a == null || b == null) return null
        return ((a.x + b.x) / 2f) to ((a.y + b.y) / 2f)
    }
}
