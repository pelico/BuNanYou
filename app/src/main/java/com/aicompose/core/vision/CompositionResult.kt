package com.aicompose.core.vision

import android.graphics.RectF

/**
 * 构图分析结果 —— 相册重构与实时提示共用同一份数据结构。
 */
data class CompositionResult(
    /** 主体边界框 (可选, 实时提示用) */
    val subjectBox: RectF? = null,
    /** 建议裁剪框 (相册重构用), 基于原图坐标 */
    val bestCrop: RectF? = null,
    /** 地平线倾斜角, 单位度 (校正用, 可选) */
    val horizonAngle: Float? = null,
    /** 构图评分, 越高越好 */
    val score: Float = 0f,
    /** 推理耗时, 毫秒 */
    val inferenceMs: Long = 0L,
)
