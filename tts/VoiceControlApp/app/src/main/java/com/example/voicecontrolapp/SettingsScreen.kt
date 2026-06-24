package com.example.voicecontrolapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onDebugLogClick: () -> Unit = {}
) {
    val raspberryPiUrl by viewModel.raspberryPiUrl
    val isConnected by viewModel.isConnected
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope() // 添加这行
    
    var urlInput by remember { mutableStateOf(raspberryPiUrl) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    
    // 同步URL变化
    LaunchedEffect(raspberryPiUrl) {
        urlInput = raspberryPiUrl
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") }
            )
        }
    ) { paddingValues ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 树莓派连接设置
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "树莓派连接设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { 
                            urlInput = it
                            saveMessage = null // 清除之前的保存消息
                        },
                        label = { Text("树莓派地址") },
                        placeholder = { Text("例如: 192.168.1.100:8080") },
                        supportingText = { 
                            Text("格式: IP地址:端口号，或域名:端口号") 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            }
                        ),
                        trailingIcon = {
                            if (urlInput != raspberryPiUrl && urlInput.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        // 直接保存并显示结果
                                        if (urlInput.isNotBlank()) {
                                            viewModel.updateRaspberryPiUrl(urlInput)
                                            saveMessage = "IP地址已保存！"
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "保存"
                                    )
                                }
                            }
                        }
                    )
                    
                    // 保存消息显示
                    if (saveMessage != null) {
                        Text(
                            text = saveMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    
                    // 保存按钮
                    if (urlInput != raspberryPiUrl && urlInput.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.updateRaspberryPiUrl(urlInput)
                                    saveMessage = "IP地址已保存并应用到所有页面！"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "保存",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("保存并应用")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    urlInput = raspberryPiUrl
                                    saveMessage = null
                                }
                            ) {
                                Text("取消")
                            }
                        }
                    }
                    
                    // 连接状态显示和测试
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = when {
                                            isTestingConnection -> Color.Yellow
                                            isConnected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isTestingConnection -> "正在测试连接..."
                                    isConnected -> "已连接"
                                    else -> "未连接"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isTestingConnection -> MaterialTheme.colorScheme.onSurface
                                    isConnected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    viewModel.refreshConnection()
                                    // 2秒后重置测试状态
                                    coroutineScope.launch {
                                        delay(2000)
                                        isTestingConnection = false
                                    }
                                },
                                enabled = !isTestingConnection,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "测试连接",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("测试连接")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    viewModel.performNetworkDiagnosis()
                                },
                                enabled = !isTestingConnection
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "网络诊断",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("诊断")
                            }
                        }
                    }
                }
            }
            
            // 语音模式设置
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "语音处理模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    var currentMode by remember {
                        mutableStateOf(viewModel.getVoiceMode())
                    }
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 文本模式选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentMode == ConfigManager.VOICE_MODE_TEXT_ONLY,
                                onClick = {
                                    currentMode = ConfigManager.VOICE_MODE_TEXT_ONLY
                                    viewModel.updateVoiceMode(ConfigManager.VOICE_MODE_TEXT_ONLY)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "纯文本模式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "仅进行语音识别，返回识别的文字结果",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // AI推断模式选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentMode == ConfigManager.VOICE_MODE_LLM_INFERENCE,
                                onClick = {
                                    currentMode = ConfigManager.VOICE_MODE_LLM_INFERENCE
                                    viewModel.updateVoiceMode(ConfigManager.VOICE_MODE_LLM_INFERENCE)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "AI智能推断模式",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "语音识别后提交给通义千问AI进行智能分析和回应",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // 当前模式状态显示
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentMode == ConfigManager.VOICE_MODE_LLM_INFERENCE)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentMode == ConfigManager.VOICE_MODE_LLM_INFERENCE)
                                "🤖 当前启用AI智能推断模式"
                            else "📝 当前为纯文本识别模式",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            
            // API端点说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "树莓派API端点",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "您的树莓派需要提供以下API端点：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ApiEndpointItem(
                            method = "GET",
                            path = "/api/health",
                            description = "健康检查"
                        )
                        
                        ApiEndpointItem(
                            method = "POST",
                            path = "/api/upload_voice",
                            description = "语音文件上传"
                        )
                        
                        ApiEndpointItem(
                            method = "POST",
                            path = "/api/control",
                            description = "控制命令"
                        )
                    }
                }
            }
            
            // 配置同步说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "配置同步",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "• 点击\"保存并应用\"后，IP地址会自动同步到所有页面\n" +
                               "• 语音控制、摄像头、手动控制都将使用新的IP地址\n" +
                               "• 配置会自动保存，重启应用后不会丢失\n" +
                               "• 测试连接功能会验证与fake_server的连通性",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // 使用提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "配置提示",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "• 确保手机和树莓派在同一网络下\n" +
                               "• 树莓派需要运行HTTP服务器\n" +
                               "• 推荐使用fake_server进行测试（端口8080）\n" +
                               "• 可以使用IP地址或域名",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
            
            // 调试工具
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "调试工具",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "遇到连接问题？查看详细的调试日志来诊断问题。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Button(
                        onClick = onDebugLogClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "调试日志",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("查看调试日志")
                    }
                }
            }
        }
    }
}

@Composable
fun ApiEndpointItem(
    method: String,
    path: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = when (method) {
                "GET" -> MaterialTheme.colorScheme.primaryContainer
                "POST" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = method,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}