package com.example.composetimerapp

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.navigation.compose.*
import com.example.composetimerapp.ui.theme.ComposeTimerAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "TIMER_CHANNEL_ID"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Timer Notification",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Timer Running")
            .setContentText("Your timer is running.")
            .setSmallIcon(R.drawable.ic_timer)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
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
                },
                onResetClick = {
                    isRunning = false
                    timeLeft = 60L // タイマーをリセット
                    navController.navigate("timer")
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
                onStartClick = {
                    isRunning = true
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
        Image(
            painter = painterResource(id = R.drawable.title_image),
            contentDescription = "Title",
            modifier = Modifier
                .size(200.dp)
                .fillMaxWidth(),// 横幅いっぱいに拡張
            contentScale = ContentScale.Fit // 画像が見切れないように収める
        )
        Text(
            text = "足立さん",
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
    onPauseClick: () -> Unit,
    onResetClick: () -> Unit
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

    // タイマー設定用の状態
    var selectedHours by remember { mutableStateOf(0) } // 初期値: 0時間
    var selectedMinutes by remember { mutableStateOf(0) } // 初期値: 0分
    var selectedSeconds by remember { mutableStateOf(0) } // 初期値: 0秒

    // LazyListState を作成
    val hoursListState = rememberLazyListState()
    val minutesListState = rememberLazyListState()
    val secondsListState = rememberLazyListState()

    // タイマーの時間を計算
    val totalSelectedTime = (selectedHours * 3600 + selectedMinutes * 60 + selectedSeconds).toLong()

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 時間のピッカー
            Picker(
                label = "時間",
                value = selectedHours,
                range = 0..99,
                onValueChange = { selectedHours = it },
                listState = hoursListState
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 分のピッカー
            Picker(
                label = "分",
                value = selectedMinutes,
                range = 0..59,
                onValueChange = { selectedMinutes = it },
                listState = minutesListState
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 秒のピッカー
            Picker(
                label = "秒",
                value = selectedSeconds,
                range = 0..59,
                onValueChange = { selectedSeconds = it },
                listState = secondsListState
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // タイマー表示
        Text(
            text = formatTime(if (isRunning) timeLeft else totalSelectedTime),
            fontSize = 60.sp, // フォントサイズを大きく
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // ボタンコンテナ
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // スタート/ポーズボタン
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        if (!isRunning) {
                            val totalSeconds = (selectedHours * 3600 + selectedMinutes * 60 + selectedSeconds).toLong()

                            //clickした際にバックグラウンドサービスを開始
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
                    modifier = Modifier
                        .width(150.dp)  // ボタンの幅を広げる
                        .padding(8.dp)  // パディングを追加して余裕を持たせる
                ) {
                    Text("スタート", fontSize = 20.sp)  // テキストサイズを大きく
                }

                // リセットボタン
                Button(
                    onClick = {
                        onResetClick()
                        //onTimeSet(60L) // 初期値にリセット
                        selectedHours = 0 // 0時間にリセット
                        selectedMinutes = 0 // 0分にリセット
                        selectedSeconds = 0 // 0秒にリセット

                        /// リストの中央に0が来るようにスクロールさせる
                        CoroutineScope(Dispatchers.Main).launch {

                            // 各リストの0の位置にスクロール
                            hoursListState.scrollToItem(0) // 0のインデックスに調整
                            minutesListState.scrollToItem(0)
                            secondsListState.scrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .width(150.dp)
                        .padding(8.dp)
                ) {
                    Text("リセット", fontSize = 20.sp)
                }
            }
        }

        /*
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
        */
    }
}

@Composable
fun Picker(label: String,
           value: Int,
           range: IntRange,
           onValueChange: (Int) -> Unit,
           listState: LazyListState // 新しく追加
) {
    val itemCount = range.count()

    // 0..59 or 0..99を2回繰り返すリストを作成
    val infiniteList = List(itemCount * 200) {
        if (it / itemCount % 2 == 0) it % itemCount   // 0..59 or 0..99
        else it % itemCount         // 0..59 or 0..99
    }

    // 初期値を1つ上にずらして設定
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (infiniteList.size / 2) - 1)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))

        Box(
            modifier = Modifier
                .height(130.dp) // 少し高さを持たせて中央を強調
                .width(60.dp)
                .background(Color.LightGray) // 背景色で視認性を向上
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                flingBehavior = rememberSnapFlingBehavior(listState) // スナッピングの動作を追加
            ) {
                // リストを拡張して、0..59 -> 0..59 を無限に繰り返す
                items(infiniteList) { item ->
                    val displayValue = range.elementAt(item)
                    val isSelected = displayValue == value
                    Text(
                        text = displayValue.toString(),
                        fontSize = 24.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = if (isSelected) Color.Black else Color.Gray
                    )
                }
            }
        }

        // スクロールが停止した後に中央のアイテムに基づいて選択を更新
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress) {
                // 現在の表示リストの中央にある項目のインデックスを取得
                val middleIndex = listState.firstVisibleItemIndex + (listState.layoutInfo.visibleItemsInfo.size / 2)
                val selectedItem = infiniteList[middleIndex % infiniteList.size]

                // 選択された秒数を更新
                if (range.elementAt(selectedItem) != value) {
                    onValueChange(range.elementAt(selectedItem))
                }

                // リストの範囲を超えた場合にジャンプして無限ループのように見せる
                if (middleIndex < itemCount || middleIndex > infiniteList.size - itemCount) {
                    listState.scrollToItem((infiniteList.size / 2) + value)
                }

            }
        }
    }
}

// リストを繰り返す拡張関数
fun <T> List<T>.repeat(times: Int): List<T> {
    return List(this.size * times) { this[it % this.size] }
}

@Composable
fun WaitScreen(
    timeLeft: Long,
    isRunning: Boolean,
    onStartPauseClick: (Boolean) -> Unit, // 停止ボタン用のコールバックを追加
    onStartClick: () -> Unit, // タイマーの再起動用コールバック
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

    // MediaPlayerのインスタンスを作成
    val mediaPlayer = remember { MediaPlayer.create(context, R.raw.ringtone) } // ringtone.mp3 を res/raw に置いたと仮定
    mediaPlayer.isLooping = true // 音楽を繰り返し再生する設定

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

            //タイマー終了時に音を再生
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
            }

            // 5秒間待機
            delay(5000L)
            vibrator?.cancel()

            // タイマーを再起動
            time = timeLeft // もとの時間で再スタート
            isCalling = false
            onStartClick() // タイマーを再起動
        }

        // isCallingがfalseになったら音を停止
        if (!isCalling && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.prepare() // 次回の再生のために準備
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
                text = "着信まで",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),  // テキストを横幅いっぱいに広げる
                textAlign = TextAlign.Center  // テキストを中央揃えにする
            )
            Image(
                painter = painterResource(id = R.drawable.timer_running_image),
                contentDescription = "Timer Running",
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "残り時間: ${formatTime(time)}",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 36.sp  // フォントサイズを大きく
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
                    modifier = Modifier
                        .width(150.dp)
                        .padding(8.dp)
                ) {
                    Text(if (isRunning) "ストップ" else "スタート", fontSize = 20.sp)  // テキストサイズを大きく
                }

                // リセットボタン
                Button(
                    onClick = {
                        onResetClick()
                        vibrator?.cancel() // バイブレーションを停止
                        isCalling = false // 通話状態のリセット
                        time = timeLeft // タイマーのリセット
                    },
                    modifier = Modifier
                        .width(150.dp)
                        .padding(8.dp)
                ) {
                    Text("リセット", fontSize = 20.sp)
                }
            }
        } else {
            // タイマーが終了したときの表示
            Image(
                painter = painterResource(id = R.drawable.timer_finished_image),
                contentDescription = "Timer Finished",
                modifier = Modifier
                    .size(200.dp)
                    .fillMaxWidth(),// 横幅いっぱいに拡張
                contentScale = ContentScale.Fit // 画像が見切れないように収める
            )
            Text(
                text = "足立さんから\n着信が来ています",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),  // テキストを横幅いっぱいに広げる
                textAlign = TextAlign.Center  // テキストを中央揃えにする
            )

            // 終了後の操作ボタン
            Button(
                onClick = {
                    onResetClick()
                    vibrator?.cancel()
                    mediaPlayer.stop() // 音楽の停止
                    mediaPlayer.prepare() // 再度再生できるように準備
                    isCalling = false
                    time  = timeLeft
                },
                modifier = Modifier
                    .width(200.dp)
                    .padding(8.dp)
            ) {
                Text(
                    text = "タイマーを\nリセットする",
                    fontSize = 20.sp,
                    lineHeight = 28.sp,  // 行間を広げる
                    modifier = Modifier.fillMaxWidth(),  // テキストを横幅いっぱいに広げる
                    textAlign = TextAlign.Center  // テキストを中央揃えにする
                )
            }
        }
    }
}