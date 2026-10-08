package com.aicompose.core.vision

import android.graphics.Bitmap

/**
 * 构图分析器接口。
 *
 * 相册重构与实时提示都依赖这个接口, 换实现 (本地 ONNX / 云端 VLM) 时 UI 不用改。
 */
interface CompositionAnalyzer {

    /**
     * 分析一张图片, 返回构图建议。
     * @param bitmap 输入图片 (建议不超过 2048px 以保证性能)
     */
    suspend fun analyze(bitmap: Bitmap): CompositionResult
}
