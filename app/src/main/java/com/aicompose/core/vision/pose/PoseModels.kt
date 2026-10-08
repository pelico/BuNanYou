package com.aicompose.core.vision.pose

import android.graphics.PointF

/**
 * 单个人体关键点。
 *
 * 坐标为归一化图像坐标 [0,1] —— 已按 Exif/相机旋转角纠正, 左上角为 (0,0)。
 */
data class Keypoint(
    val type: Int,
    val x: Float,
    val y: Float,
    /** 该点落在画面内的置信度, 越高越可靠 */
    val inFrame: Float,
)

/** 一次姿态检测的结果。 */
data class DetectedPose(val keypoints: List<Keypoint>) {

    private val byType: Map<Int, Keypoint> = keypoints.associateBy { it.type }

    fun point(type: Int, minConfidence: Float = 0.3f): Keypoint? =
        byType[type]?.takeIf { it.inFrame >= minConfidence }

    /** 置信度足够高的关键点, 用于绘制与匹配 */
    val visible: List<Keypoint> get() = keypoints.filter { it.inFrame >= 0.3f }

    /** 双肩/双髋等成对关键点的中点, 两侧都可见时才有值 */
    fun midpoint(a: Int, b: Int, minConfidence: Float = 0.3f): Keypoint? {
        val pa = point(a, minConfidence) ?: return null
        val pb = point(b, minConfidence) ?: return null
        return Keypoint(a, (pa.x + pb.x) / 2f, (pa.y + pb.y) / 2f, minOf(pa.inFrame, pb.inFrame))
    }
}

enum class AdviceLevel { GOOD, TIP, WARN }

data class PoseAdvice(val level: AdviceLevel, val text: String)

/**
 * 参考姿势模板。
 *
 * 坐标为「人物外接框」归一化: x 0 = 人物最左、1 = 最右; y 0 = 头顶、1 = 脚底。
 * 这样叠加到任意检测到的人物上都能自动缩放对齐。
 */
data class PoseTemplate(
    val id: String,
    val name: String,
    val summary: String,
    val points: Map<Int, PointF>,
)

/** 归一化的人体外接框。 */
data class BoxF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}