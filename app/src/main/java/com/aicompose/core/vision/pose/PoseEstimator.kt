package com.aicompose.core.vision.pose

import android.graphics.Bitmap
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit 姿态检测的薄封装 —— 完全端侧运行, 不需要联网也不需要 API Key。
 *
 * 输出统一换算成归一化图像坐标 ([DetectedPose]), 与分辨率解耦。
 */
class PoseEstimator(mode: Int = PoseDetectorOptions.SINGLE_IMAGE_MODE) {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(mode)
            .build()
    )

    /**
     * 实时取景用: 直接吃 [ImageProxy] 的 MediaImage, 省掉一次 Bitmap 转换。
     * [onComplete] 在 ML Kit 处理结束后回调, 此时才可以安全关闭 MediaImage。
     */
    fun detectAsync(
        mediaImage: Image,
        rotationDegrees: Int,
        onResult: (DetectedPose?) -> Unit,
        onComplete: () -> Unit,
    ) {
        val input = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { pose -> onResult(pose.toDetected(input.width, input.height)) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { onComplete() }
    }

    /** 照片分析用 */
    suspend fun detect(bitmap: Bitmap): DetectedPose? = suspendCancellableCoroutine { cont ->
        val input = InputImage.fromBitmap(bitmap, 0)
        detector.process(input)
            .addOnSuccessListener { pose -> cont.resume(pose.toDetected(input.width, input.height)) }
            .addOnFailureListener { cont.resume(null) }
    }

    fun close() {
        detector.close()
    }

    private fun Pose.toDetected(width: Int, height: Int): DetectedPose? {
        if (width <= 0 || height <= 0) return null
        val w = width.toFloat()
        val h = height.toFloat()
        val points = allPoseLandmarks.map { lm ->
            Keypoint(
                type = lm.landmarkType,
                x = lm.position.x / w,
                y = lm.position.y / h,
                inFrame = lm.inFrameLikelihood,
            )
        }
        return DetectedPose(points)
    }
}