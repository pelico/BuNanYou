package com.aicompose.core.vision

import android.graphics.Bitmap

/**
 * 云端构图分析器 —— 占位实现, 预留接口。
 *
 * 后续接入 VLM (如 Venus / 多模态模型) 时, 只需在这里实现真正的网络请求,
 * 上层 UI 无需改动 (切换由 AnalyzerHolder 控制)。
 */
class CloudCompositionAnalyzer : CompositionAnalyzer {

    override suspend fun analyze(bitmap: Bitmap): CompositionResult {
        TODO("云端推理尚未实现: 在此处调用 VLM API, 返回裁剪框坐标")
    }
}
