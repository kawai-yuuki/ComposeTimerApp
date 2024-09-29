// TimerService.kt
package com.example.composetimerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TIME = "EXTRA_TIME"
        const val ACTION_TIMER_UPDATE = "TIMER_UPDATE" // 新規追加
        const val EXTRA_TIME_LEFT = "timeLeft" // 新規追加
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timeLeft: Long = 0L
    private var isRunning = false
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> {
                timeLeft = intent.getLongExtra(EXTRA_TIME, 60L)
                startForegroundService()
                startTimer()
            }
            ACTION_STOP -> {
                stopTimer()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Running")
            .setContentText("Time left: $timeLeft seconds")
            .setSmallIcon(R.mipmap.ic_launcher) // 既存のアイコンを使用
            .setOngoing(true) // ユーザーがサービスを停止できないようにする
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Running")
            .setContentText("Time left: $timeLeft seconds")
            .setSmallIcon(R.mipmap.ic_launcher) // 既存のアイコンを使用
            .setOngoing(true)
            .build()

        val notificationManager: NotificationManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(NotificationManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        notificationManager.notify(1, notification)
    }

    private fun startTimer() {
        if (!isRunning) {
            isRunning = true
            job = serviceScope.launch {
                while (timeLeft > 0 && isRunning) {
                    delay(1000L)
                    timeLeft -= 1
                    updateNotification()
                    sendTimerUpdateBroadcast()
                }
                if (timeLeft <= 0) {
                    // タイマー終了時の処理
                    stopForeground(true)
                    stopSelf()
                    sendTimerFinishedNotification()
                }
            }
        }
    }

    private fun stopTimer() {
        isRunning = false
        job?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun sendTimerUpdateBroadcast() {
        val intent = Intent(ACTION_TIMER_UPDATE).apply {
            putExtra(EXTRA_TIME_LEFT, timeLeft)
        }
        sendBroadcast(intent)
    }

    private fun sendTimerFinishedNotification() {
        val notificationManager: NotificationManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(NotificationManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Finished")
            .setContentText("Your timer has ended.")
            .setSmallIcon(R.mipmap.ic_launcher) // 既存のアイコンを使用
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }
}
