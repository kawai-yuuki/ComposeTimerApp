package com.example.composetimerapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.*

class ShakeDetector(
    private val onShake: () -> Unit,
    private val onShakeContinuous: () -> Unit
) : SensorEventListener {

    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0

    // シェイク検出のためのパラメータ
    private val SHAKE_THRESHOLD_GRAVITY = 2.7f
    private val SHAKE_SLOP_TIME_MS = 500
    private val SHAKE_COUNT_RESET_TIME_MS = 3000

    // 連続シェイク検出用
    private var continuousShakeStart: Long = 0
    private val CONTINUOUS_SHAKE_DURATION_MS = 10000 // 10秒
    private var isContinuousShaking = false
    private var continuousShakeJob: Job? = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // gForce は動きがないときに約1に近くなります。
            val gForce = kotlin.math.sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()
                if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    shakeCount = 0
                }

                shakeTimestamp = now
                shakeCount++

                onShake()
                Log.d("ShakeDetector", "Shake detected. Count: $shakeCount")

                if (!isContinuousShaking) {
                    isContinuousShaking = true
                    continuousShakeStart = now
                    startContinuousShakeTimer()
                } else {
                    // シェイクが継続している場合、タイマーをリセット
                    continuousShakeStart = now
                }
            } else {
                // シェイクが検出されていない場合、連続シェイクフラグをリセット
                if (isContinuousShaking) {
                    isContinuousShaking = false
                    continuousShakeJob?.cancel()
                    continuousShakeJob = null
                    Log.d("ShakeDetector", "Shaking stopped.")
                }
            }
        }
    }

    private fun startContinuousShakeTimer() {
        continuousShakeJob = CoroutineScope(Dispatchers.Main).launch {
            while (isContinuousShaking) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val elapsed = now - continuousShakeStart
                Log.d("ShakeDetector", "Continuous shaking elapsed: $elapsed ms")
                if (elapsed >= CONTINUOUS_SHAKE_DURATION_MS) {
                    onShakeContinuous()
                    isContinuousShaking = false
                    cancel()
                    Log.d("ShakeDetector", "Continuous shaking detected. Triggering onShakeContinuous.")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 使用しない
    }
}
