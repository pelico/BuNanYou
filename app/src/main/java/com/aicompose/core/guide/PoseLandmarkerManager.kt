package com.aicompose.core.guide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * - GPU delegate 在部分机型会"创建成功但推理静默失败": 看门狗 (持续喂数据
 *   3s 无结果) 或 detectAsync 异常时自动降级 CPU
 * - 降级后旧实例的在途回调通过 [generation] 门卫丢弃, 不允许空结果
 *   回灌把已检测到的人体打掉
 *
 * 使用约定: 调用方必须传入**帧级独立的位图副本** (不可复用), 因为
 * detectAsync 是异步的, 复用位图会产生撕裂帧导致检测率归零。
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

    /** 实例代号: 每次重建自增, 旧实例回调因代号不符被丢弃 */
    @Volatile
    private var generation = 0

    private var lastResultMs = 0L
    private var firstFeedMs = 0L

    init {
        synchronized(lock) {
            landmarker = create(Delegate.GPU, generation)
            if (landmarker == null) {
                useGpu = false
                landmarker = create(Delegate.CPU, generation)
            }
        }
    }

    fun detectAsync(image: MPImage, timestampMs: Long) {
        synchronized(lock) {
            if (landmarker == null) {
                // init 阶段双双失败时懒重试 CPU
                useGpu = false
                landmarker = create(Delegate.CPU, generation)
            }
            val current = landmarker ?: return
            if (firstFeedMs == 0L) firstFeedMs = timestampMs
            try {
                current.detectAsync(image, timestampMs)
            } catch (_: Exception) {
                fallbackToCpu(timestampMs)
                return
            }
            if (useGpu) {
                // 看门狗: 从首帧 (或上次结果) 起持续喂数据 3s 仍无结果 → 判定 GPU 失效
                val anchor = if (lastResultMs == 0L) firstFeedMs else lastResultMs
                if (anchor > 0 && timestampMs - anchor > WATCHDOG_MS) {
                    fallbackToCpu(timestampMs)
                }
            }
        }
    }

    private fun fallbackToCpu(nowMs: Long) {
        if (!useGpu) return
        useGpu = false
        val old = landmarker
        generation++                       // 先换代, 旧实例回调全部作废
        landmarker = create(Delegate.CPU, generation)
        // 旧实例可能有在途回调, 延迟关闭避免 native crash
        if (old != null) {
            mainHandler.postDelayed({ try { old.close() } catch (_: Exception) {} }, 2000)
        }
        lastResultMs = 0L
        firstFeedMs = nowMs
    }

    private fun create(delegate: Delegate, gen: Int): PoseLandmarker? = try {
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
                if (gen != generation) return@setResultListener   // 过期实例, 丢弃
                // 与 detectAsync 的 timestampMs 同基准 (elapsedRealtime), 看门狗才能对齐
                lastResultMs = SystemClock.elapsedRealtime()
                mainHandler.post { onResult(result) }
            }
            .build()
        PoseLandmarker.createFromOptions(appContext, options)
    } catch (t: Throwable) {
        null
    }

    fun close() {
        synchronized(lock) {
            generation++
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
