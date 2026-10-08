package com.aicompose.core.vision

import android.content.Context

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
}
