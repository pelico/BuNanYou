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
 * - 加载 assets/pose_landmarker_lite.task, 33 个归一化关键点
 * - 默认 GPU delegate; 部分机型 GPU 会"创建成功但推理静默失败",
 *   通过看门狗 (持续喂数据但 3 秒无结果) 或 detectAsync 异常自动降级 CPU
 * - [detectAsync] 由 CameraX 分析线程调用, 结果通过主线程 Handler 回调
 */
class PoseLandmarkerManager(
    context: Context,
    private val onResult: (PoseLandmarkerResult?) -> Unit,
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var landmarker: PoseLandmarker? = null
    private var useGpu = true

    private var lastFeedMs = 0L
    private var lastResultMs = 0L
    private var firstFeedMs = 0L

    init {
        synchronized(lock) {
            landmarker = create(Delegate.GPU)
            if (landmarker == null) {
                useGpu = false
                landmarker = create(Delegate.CPU)
            }
        }
    }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        synchronized(lock) {
            if (landmarker == null) {
                // init 阶段双双失败时懒重试 CPU
                useGpu = false
                landmarker = create(Delegate.CPU)
            }
            val current = landmarker ?: return
            if (firstFeedMs == 0L) firstFeedMs = timestampMs
            lastFeedMs = timestampMs
            try {
                current.detectAsync(image, timestampMs)
            } catch (_: Exception) {
                fallbackToCpu(timestampMs, "推理异常")
                return
            }
            if (useGpu) {
                // 看门狗: 从首帧 (或上次结果) 起持续喂数据 3s 仍无结果 → 判定 GPU 失效
                val anchor = if (lastResultMs == 0L) firstFeedMs else lastResultMs
                if (anchor > 0 && timestampMs - anchor > WATCHDOG_MS) {
                    fallbackToCpu(timestampMs, "持续无结果")
                }
            }
        }
    }

    private fun fallbackToCpu(nowMs: Long, reason: String) {
        if (!useGpu) return
        useGpu = false
        val old = landmarker
        landmarker = create(Delegate.CPU)
        // 旧实例可能有在途回调, 延迟关闭避免 native crash
        if (old != null) {
            mainHandler.postDelayed({ try { old.close() } catch (_: Exception) {} }, 2000)
        }
        lastFeedMs = nowMs
        lastResultMs = 0L
        firstFeedMs = nowMs
    }

    private fun create(delegate: Delegate): PoseLandmarker? = try {
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
                lastResultMs = System.currentTimeMillis()
                mainHandler.post { onResult(result) }
            }
            .build()
        PoseLandmarker.createFromOptions(appContext, options)
    } catch (t: Throwable) {
        null
    }

    fun close() {
        synchronized(lock) {
            try {
                landmarker?.close()
            } catch (_: Exception) {
            }
            landmarker = null
        }
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
        const val WATCHDOG_MS = 3000L
    }
}
