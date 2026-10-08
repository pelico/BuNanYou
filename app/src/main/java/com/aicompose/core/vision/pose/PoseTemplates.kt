package com.aicompose.core.vision.pose

import android.graphics.PointF
import com.google.mlkit.vision.pose.PoseLandmark

/**
 * 参考姿势库。
 *
 * 每个模板用一组「人物外接框归一化」坐标描述目标姿势 (见 [PoseTemplate]),
 * 拍摄时把模板骨架半透明叠加在人物身上, 被拍者照着摆即可。
 */
object PoseTemplates {

    /** 骨架连线, 用于绘制火柴人 */
    val BONES: List<Pair<Int, Int>> = listOf(
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.NOSE to PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.NOSE to PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
        PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
        PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
    )

    private fun p(x: Float, y: Float) = PointF(x, y)

    // 约定: 人物正对镜头时, 他/她的左手出现在画面右侧 (x 偏大)
    val ALL: List<PoseTemplate> = listOf(
        PoseTemplate(
            id = "stand",
            name = "经典站姿",
            summary = "直立、重心稳、双手自然放松，最不容易出错",
            points = mapOf(
                PoseLandmark.NOSE to p(0.50f, 0.06f),
                PoseLandmark.LEFT_SHOULDER to p(0.63f, 0.22f),
                PoseLandmark.RIGHT_SHOULDER to p(0.37f, 0.22f),
                PoseLandmark.LEFT_ELBOW to p(0.70f, 0.41f),
                PoseLandmark.RIGHT_ELBOW to p(0.30f, 0.41f),
                PoseLandmark.LEFT_WRIST to p(0.72f, 0.59f),
                PoseLandmark.RIGHT_WRIST to p(0.28f, 0.59f),
                PoseLandmark.LEFT_HIP to p(0.57f, 0.52f),
                PoseLandmark.RIGHT_HIP to p(0.43f, 0.52f),
                PoseLandmark.LEFT_KNEE to p(0.56f, 0.75f),
                PoseLandmark.RIGHT_KNEE to p(0.44f, 0.75f),
                PoseLandmark.LEFT_ANKLE to p(0.54f, 0.97f),
                PoseLandmark.RIGHT_ANKLE to p(0.46f, 0.97f),
            ),
        ),
        PoseTemplate(
            id = "hand_on_hip",
            name = "叉腰自信",
            summary = "一手叉腰、一腿微前伸，立刻有气势和线条",
            points = mapOf(
                PoseLandmark.NOSE to p(0.50f, 0.06f),
                PoseLandmark.LEFT_SHOULDER to p(0.64f, 0.22f),
                PoseLandmark.RIGHT_SHOULDER to p(0.36f, 0.22f),
                PoseLandmark.LEFT_ELBOW to p(0.75f, 0.39f),
                PoseLandmark.LEFT_WRIST to p(0.62f, 0.51f),
                PoseLandmark.RIGHT_ELBOW to p(0.28f, 0.42f),
                PoseLandmark.RIGHT_WRIST to p(0.30f, 0.60f),
                PoseLandmark.LEFT_HIP to p(0.57f, 0.52f),
                PoseLandmark.RIGHT_HIP to p(0.43f, 0.52f),
                PoseLandmark.LEFT_KNEE to p(0.58f, 0.75f),
                PoseLandmark.RIGHT_KNEE to p(0.42f, 0.75f),
                PoseLandmark.LEFT_ANKLE to p(0.60f, 0.97f),
                PoseLandmark.RIGHT_ANKLE to p(0.40f, 0.97f),
            ),
        ),
        PoseTemplate(
            id = "side_45",
            name = "侧身 45°",
            summary = "身体转开 45°、双肩错开，显瘦又有立体感",
            points = mapOf(
                PoseLandmark.NOSE to p(0.57f, 0.06f),
                PoseLandmark.LEFT_SHOULDER to p(0.62f, 0.22f),
                PoseLandmark.RIGHT_SHOULDER to p(0.45f, 0.23f),
                PoseLandmark.LEFT_ELBOW to p(0.66f, 0.41f),
                PoseLandmark.RIGHT_ELBOW to p(0.42f, 0.43f),
                PoseLandmark.LEFT_WRIST to p(0.68f, 0.60f),
                PoseLandmark.RIGHT_WRIST to p(0.40f, 0.62f),
                PoseLandmark.LEFT_HIP to p(0.60f, 0.52f),
                PoseLandmark.RIGHT_HIP to p(0.45f, 0.53f),
                PoseLandmark.LEFT_KNEE to p(0.60f, 0.75f),
                PoseLandmark.RIGHT_KNEE to p(0.47f, 0.75f),
                PoseLandmark.LEFT_ANKLE to p(0.58f, 0.97f),
                PoseLandmark.RIGHT_ANKLE to p(0.49f, 0.97f),
            ),
        ),
        PoseTemplate(
            id = "walking",
            name = "走动感",
            summary = "迈步 + 手臂摆动，适合街拍，画面立刻有动势",
            points = mapOf(
                PoseLandmark.NOSE to p(0.50f, 0.06f),
                PoseLandmark.LEFT_SHOULDER to p(0.63f, 0.22f),
                PoseLandmark.RIGHT_SHOULDER to p(0.37f, 0.22f),
                PoseLandmark.LEFT_ELBOW to p(0.70f, 0.35f),
                PoseLandmark.LEFT_WRIST to p(0.72f, 0.24f),
                PoseLandmark.RIGHT_ELBOW to p(0.32f, 0.40f),
                PoseLandmark.RIGHT_WRIST to p(0.29f, 0.57f),
                PoseLandmark.LEFT_HIP to p(0.57f, 0.52f),
                PoseLandmark.RIGHT_HIP to p(0.43f, 0.52f),
                PoseLandmark.LEFT_KNEE to p(0.64f, 0.73f),
                PoseLandmark.RIGHT_KNEE to p(0.38f, 0.76f),
                PoseLandmark.LEFT_ANKLE to p(0.68f, 0.94f),
                PoseLandmark.RIGHT_ANKLE to p(0.33f, 0.98f),
            ),
        ),
        PoseTemplate(
            id = "look_back",
            name = "回头侧脸",
            summary = "上半身侧转、一手轻抬靠近头部，最有氛围感",
            points = mapOf(
                PoseLandmark.NOSE to p(0.58f, 0.07f),
                PoseLandmark.LEFT_SHOULDER to p(0.61f, 0.22f),
                PoseLandmark.RIGHT_SHOULDER to p(0.45f, 0.24f),
                PoseLandmark.LEFT_ELBOW to p(0.72f, 0.33f),
                PoseLandmark.LEFT_WRIST to p(0.62f, 0.15f),
                PoseLandmark.RIGHT_ELBOW to p(0.40f, 0.46f),
                PoseLandmark.RIGHT_WRIST to p(0.38f, 0.62f),
                PoseLandmark.LEFT_HIP to p(0.58f, 0.53f),
                PoseLandmark.RIGHT_HIP to p(0.45f, 0.54f),
                PoseLandmark.LEFT_KNEE to p(0.58f, 0.75f),
                PoseLandmark.RIGHT_KNEE to p(0.47f, 0.76f),
                PoseLandmark.LEFT_ANKLE to p(0.57f, 0.97f),
                PoseLandmark.RIGHT_ANKLE to p(0.49f, 0.97f),
            ),
        ),
        PoseTemplate(
            id = "sitting",
            name = "坐姿放松",
            summary = "坐着、双手自然放大腿上，适合咖啡厅等场景",
            points = mapOf(
                PoseLandmark.NOSE to p(0.50f, 0.10f),
                PoseLandmark.LEFT_SHOULDER to p(0.62f, 0.26f),
                PoseLandmark.RIGHT_SHOULDER to p(0.38f, 0.26f),
                PoseLandmark.LEFT_ELBOW to p(0.68f, 0.44f),
                PoseLandmark.LEFT_WRIST to p(0.60f, 0.58f),
                PoseLandmark.RIGHT_ELBOW to p(0.32f, 0.44f),
                PoseLandmark.RIGHT_WRIST to p(0.40f, 0.58f),
                PoseLandmark.LEFT_HIP to p(0.58f, 0.62f),
                PoseLandmark.RIGHT_HIP to p(0.42f, 0.62f),
                PoseLandmark.LEFT_KNEE to p(0.64f, 0.84f),
                PoseLandmark.RIGHT_KNEE to p(0.38f, 0.84f),
                PoseLandmark.LEFT_ANKLE to p(0.62f, 0.98f),
                PoseLandmark.RIGHT_ANKLE to p(0.41f, 0.98f),
            ),
        ),
    )

    fun byId(id: String?): PoseTemplate? = ALL.firstOrNull { it.id == id }

    /** 左右镜像 —— 被拍者与模板反向站位时用它匹配 */
    fun mirror(t: PoseTemplate): PoseTemplate = t.copy(
        points = t.points.mapValues { (_, v) -> PointF(1f - v.x, v.y) },
    )
}