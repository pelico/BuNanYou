package com.aicompose.core.vision.pose

import android.graphics.PointF
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 姿势教练 —— 纯端侧规则引擎。
 *
 * 输入 [DetectedPose], 输出可执行的中文动作建议 + 参考姿势匹配度。
 * 所有几何计算都在归一化图像坐标下进行, 与分辨率无关。
 */
object PoseCoach {

    /** 没有任何检测结果时, 参考骨架的默认落位 (居中偏下, 留出头顶空间) */
    val DEFAULT_BOX = BoxF(0.28f, 0.10f, 0.72f, 0.96f)

    // ---------------------------------------------------------------- 几何

    fun personBox(pose: DetectedPose): BoxF? {
        val pts = pose.visible
        if (pts.size < 4) return null
        return BoxF(
            left = pts.minOf { it.x },
            top = pts.minOf { it.y },
            right = pts.maxOf { it.x },
            bottom = pts.maxOf { it.y },
        )
    }

    /** 人体占画面高度比例 (0~1) */
    private fun bodyHeightRatio(pose: DetectedPose): Float? {
        val box = personBox(pose) ?: return null
        return box.height
    }

    // ---------------------------------------------------------------- 建议

    /**
     * 生成建议, 重要的排在前面。
     */
    fun buildAdvice(pose: DetectedPose): List<PoseAdvice> {
        val out = ArrayList<PoseAdvice>()
        val box = personBox(pose)
        if (box == null) {
            return listOf(PoseAdvice(AdviceLevel.WARN, "没检测到人物，请让被拍者完整进入画面"))
        }

        // 1. 人物大小
        val h = box.height
        when {
            h < 0.35f -> out += PoseAdvice(AdviceLevel.WARN, "人物偏小，走近一点或把焦距拉近")
            h > 0.96f -> out += PoseAdvice(AdviceLevel.WARN, "人物太满，退后半步留出头顶和脚部空间")
            else -> out += PoseAdvice(AdviceLevel.GOOD, "人物大小合适")
        }

        // 2. 头顶留白
        val headroom = box.top
        when {
            headroom > 0.22f -> out += PoseAdvice(AdviceLevel.TIP, "头顶留白偏多，手机稍微上抬或走近一点")
            headroom < 0.04f -> out += PoseAdvice(AdviceLevel.WARN, "头顶快贴到画面上沿了，把手机放低一些")
        }

        // 3. 水平位置 (三分线)
        when {
            box.centerX < 0.38f -> out += PoseAdvice(AdviceLevel.TIP, "人物偏左，向右移半步，让身体落到右侧三分线上")
            box.centerX > 0.62f -> out += PoseAdvice(AdviceLevel.TIP, "人物偏右，向左移半步，让身体落到左侧三分线上")
            else -> out += PoseAdvice(AdviceLevel.GOOD, "位置很好，身体正好压在三等分线上")
        }

        // 4. 双肩是否端平
        val ls = pose.point(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.point(PoseLandmark.RIGHT_SHOULDER)
        if (ls != null && rs != null) {
            val angle = Math.toDegrees(
                atan2((rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble())
            ).toFloat()
            if (abs(angle) > 9f) {
                out += PoseAdvice(AdviceLevel.TIP, "双肩有 %.0f° 倾斜，手机端平或让对方放松肩膀".format(abs(angle)))
            }
        }

        // 5. 正对镜头太"板正" → 建议转体
        val bodyHeight = torsoScale(pose, box)
        val shoulderSpan = if (ls != null && rs != null) abs(ls.x - rs.x) else null
        if (shoulderSpan != null && bodyHeight != null && bodyHeight > 0f) {
            val ratio = shoulderSpan / bodyHeight
            when {
                ratio > 0.30f -> out += PoseAdvice(
                    AdviceLevel.TIP, "身体完全正对镜头略显呆板，转体 30~45° 更显瘦、更有立体感",
                )
                ratio < 0.12f -> out += PoseAdvice(
                    AdviceLevel.TIP, "侧得有点过了，转回来一点让侧脸更完整",
                )
            }
        }

        // 6. 手臂是否僵直下垂
        val lw = pose.point(PoseLandmark.LEFT_WRIST)
        val rw = pose.point(PoseLandmark.RIGHT_WRIST)
        val lh = pose.point(PoseLandmark.LEFT_HIP)
        val rh = pose.point(PoseLandmark.RIGHT_HIP)
        if (lw != null && rw != null && lh != null && rh != null) {
            val bothDown = lw.y > lh.y && rw.y > rh.y
            val bothTucked = abs(lw.x - lh.x) < 0.06f && abs(rw.x - rh.x) < 0.06f
            if (bothDown && bothTucked) {
                out += PoseAdvice(AdviceLevel.TIP, "双手都自然下垂会有点僵，试试一只手叉腰、或抬手轻碰头发")
            }
        }

        // 7. 双腿并太紧
        val la = pose.point(PoseLandmark.LEFT_ANKLE)
        val ra = pose.point(PoseLandmark.RIGHT_ANKLE)
        if (la != null && ra != null && shoulderSpan != null && shoulderSpan > 0.01f) {
            val stance = abs(la.x - ra.x)
            if (stance / shoulderSpan < 0.6f) {
                out += PoseAdvice(AdviceLevel.TIP, "双腿并得太紧，一条腿往前迈一点或分开站，更有线条")
            }
        }

        // 8. 镜头高度
        val nose = pose.point(PoseLandmark.NOSE)
        if (nose != null) {
            when {
                nose.y > 0.55f -> out += PoseAdvice(AdviceLevel.TIP, "镜头偏低成了仰拍，把手机抬到对方眼睛的高度")
                nose.y < 0.22f -> out += PoseAdvice(AdviceLevel.TIP, "镜头偏高成了俯拍，手机放低一点更自然")
            }
        }

        // 9. 推荐最接近的参考姿势
        val best = rankTemplates(pose).firstOrNull()
        if (best != null && best.second < 0.8f) {
            out += PoseAdvice(
                AdviceLevel.TIP,
                "最接近「${best.first.name}」(匹配 ${(best.second * 100).toInt()}%)：${best.first.summary}",
            )
        }

        return out.sortedBy { it.level.ordinal }.take(5)
    }

    /** 躯干尺度近似: 用于消除远近带来的尺度差异 */
    private fun torsoScale(pose: DetectedPose, box: BoxF): Float? {
        val shoulderMid = pose.midpoint(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        val hipMid = pose.midpoint(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)
        if (shoulderMid != null && hipMid != null) {
            val torso = hypot(hipMid.x - shoulderMid.x, hipMid.y - shoulderMid.y)
            if (torso > 0.01f) {
                // 肩到髋约为身高的 0.28, 换算成近似身高
                return torso / 0.28f
            }
        }
        return box.height.takeIf { it > 0.01f }
    }

    // ------------------------------------------------------------ 模板匹配

    /**
     * 计算当前姿势与各模板的匹配度 (0~1, 越大越像), 按相似度降序返回。
     * 同时支持镜像匹配, 所以左右反向站位也能匹配上。
     */
    fun rankTemplates(pose: DetectedPose): List<Pair<PoseTemplate, Float>> {
        val box = personBox(pose) ?: return emptyList()
        return PoseTemplates.ALL
            .map { t ->
                val direct = similarity(pose, box, t)
                val mirrored = similarity(pose, box, PoseTemplates.mirror(t))
                if (mirrored > direct) t to mirrored else t to direct
            }
            .sortedByDescending { it.second }
    }

    private fun similarity(pose: DetectedPose, box: BoxF, template: PoseTemplate): Float {
        if (box.width <= 0.001f || box.height <= 0.001f) return 0f
        var sum = 0f
        var n = 0
        for ((type, target) in template.points) {
            val kp = pose.point(type) ?: continue
            val nx = (kp.x - box.left) / box.width
            val ny = (kp.y - box.top) / box.height
            sum += hypot((nx - target.x).toDouble(), (ny - target.y).toDouble()).toFloat()
            n++
        }
        // 关键点太少时不可信
        if (n < 5) return 0f
        val meanDist = sum / n
        return (1f - meanDist * 2f).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------ 叠加绘制

    /**
     * 把参考模板的骨架换算到归一化图像坐标, 便于叠加在人物身上。
     * 返回若干线段 (起点, 终点)。
     */
    fun templateSegments(pose: DetectedPose?, template: PoseTemplate): List<Pair<PointF, PointF>> {
        val box = pose?.let { personBox(it) } ?: DEFAULT_BOX
        val scaled = template.points.mapValues { (_, v) ->
            PointF(box.left + v.x * box.width, box.top + v.y * box.height)
        }
        return PoseTemplates.BONES.mapNotNull { (a, b) ->
            val pa = scaled[a]
            val pb = scaled[b]
            if (pa != null && pb != null) pa to pb else null
        }
    }

    /** 当前姿势的骨架线段 (归一化图像坐标) */
    fun poseSegments(pose: DetectedPose): List<Pair<PointF, PointF>> =
        PoseTemplates.BONES.mapNotNull { (a, b) ->
            val pa = pose.point(a)
            val pb = pose.point(b)
            if (pa != null && pb != null) {
                PointF(pa.x, pa.y) to PointF(pb.x, pb.y)
            } else null
        }
}