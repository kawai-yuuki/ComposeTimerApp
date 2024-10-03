package com.example.composetimerapp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.navigation.compose.*
import com.example.composetimerapp.ui.theme.ComposeTimerAppTheme
import kotlinx.coroutines.delay

// 時間を分:秒形式にフォーマットする関数（トップレベルに定義）
@SuppressLint("DefaultLocale")
fun formatTime(seconds: Long): String {
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return String.format("%02d:%02d", minutesPart, secondsPart)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeTimerAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// ForegroundService クラスの実装
class TimerForegroundService : Service() {
    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)

        // バイブレーションを開始
        startVibration()

        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "TIMER_CHANNEL_ID"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Timer Notification",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Timer Running")
            .setContentText("Your timer is running.")
            .setSmallIcon(R.drawable.ic_timer)
            .build()
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 200, 500) // 0ms待機、500ms振動、200ms待機、500ms振動
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) // 1回だけ
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

@Composable
fun AppNavigation() {
    // timeLeftとisRunningの状態をAppNavigationで管理
    var timeLeft by remember { mutableLongStateOf(60L) }
    var isRunning by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    NavHost(navController, startDestination = "start") {
        // Start page
        composable("start") {
            StartScreen(onStartTransition = { navController.navigate("timer") })
        }
        // Time setting page
        composable("timer") {
            TimerScreen(
                timeLeft = timeLeft,
                isRunning = isRunning,
                onTimeSet = { newTime -> timeLeft = newTime },
                onStartClick = {
                    isRunning = true
                    navController.navigate("wait")
                },
                onPauseClick = {
                    isRunning = false
                }
            )
        }
        // waiting call page
        composable("wait") {
            WaitScreen(
                timeLeft = timeLeft,
                isRunning = isRunning,
                onStartPauseClick = {
                    currentIsRunning -> isRunning = !currentIsRunning // タイマーの開始と停止
                },
                onResetClick = {
                    isRunning = false
                    timeLeft = 60L // タイマーをリセット
                    navController.navigate("timer")
                }
            )
        }
    }
}

@Composable
fun StartScreen(onStartTransition: () -> Unit) {
    // 自動遷移を実装
    LaunchedEffect(Unit) {
        delay(2000L) // 2秒待機
        onStartTransition()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Timer App",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun TimerScreen(
    timeLeft: Long,
    isRunning: Boolean,
    onTimeSet: (Long) -> Unit,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit
) {
    // コンテキストの取得
    val context = LocalContext.current
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    // スクロールによる時間設定用の状態
    var selectedMinutes by remember { mutableFloatStateOf(1f) } // 初期値: 1分
    var selectedSeconds by remember { mutableFloatStateOf(0f) } // 初期値: 0秒

    // タイマーの実行
    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            //onStartClick()
            delay(1000L)
            onTimeSet(timeLeft - 1)
        }
        if (timeLeft <= 0 && isRunning) {
            // タイマー終了の通知
            Toast.makeText(context, "タイマーが終了しました！", Toast.LENGTH_SHORT).show()

            // バイブレーションの実行
            if (vibrator.hasVibrator()) {
                try {
                    // バイブレーションパターンの定義
                    val pattern = longArrayOf(0, 500, 200, 500) // 0ms待機、500ms振動、200ms待機、500ms振動
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(
                                pattern,
                                -1 // 繰り返しなし
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    Log.d("TimerScreen", "バイブレーションを実行しました。")
                } catch (e: Exception) {
                    Log.e("TimerScreen", "バイブレーションに失敗しました: ${e.message}")
                    Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("TimerScreen", "デバイスはバイブレーションをサポートしていません。")
                Toast.makeText(context, "デバイスはバイブレーションをサポートしていません。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UIの構築
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 分の選択
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

        // 秒の選択
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

        Spacer(modifier = Modifier.height(32.dp))

        // タイマー表示
        Text(
            text = formatTime(timeLeft),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // ボタンコンテナ
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // スタート/ポーズボタン
            Button(
                onClick = {
                    if (!isRunning) {
                        // スクロールで選択された時間をタイマーに設定
                        val totalSeconds = (selectedMinutes.toInt()*60+selectedSeconds.toInt()).toLong()
                        val serviceIntent = Intent(context, TimerForegroundService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        if (totalSeconds > 0) {
                            onTimeSet(totalSeconds)
                            onStartClick()
                        }
                    } else {
                        onPauseClick()
                    }
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text(if (isRunning) "Pause" else "Start")
            }

            // リセットボタン
            Button(
                onClick = {
                    onTimeSet(60L) // 初期値にリセット
                    selectedMinutes = 1f // 初期値にリセット（例: 1分）
                    selectedSeconds = 0f // 初期値にリセット
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text("Reset")
            }
        }

        // テスト用バイブレーションボタンの追加
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (vibrator.hasVibrator()) {
                    try {
                        // バイブレーションパターンの定義
                        val pattern = longArrayOf(0, 500, 200, 500) // 0ms待機、500ms振動、200ms待機、500ms振動
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(
                                VibrationEffect.createWaveform(
                                    pattern,
                                    -1 // 繰り返しなし
                                )
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(pattern, -1)
                        }
                        Toast.makeText(context, "バイブレーションを実行しました！", Toast.LENGTH_SHORT).show()
                        Log.d("TimerScreen", "テストバイブレーションを実行しました。")
                    } catch (e: Exception) {
                        Log.e("TimerScreen", "テストバイブレーションに失敗しました: ${e.message}")
                        Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("TimerScreen", "デバイスはバイブレーションをサポートしていません。")
                    Toast.makeText(context, "デバイスはバイブレーションをサポートしていません。", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.width(200.dp)
        ) {
            Text("Test Vibration")
        }
    }
}

@Composable
fun WaitScreen(
    timeLeft: Long,
    isRunning: Boolean,
    onStartPauseClick: (Boolean) -> Unit, // 停止ボタン用のコールバックを追加
    onResetClick: () -> Unit
) {
    // コンテキストの取得
    val context = LocalContext.current
    var time by remember { mutableLongStateOf(timeLeft) } // timeLeftでコピーして使用
    var isCalling by remember { mutableStateOf(false) } // タイマー終了状態を管理

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(VIBRATOR_SERVICE) as Vibrator?
        }
    }

    // タイマーの実行
    LaunchedEffect(isRunning, time) {
        if (isRunning && time > 0) {
            delay(1000L)
            time -= 1 // 1秒ごとにカウントダウン
        }
        if (time <= 0 && isRunning) {
            isCalling = true // タイマーが終了したことを示す

            // カウントダウン終了時の通知
            Toast.makeText(context, "待機が終了しました！", Toast.LENGTH_SHORT).show()

            // バイブレーションの実行
            if (vibrator?.hasVibrator() == true) {
                try {
                    val pattern = longArrayOf(0, 500, 200, 500) // 0ms待機，500ms振動，200ms振動，500ms振動
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createWaveform(pattern, 0) // 無限ループ
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    Log.d("WaitScreen", "バイブレーションを実行しました．")
                } catch (e: Exception) {
                    Log.e("WaitScreen", "バイブレーションに失敗しました: ${e.message}")
                    Toast.makeText(context, "バイブレーションに失敗しました！", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("WaitScreen", "デバイスはバイブレーションにサポートしていません．")
                Toast.makeText(context, "デバイスはバイブレーションをサポートしていません．", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UIの構築
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isCalling) {
            // タイマーが動作中または停止中の通常表示
            Text(
                text = "残り時間: ${formatTime(time)}",
                style = MaterialTheme.typography.headlineMedium,
            )

            // ボタンコンテナ
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 開始/停止ボタンの追加
                Button(
                    onClick = {
                        onStartPauseClick(isRunning) // 停止ボタンが押されたときにタイマーを停止
                    },
                    modifier = Modifier.width(100.dp)
                ) {
                    Text(if (isRunning) "Pause" else "Start") // 状態に応じてボタン表示を変更
                }

                // リセットボタン
                Button(
                    onClick = {
                        onResetClick()
                        vibrator?.cancel() // バイブレーションを停止
                        isCalling = false // 通話状態のリセット
                        time = timeLeft // タイマーのリセット
                    },
                    modifier = Modifier.width(100.dp)
                ) {
                    Text("Reset")
                }
            }
        } else {
            // タイマーが終了したときの表示
            Text(
                text = "足立さんから着信が来ています",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 終了後の操作ボタン
            Button(
                onClick = {
                    onResetClick()
                    vibrator?.cancel()
                    isCalling = false
                    time  = timeLeft
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Reset Timer")
            }
        }
    }
}