package com.aicompose.core.vision

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分析器单例持有者 —— 统一管理本地 / 云端实现的切换。
 *
 * 默认使用本地 GAIC ONNX 推理 (离线可用); 后续可通过 setCloud(true)
 * 切换到云端 VLM 实现。
 */
object AnalyzerHolder {

    @Volatile
    private var local: GaicOnnxAnalyzer? = null

    private val cloud by lazy { CloudCompositionAnalyzer() }

    @Volatile
    var useCloud: Boolean = false

    fun get(context: Context): CompositionAnalyzer {
        if (useCloud) return cloud
        return local ?: synchronized(this) {
            local ?: GaicOnnxAnalyzer(context.applicationContext).also { local = it }
        }
    }

    /**
     * 便捷入口: 在后台线程构建分析器并分析。
     *
     * 分析器首次构建要读取 12MB 模型并初始化 ONNX 会话, 必须放在后台线程,
     * 否则会阻塞主线程甚至触发 ANR。
     */
    suspend fun analyze(context: Context, bitmap: Bitmap): CompositionResult {
        val analyzer = withContext(Dispatchers.Default) { get(context) }
        return analyzer.analyze(bitmap)
    }
}
