package com.aicompose.core.guide

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * MediaPipe Pose Landmarker 封装 (LIVE_STREAM 模式)。
 *
 * - 加载 assets/pose_landmarker_lite.task, 优先 GPU delegate, 失败自动回退 CPU
 * - [detectAsync] 由 CameraX 分析线程调用, 结果通过主线程 Handler 回调
 * - 每帧输出 33 个归一化关键点 (0~1, 相对输入图像)
 */
class PoseLandmarkerManager(
    context: Context,
    private val onResult: (PoseLandmarkerResult?) -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var landmarker: PoseLandmarker? = null

    init {
        landmarker = create(context, Delegate.GPU) ?: create(context, Delegate.CPU)
    }

    private fun create(context: Context, delegate: Delegate): PoseLandmarker? = try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ ->
                mainHandler.post { onResult(result) }
            }
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (t: Throwable) {
        null
    }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        try {
            landmarker?.detectAsync(image, timestampMs)
        } catch (_: Exception) {
            // LIVE_STREAM 内部异常单帧丢弃即可, 不中断预览
        }
    }

    fun close() {
        try {
            landmarker?.close()
        } catch (_: Exception) {
        }
        landmarker = null
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
