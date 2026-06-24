package com.example.voicecontrolapp

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    context: Context = LocalContext.current,
    viewModel: CameraViewModel = viewModel()
) {
    // 初始化ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    val isConnected by viewModel.isConnected
    val cameraUrl by viewModel.cameraUrl
    val isStreaming by viewModel.isStreaming
    val errorMessage by viewModel.errorMessage
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // 标题和控制区域
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "机械狗摄像头",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                IconButton(
                    onClick = { viewModel.refreshCamera() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新摄像头"
                    )
                }
                
                IconButton(
                    onClick = { 
                        if (isStreaming) {
                            viewModel.stopStream()
                        } else {
                            viewModel.startStream()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = if (isStreaming) "停止直播" else "开始直播"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 状态指示器
        StatusIndicator(
            isConnected = isConnected,
            isStreaming = isStreaming,
            errorMessage = errorMessage
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 摄像头视频区域
        CameraVideoArea(
            cameraUrl = cameraUrl,
            isStreaming = isStreaming,
            isConnected = isConnected,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 摄像头设置
        CameraSettings(
            onUrlChange = { viewModel.updateCameraUrl(it) },
            currentUrl = cameraUrl
        )
    }
}

@Composable
fun StatusIndicator(
    isConnected: Boolean,
    isStreaming: Boolean,
    errorMessage: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                isStreaming -> MaterialTheme.colorScheme.primaryContainer
                isConnected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when {
                            errorMessage != null -> MaterialTheme.colorScheme.error
                            isStreaming -> Color.Green
                            isConnected -> MaterialTheme.colorScheme.primary
                            else -> Color.Gray
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = when {
                    errorMessage != null -> "错误: $errorMessage"
                    isStreaming -> "直播中"
                    isConnected -> "已连接，点击开始直播"
                    else -> "未连接到机械狗"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CameraVideoArea(
    cameraUrl: String,
    isStreaming: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isStreaming && cameraUrl.isNotEmpty() -> {
                    // 使用MjpegStreamDisplay加载MJPEG流
                    MjpegStreamDisplay(
                        url = cameraUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                
                !isConnected -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "摄像头离线",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "机械狗未连接",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "请检查网络连接和设置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "摄像头准备",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "摄像头准备就绪",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击开始按钮开始直播",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MjpegStreamDisplay(
    url: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember(url) { mutableStateOf(true) }
    var hasError by remember(url) { mutableStateOf(false) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    var retryCount by remember(url) { mutableStateOf(0) }
    var imageKey by remember(url) { mutableStateOf(0) }
    val context = LocalContext.current

    // 当URL改变时重置状态
    LaunchedEffect(url) {
        isLoading = true
        hasError = false
        errorMessage = null
        retryCount = 0
        imageKey++

        // 设置超时，如果10秒后还在加载则认为失败
        delay(10000)
        if (isLoading) {
            Log.w("MjpegStream", "加载超时，设置为错误状态")
            isLoading = false
            hasError = true
            errorMessage = "连接超时"
        }
    }

    // 定期刷新图像以模拟视频流
    LaunchedEffect(url, hasError) {
        if (!hasError && url.isNotEmpty()) {
            while (true) {
                delay(100) // 每100ms刷新一次，约10fps
                imageKey++
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 使用自定义MJPEG流组件 - 专门处理multipart/x-mixed-replace格式
        AndroidView(
            factory = { ctx ->
                MjpegStreamView(ctx).apply {
                    // 设置初始状态
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { mjpegView ->
                Log.d("CameraScreen", "=== 更新MJPEG流组件 ===")
                Log.d("CameraScreen", "URL: $url")
                Log.d("CameraScreen", "URL长度: ${url.length}")
                Log.d("CameraScreen", "URL是否为空: ${url.isEmpty()}")
                Log.d("CameraScreen", "当前线程: ${Thread.currentThread().name}")

                if (url.isEmpty()) {
                    Log.w("CameraScreen", "URL为空，显示测试图片")
                    mjpegView.showTestImage()
                    return@AndroidView
                }

                try {
                    Log.d("CameraScreen", "停止之前的流...")
                    mjpegView.stopStream()

                    // 先显示测试图片验证ImageView工作正常
                    Log.d("CameraScreen", "显示测试图片...")
                    mjpegView.showTestImage()

                    // 延迟启动MJPEG流
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("CameraScreen", "启动新的MJPEG流...")
                        mjpegView.startStream(url)
                    }, 2000)

                    // 设置加载状态
                    isLoading = true
                    hasError = false
                    errorMessage = null
                    Log.d("CameraScreen", "设置加载状态为true")

                    // 延迟设置成功状态
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("CameraScreen", "延迟回调执行，设置加载完成状态")
                        isLoading = false
                        hasError = false
                    }, 8000) // 增加到8秒给更多时间

                } catch (e: Exception) {
                    Log.e("CameraScreen", "启动MJPEG流异常", e)
                    Log.e("CameraScreen", "异常类型: ${e.javaClass.simpleName}")
                    Log.e("CameraScreen", "异常消息: ${e.message}")
                    isLoading = false
                    hasError = true
                    errorMessage = e.message
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 显示加载状态
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在连接摄像头...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (retryCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "重试次数: $retryCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 显示错误状态
        if (hasError && !isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "连接失败",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "无法连接到摄像头",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "错误: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "URL: $url",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        retryCount++
                        isLoading = true
                        hasError = false
                        errorMessage = null
                        Log.d("MjpegStream", "手动重新连接，重试次数: $retryCount")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重新连接")
                }
            }
        }
    }
}

@Composable
fun CameraSettings(
    onUrlChange: (String) -> Unit,
    currentUrl: String
) {
    var showSettings by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    
    Column {
        OutlinedButton(
            onClick = { showSettings = !showSettings },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showSettings) "隐藏设置" else "摄像头设置")
        }
        
        if (showSettings) {
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("摄像头流地址") },
                placeholder = { Text("例如: http://192.168.110.21:8080/remote_camera_feed") },
                supportingText = {
                    Text("输入机械狗远程摄像头的MJPEG流地址")
                },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (urlInput != currentUrl && urlInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onUrlChange(urlInput)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "应用设置"
                            )
                        }
                    }
                }
            )
        }
    }
}