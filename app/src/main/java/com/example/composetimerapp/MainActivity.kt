package com.example.composetimerapp

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.navigation.compose.*
import com.example.composetimerapp.ui.theme.ComposeTimerAppTheme
import kotlinx.coroutines.delay
import kotlin.math.sqrt

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
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    // スライダーによる時間設定用の状態
    var selectedMinutes by remember { mutableFloatStateOf(1f) } // 初期値:1分
    var selectedSeconds by remember { mutableFloatStateOf(0f) } // 初期値:0分

    /////////////////////////////////////////////
    //20241003追加 by komoda
    // 運動セッションの時間設定（秒単位）
    var exerciseDurationSeconds by remember { mutableLongStateOf(60L) }

    // SensorManager の取得
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // バイブレーション実行中かどうかのフラグ
    var isVibrating by remember { mutableStateOf(false) }
    //フラグはこういうisから始めるのが慣例？
    var isShaking by remember { mutableStateOf(false) }

    // シェイクの振り始めの検出時間を管理
    var firstShakeTime by remember { mutableStateOf(0L) }
    // シェイクの最後の検出時間を追跡
    var lastShakeTime by remember { mutableStateOf(0L) }
    // 振っていない時間
    var notShakingTime by remember { mutableStateOf(0L) }

    // 運動セッションの状態管理
    var isVibrationSessionActive by remember { mutableStateOf(false) }// タイマーの実行にてtrueにする
    var vibrationSessionTimeLeft by remember { mutableStateOf(60L) } // 初期値: 60秒

    // ShakeDetector の呼び出し
    val sensorEventListener = shakeDetector {
        if (firstShakeTime == 0L) {
            firstShakeTime = System.currentTimeMillis()
            isShaking = true
        }
    }

    // センサーリスナーの登録と解除
    DisposableEffect(sensorManager) {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
    /////////////////////////////////////////////

    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1000L)
            onTimeSet(timeLeft - 1)
        }
        if (timeLeft <= 0 && isRunning) {
            Toast.makeText(context, "タイマーが終了しました！", Toast.LENGTH_SHORT).show()
            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Minutes: ${selectedMinutes.toInt()}", fontSize = 18.sp)
        Slider(
            value = selectedMinutes,
            onValueChange = { selectedMinutes = it },
            valueRange = 0f..60f,
            steps = 59,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Seconds: ${selectedSeconds.toInt()}", fontSize = 18.sp)
        Slider(
            value = selectedSeconds,
            onValueChange = { selectedSeconds = it },
            valueRange = 0f..59f,
            steps = 58,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Exercise Duration: ${exerciseDurationSeconds}s", fontSize = 18.sp)
        Slider(
            value = exerciseDurationSeconds.toFloat(),
            onValueChange = { exerciseDurationSeconds = it.toLong() },
            valueRange = 10f..120f,
            steps = 110,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = formatTime(timeLeft), fontSize = 48.sp, fontWeight = FontWeight.Bold)

        Text(
            text = if (isShaking) "Shaking" else "No Shaking",
            fontSize = 24.sp,
            color = if (isShaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (!isRunning) {
                        val totalSeconds = (selectedMinutes.toInt() * 60 + selectedSeconds.toInt()).toLong()
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

            Button(
                onClick = {
                    onTimeSet(60L)
                    selectedMinutes = 1f
                    selectedSeconds = 0f
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (vibrator?.hasVibrator() == true) {
                    val pattern = longArrayOf(0, 500, 200, 500)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, -1)
                    }
                    Toast.makeText(context, "バイブレーションを実行しました！", Toast.LENGTH_SHORT).show()
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
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
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
                text = "足立さんが着信が来ています",
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

/////////////////////////////////////////
// 20241003追加by komoda
@Composable
fun shakeDetector(
    onShake: () -> Unit
): SensorEventListener {
    val SHAKE_THRESHOLD_GRAVITY = 1f
    val SHAKE_SLOP_TIME_MS = 500
    var shakeTimestamp by remember { mutableStateOf(0L) }

    return object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val gX = x / SensorManager.GRAVITY_EARTH
                val gY = y / SensorManager.GRAVITY_EARTH
                val gZ = z / SensorManager.GRAVITY_EARTH

                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                    val now = System.currentTimeMillis()
                    if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                        return
                    }
                    shakeTimestamp = now
                    onShake()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
}
/////////////////////////////////////////