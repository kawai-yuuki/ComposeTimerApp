package com.example.composetimerapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {
    //最後にシェイクが発生したタイムスタンプを保存する変数
    private var shakeTimestamp: Long = 0
    //シェイク回数を保存する変数
    // シェイク検出に使用する重力の閾値(1.0G以上でシェイクと判断)
    private val SHAKE_THRESHOLD_GRAVITY = 1f
    //シェイク間の最小間隔(500ミリ秒以内のシェイクは無視する)
    private val SHAKE_SLOP_TIME_MS = 500

    // センサーの値が変化したときに呼ばれるメソッド
    override fun onSensorChanged(event: SensorEvent?) {
        //　イベントがnullでない場合に処理を続行
        event?.let {
            // センサーのX, Y, Z軸の値を取得
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            //X, Y, Z軸の値を地球の重力で割ることで、重力に対する相対値を計算
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // X, Y, Z軸の重力の大きさをベクトルとして計算(静止時は約1になる)
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            // 計算された重力が閾値を超えた場合にシェイクを検出
            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                // 現在の時間を取得
                val now = System.currentTimeMillis()

                //前回のシェイクが500ms以内であれば、重複を避けるため無視
                if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                //現在の時間を最後のシェイク時間として保存
                shakeTimestamp = now
                //シェイクが発生した際のコールバック関数を呼び出し
                onShake()
            }
        }
    }

    //センサーの精度が変更されたときに呼ばれるメソッド(今回は使用しない)
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 処理は行わない
    }
}
