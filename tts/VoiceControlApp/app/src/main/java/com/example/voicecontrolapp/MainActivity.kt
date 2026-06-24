package com.example.voicecontrolapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.voicecontrolapp.ui.theme.VoiceControlAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 添加应用启动日志
        DebugLogManager.addLog(LogLevel.INFO, "MainActivity", "应用启动")
        DebugLogManager.addLog(LogLevel.INFO, "System", DebugLogManager.getSystemInfo())

        // 初始化ViewModel
        mainViewModel.initialize(this)

        setContent {
            VoiceControlAppTheme {
                VoiceControlApp(mainViewModel)
            }
        }
    }
}

// 导航目标枚举
enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    Voice("voice", "语音控制", Icons.Default.Mic),
    Camera("camera", "摄像头", Icons.Default.Videocam),
    Control("control", "手动控制", Icons.Default.TouchApp),
    Settings("settings", "设置", Icons.Default.Settings),
    DebugLog("debug_log", "调试日志", Icons.Default.BugReport)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceControlApp(mainViewModel: MainViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Voice.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Voice.route) {
                VoiceScreen(viewModel = mainViewModel)
            }

            composable(Screen.Camera.route) {
                CameraScreen()
            }

            composable(Screen.Control.route) {
                ControlScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = mainViewModel,
                    onDebugLogClick = {
                        navController.navigate(Screen.DebugLog.route)
                    }
                )
            }

            composable(Screen.DebugLog.route) {
                DebugLogScreen(
                    onBackClick = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        Screen.values().forEach { screen ->
            if (screen != Screen.DebugLog) { // 不在底部导航栏显示调试日志
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = { Text(screen.title) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    // 权限处理
    val recordAudioPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    ) { granted ->
        viewModel.setRecordPermission(granted)
    }

    // 检查权限并更新ViewModel
    LaunchedEffect(recordAudioPermissionState.status.isGranted) {
        viewModel.setRecordPermission(recordAudioPermissionState.status.isGranted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 顶部状态栏
        StatusBar(viewModel = viewModel)

        Spacer(modifier = Modifier.height(16.dp)) 

        // 权限检查
        if (!recordAudioPermissionState.status.isGranted) {
            PermissionRequestCard(
                onRequestPermission = {
                    recordAudioPermissionState.launchPermissionRequest()
                },
                shouldShowRationale = recordAudioPermissionState.status.shouldShowRationale
            )
        } else {
            // 主要内容区域
            MainContent(viewModel)
        }
    }
}

@Composable
fun StatusBar(
    viewModel: MainViewModel
) {
    val isConnected by viewModel.isConnected
    val raspberryPiUrl by viewModel.raspberryPiUrl

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "连接状态",
                    tint = if (isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isConnected) "已连接" else "未连接",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = raspberryPiUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = { viewModel.refreshConnection() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新连接"
                )
            }
        }
    }
}

@Composable
fun PermissionRequestCard(
    onRequestPermission: () -> Unit,
    shouldShowRationale: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("需要录音权限", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (shouldShowRationale) "我们需要录音权限来捕捉您的语音命令。请授予权限以继续。" else "为了使用语音控制功能，请在应用设置中开启录音权限。")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    val isRecording by viewModel.isRecording
    val isSending by viewModel.isSending
    val uploadProgress by viewModel.uploadProgress
    val statusMessage by viewModel.statusMessage
    val recognizedText by viewModel.recognizedText

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上半部分: 文本显示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RecognizedTextDisplay(text = recognizedText)
            Spacer(modifier = Modifier.height(16.dp))
            StatusMessage(statusMessage, isSending, uploadProgress)
        }

        // 下半部分: 控制按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            VoiceButton(
                isRecording = isRecording,
                onToggleRecording = { viewModel.toggleRecording() },
                enabled = !isSending
            )
        }
    }
}


@Composable
fun RecognizedTextDisplay(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = if (text.isNotBlank()) text else "点击按钮开始说话...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                color = if (text.isNotBlank())
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusMessage(
    message: String,
    isSending: Boolean,
    uploadProgress: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp), // 固定高度以避免布局跳动
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (isSending) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (uploadProgress > 0) uploadProgress / 100f else 0f },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun VoiceButton(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    enabled: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 0.95f else 1f,
        label = "按钮缩放动画"
    )

    Button(
        onClick = {
            Log.d("VoiceButton", "按钮被点击，当前录音状态: $isRecording")
            onToggleRecording()
        },
        enabled = enabled,
        modifier = Modifier
            .size(200.dp)
            .scale(scale),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant
                isRecording -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
        ),
        border = BorderStroke(
            width = 4.dp,
            color = Color.White.copy(alpha = 0.5f)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isRecording) "正在录音" else "开始录音",
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isRecording) "点击结束" else "点击开始",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun UsageInstructions() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "使用说明",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• 点击圆形按钮开始录音\n• 再次点击按钮停止并发送\n• 确保树莓派设备已连接\n• 语音将自动传输到树莓派执行",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VoiceControlAppPreview() {
    VoiceControlAppTheme {
        // Preview内容
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("语音控制应用预览")
        }
    }
}
