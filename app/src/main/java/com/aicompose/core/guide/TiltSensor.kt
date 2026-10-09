package com.aicompose.core.guide

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/** 物理倾角 (度) */
data class Tilt(val rollDeg: Float = 0f, val pitchDeg: Float = 0f) {
    /** |roll| < 1.5° 视为地平线平稳 */
    val level: Boolean get() = abs(rollDeg) < 1.5f
}

/**
 * 物理水平仪 —— 监听 TYPE_ROTATION_VECTOR, 输出平滑后的
 * Roll (左右侧倾) 与 Pitch (前后俯仰)。
 *
 * 回调在主线程执行 (registerListener 默认使用主线程 Handler), 可直接写 Compose 状态。
 */
class TiltSensor(context: Context, private val onTilt: (Tilt) -> Unit) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotMat = FloatArray(9)
    private val remap = FloatArray(9)
    private val orient = FloatArray(3)

    private var roll = 0f
    private var pitch = 0f

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotMat, event.values)
        // 竖屏持机: 把设备坐标系重映射到显示坐标系, roll 才对应"画面左右倾斜"
        SensorManager.remapCoordinateSystem(
            rotMat, SensorManager.AXIS_X, SensorManager.AXIS_Z, remap,
        )
        SensorManager.getOrientation(remap, orient)
        val rawRoll = Math.toDegrees(orient[2].toDouble()).toFloat()
        val rawPitch = Math.toDegrees(orient[1].toDouble()).toFloat()
        // 指数平滑, 避免指针抖动
        val a = 0.2f
        roll += (rawRoll - roll) * a
        pitch += (rawPitch - pitch) * a
        onTilt(Tilt(rollDeg = roll, pitchDeg = pitch))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
