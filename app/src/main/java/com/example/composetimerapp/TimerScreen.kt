package com.example.composetimerapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composetimerapp.ui.theme.ComposeTimerAppTheme

/**
 * 時間を分:秒形式にフォーマットする関数
 */
@SuppressLint("DefaultLocale")
fun formatTime(seconds: Long): String {
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return String.format("%02d:%02d", minutesPart, secondsPart)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimerScreen() {
    // コンテキストの取得
    val context = LocalContext.current

    // ブロードキャストで受信したタイマーの残り時間
    var timeLeft by remember { mutableStateOf(60L) } // 初期値: 60秒

    // タイマーの実行状態
    var isRunning by remember { mutableStateOf(false) }

    // スライダーによる時間設定用の状態
    var selectedMinutes by remember { mutableStateOf(1f) } // 初期値: 1分
    var selectedSeconds by remember { mutableStateOf(0f) } // 初期値: 0秒

    // 運動セッションの時間設定（秒単位）
    var exerciseDurationSeconds by remember { mutableStateOf(60L) } // 初期値: 60秒

    // Vibrator の取得
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    // SensorManager の取得
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // バイブレーション実行中かどうかのフラグ
    var isVibrating by remember { mutableStateOf(false) }

    // シェイク状態を追跡するための状態変数
    var isShaking by remember { mutableStateOf(false) }
    var continuousShakingTime by remember { mutableStateOf(0L) } // 連続シェイク時間（ミリ秒）

    // シェイクの最後の検出時間を追跡
    var lastShakeTime by remember { mutableStateOf(0L) }

    // 運動セッションの状態管理
    var isVibrationSessionActive by remember { mutableStateOf(false) }
    var vibrationSessionTimeLeft by remember { mutableStateOf(60L) } // 初期値: 60秒

    // ShakeDetector のインスタンス作成
    val shakeDetector = remember {
        ShakeDetector(
            onShake = {
                // シェイクが検出された場合の処理
                lastShakeTime = System.currentTimeMillis()
                if (isVibrationSessionActive) {
                    isShaking = true
                }
            }
        )
    }

    // センサーリスナーの登録と解除
    DisposableEffect(Unit) {
        val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(shakeDetector)
        }
    }

    // ブロードキャストレシーバーの定義
    val timerUpdateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "TIMER_UPDATE") {
                    val updatedTime = intent.getLongExtra("timeLeft", 0L)
                    timeLeft = updatedTime
                    if (updatedTime <= 0 && isRunning) {
                        isRunning = false
                        Toast.makeText(context, "タイマーが終了しました！", Toast.LENGTH_SHORT).show()
                        // 運動セッションを開始
                        isVibrationSessionActive = true
                        vibrationSessionTimeLeft = exerciseDurationSeconds // 運動セッション時間

                        // バイブレーションの実行（運動セッション開始時）
                        if (vibrator?.hasVibrator() == true) {
                            try {
                                val pattern = longArrayOf(0, 500, 200, 500)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createWaveform(
                                            pattern,
                                            0
                                        )
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(pattern, 0)
                                }
                                isVibrating = true
                                Toast.makeText(context, "バイブレーションを開始しました！", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "デバイスはバイブレーションをサポートしていません。", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // ブロードキャストの登録と解除
    DisposableEffect(Unit) {
        val filter = IntentFilter("TIMER_UPDATE")
        context.registerReceiver(timerUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(timerUpdateReceiver)
        }
    }

    // サービスを開始する関数
    fun startTimerService(totalSeconds: Long) {
        val startIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_TIME, totalSeconds)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }
    }

    // サービスを停止する関数
    fun stopTimerService() {
        val stopIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    // シェイク状態と連続シェイク時間の管理
    LaunchedEffect(lastShakeTime, isShaking, isVibrationSessionActive) {
        while (isVibrationSessionActive) {
            kotlinx.coroutines.delay(100L) // 0.1秒ごとにチェック
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShakeTime <= 500L) { // シェイクが最近検出された
                if (!isShaking) {
                    isShaking = true
                    continuousShakingTime = 0L
                    // バイブレーションを停止
                    vibrator?.cancel()
                    isVibrating = false
                    Toast.makeText(context, "バイブレーションが停止しました！", Toast.LENGTH_SHORT).show()
                }
                continuousShakingTime += 100L
                if (continuousShakingTime >= 3000L) { // 3秒以上シェイク
                    // バイブレーションを再開
                    if (vibrator?.hasVibrator() == true) {
                        try {
                            val pattern = longArrayOf(0, 500, 200, 500)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(
                                    VibrationEffect.createWaveform(
                                        pattern,
                                        0
                                    )
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(pattern, 0)
                            }
                            isVibrating = true
                            Toast.makeText(context, "バイブレーションを再開しました！", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isShaking = false
                    continuousShakingTime = 0L
                }
            } else {
                if (isShaking) {
                    isShaking = false
                    continuousShakingTime = 0L
                    // バイブレーションを再開
                    if (vibrator?.hasVibrator() == true && !isVibrating) {
                        try {
                            val pattern = longArrayOf(0, 500, 200, 500)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(
                                    VibrationEffect.createWaveform(
                                        pattern,
                                        0
                                    )
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(pattern, 0)
                            }
                            isVibrating = true
                            Toast.makeText(context, "バイブレーションを再開しました！", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // 運動セッションのタイマー管理
    LaunchedEffect(isVibrationSessionActive, vibrationSessionTimeLeft) {
        if (isVibrationSessionActive) {
            while (vibrationSessionTimeLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                vibrationSessionTimeLeft -= 1
            }
            // 運動セッション終了
            isVibrationSessionActive = false
            // バイブレーションを停止
            vibrator?.cancel()
            isVibrating = false
            isShaking = false
            continuousShakingTime = 0L
            Toast.makeText(context, "運動セッションが終了しました！", Toast.LENGTH_SHORT).show()
        }
    }

    // UIの構築
    ComposeTimerAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // メインタイマーの分の選択
                Text(
                    text = "Minutes: ${selectedMinutes.toInt()}",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Slider(
                    value = selectedMinutes,
                    onValueChange = { selectedMinutes = it },
                    valueRange = 0f..60f,
                    steps = 59, // 0から60までの整数を選択可能にする
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // メインタイマーの秒の選択
                Text(
                    text = "Seconds: ${selectedSeconds.toInt()}",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Slider(
                    value = selectedSeconds,
                    onValueChange = { selectedSeconds = it },
                    valueRange = 0f..59f,
                    steps = 58, // 0から59までの整数を選択可能にする
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 運動セッションの時間設定（秒単位）の選択
                Text(
                    text = "Exercise Duration: ${exerciseDurationSeconds}s",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Slider(
                    value = exerciseDurationSeconds.toFloat(),
                    onValueChange = { exerciseDurationSeconds = it.toLong() },
                    valueRange = 10f..120f, // 10秒から120秒まで
                    steps = 110, // 10秒から120秒までの1秒刻み
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // タイマー表示
                Text(
                    text = formatTime(timeLeft),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // シェイク状態の表示
                Text(
                    text = if (isShaking) "Shaking" else "No Shaking",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isShaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 連続シェイク時間の表示（運動セッション中のみ）
                if (isVibrationSessionActive) {
                    Text(
                        text = "Shaking Time: ${continuousShakingTime / 1000}.${(continuousShakingTime % 1000) / 100}s",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }

                // ボタンコンテナ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // スタート/ポーズボタン
                    Button(
                        onClick = {
                            if (!isRunning && !isVibrationSessionActive) {
                                // スライダーで選択された時間をタイマーに設定
                                val totalSeconds = (selectedMinutes.toInt() * 60 + selectedSeconds.toInt()).toLong()
                                if (totalSeconds > 0) {
                                    timeLeft = totalSeconds
                                    isRunning = true
                                    startTimerService(totalSeconds)
                                }
                            } else if (isRunning) {
                                isRunning = false
                                // サービスを停止
                                stopTimerService()
                                // ポーズ時にバイブレーションをキャンセル
                                vibrator?.cancel()
                                isVibrating = false
                            }
                        },
                        modifier = Modifier.width(100.dp)
                    ) {
                        Text(if (isRunning) "Pause" else "Start")
                    }

                    // リセットボタン
                    Button(
                        onClick = {
                            isRunning = false
                            timeLeft = 60L // 初期値にリセット
                            selectedMinutes = 1f // 初期値にリセット（例: 1分）
                            selectedSeconds = 0f // 初期値にリセット
                            // 運動セッションの時間もリセット
                            exerciseDurationSeconds = 60L // 初期値にリセット
                            // バイブレーションをキャンセル
                            vibrator?.cancel()
                            isVibrating = false
                            // シェイク状態もリセット
                            isShaking = false
                            continuousShakingTime = 0L
                            lastShakeTime = 0L
                            // 運動セッションもリセット
                            isVibrationSessionActive = false
                            vibrationSessionTimeLeft = exerciseDurationSeconds
                            // サービスを停止
                            stopTimerService()
                        },
                        modifier = Modifier.width(100.dp)
                    ) {
                        Text("Reset")
                    }

                    // ストップボタンの追加
                    Button(
                        onClick = {
                            isRunning = false
                            timeLeft = 60L // 初期値にリセット
                            selectedMinutes = 1f // 初期値にリセット（例: 1分）
                            selectedSeconds = 0f // 初期値にリセット
                            // 運動セッションの時間もリセット
                            exerciseDurationSeconds = 60L // 初期値にリセット
                            // バイブレーションをキャンセル
                            vibrator?.cancel()
                            isVibrating = false
                            // シェイク状態もリセット
                            isShaking = false
                            continuousShakingTime = 0L
                            lastShakeTime = 0L
                            // 運動セッションもリセット
                            isVibrationSessionActive = false
                            vibrationSessionTimeLeft = exerciseDurationSeconds
                            // サービスを停止
                            stopTimerService()
                            Toast.makeText(context, "バイブレーションを停止しました！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.width(100.dp)
                    ) {
                        Text("Stop")
                    }
                }

                // テスト用バイブレーションボタンの追加
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (vibrator?.hasVibrator() == true) {
                            try {
                                // バイブレーションパターンの定義
                                val pattern = longArrayOf(0, 500, 200, 500) // 0ms待機、500ms振動、200ms待機、500ms振動
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createWaveform(
                                            pattern,
                                            0 // 繰り返し開始インデックス（0で無限に繰り返す）
                                        )
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(pattern, 0) // 0で無限ループ
                                }
                                isVibrating = true
                                Toast.makeText(context, "バイブレーションを実行しました！", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "デバイスはバイブレーションをサポートしていません。", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text("Test Vibration")
                }
            }
        }
    }
}
