package com.example.composetimerapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import com.example.composetimerapp.ui.theme.ComposeTimerAppTheme
import kotlinx.coroutines.delay

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
    NavHost(navController = navController, startDestination = "start") {
        composable("start") {
            StartScreen(onStartClick = { navController.navigate("timer") })
        }
        composable("timer") {
            TimerScreen()
        }
    }
}

@Composable
fun StartScreen(onStartClick: () -> Unit) {
    // 自動遷移を実装
    LaunchedEffect(Unit) {
        delay(2000L) // 2秒待機
        onStartClick()
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

@Composable
fun TimerScreen() {
    // タイマーの状態管理
    var timeLeft by remember { mutableLongStateOf(60L) } // 初期値: 60秒
    var isRunning by remember { mutableStateOf(false) }

    // スクロールによる時間設定用の状態
    var selectedMinutes by remember { mutableFloatStateOf(1f) } // 初期値: 1分
    var selectedSeconds by remember { mutableFloatStateOf(0f) } // 初期値: 0秒

    // タイマーの実行
    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1000L)
            timeLeft -= 1
        }
        if (timeLeft <= 0 && isRunning) {
            isRunning = false
            // タイマー終了時の処理（例: トーストメッセージなど）
            // トーストメッセージを表示する場合は以下を有効にしてください
            // val context = LocalContext.current
            // Toast.makeText(context, "Timer Finished!", Toast.LENGTH_SHORT).show()
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
    }
}

// 時間を分:秒形式にフォーマットする関数
@SuppressLint("DefaultLocale")
fun formatTime(seconds: Long): String {
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return String.format("%02d:%02d", minutesPart, secondsPart)
}
