package com.example.composetimerapp

import android.annotation.SuppressLint
import android.content.Context
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
    val navController = rememberNavController()
    NavHost(navController, startDestination = "start") {
        // Start page
        composable("start") {
            StartScreen(onStartTransition = { navController.navigate("timer") })
        }
        // Time setting page
        composable("timer") {
            TimerScreen(onStartClick = {navController.navigate("wait") })
        }
        // waiting call page
        composable("wait") {
            WaitScreen(onResetClick = {navController.navigate("timer")})
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
        // スタートボタンを削除
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun TimerScreen(onStartClick: () -> Unit) {
    // コンテキストの取得
    val context = LocalContext.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        }
    }

    // タイマーの状態管理
    var timeLeft by remember { mutableLongStateOf(60L) } // 初期値: 60秒
    var isRunning by remember { mutableStateOf(false) }

    // スクロールによる時間設定用の状態
    var selectedMinutes by remember { mutableFloatStateOf(1f) } // 初期値: 1分
    var selectedSeconds by remember { mutableFloatStateOf(0f) } // 初期値: 0秒

    // タイマーの実行
    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            onStartClick()
            delay(1000L)
            timeLeft -= 1
        }
        if (timeLeft <= 0 && isRunning) {
            isRunning = false
            // タイマー終了の通知
            Log.d("TimerScreen", "タイマーが終了しました。")
            Toast.makeText(context, "タイマーが終了しました！", Toast.LENGTH_SHORT).show()

            // バイブレーションの実行
            if (vibrator?.hasVibrator() == true) {
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
                        val totalSeconds = (selectedMinutes.toInt() * 60 + selectedSeconds.toInt()).toLong()
                        if (totalSeconds > 0) {
                            timeLeft = totalSeconds
                            isRunning = true
                        }
                    } else {
                        isRunning = false
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
                if (vibrator?.hasVibrator() == true) {
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
fun WaitScreen(onResetClick: () -> Unit) {
    // コンテキストの取得
    val context = LocalContext.current

    // タイマーの状態管理（初期値: 10秒）
    var timeLeft by remember { mutableLongStateOf(10L) }
    var isRunning by remember { mutableStateOf(true) }


    // タイマーの実行
    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1000L) // 1秒ごとにカウントダウン
            timeLeft -= 1
        }
        if (timeLeft <= 0 && isRunning) {
            isRunning = false
            // カウントダウン終了時の通知
            Toast.makeText(context, "待機が終了しました！", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 残り時間の表示
        Text(
            text = "残り時間: ${formatTime(timeLeft)}",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // リセットボタン
        Button(
            onClick = {
                isRunning = false
                timeLeft = 10L // 初期値にリセット
                onResetClick()
            },
            modifier = Modifier.width(100.dp)
        ) {
            Text("Reset")
        }
    }
}