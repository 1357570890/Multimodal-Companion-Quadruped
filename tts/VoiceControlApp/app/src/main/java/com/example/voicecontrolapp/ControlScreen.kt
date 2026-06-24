package com.example.voicecontrolapp

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
fun ControlScreen(
    context: Context = LocalContext.current,
    viewModel: ControlViewModel = viewModel()
) {
    // 初始化ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    val isConnected by viewModel.isConnected
    val connectionStatus by viewModel.connectionStatus
    val lastCommand by viewModel.lastCommand
    val commandHistory by viewModel.commandHistory
    val commandFrequency by viewModel.commandFrequency
    val isRecording by viewModel.isRecording
    val asrResult by viewModel.asrResult
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // 标题和状态
        Text(
            text = "机械狗手动控制",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 连接状态
        ConnectionStatusCard(
            isConnected = isConnected,
            status = connectionStatus,
            lastCommand = lastCommand
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 语音控制区域
        VoiceControlCard(
            isRecording = isRecording,
            asrResult = asrResult,
            onMicClick = {
                if (isRecording) {
                    viewModel.stopVoiceRecognition()
                } else {
                    viewModel.startVoiceRecognition()
                }
            },
            enabled = isConnected
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 方向控制区域
        DirectionControlArea(
            onCommand = { command -> viewModel.sendCommand(command) },
            enabled = isConnected,
            commandFrequency = commandFrequency
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 动作控制区域
        ActionControlArea(
            onCommand = { command -> viewModel.sendCommand(command) },
            enabled = isConnected
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 命令历史
        CommandHistoryCard(commandHistory = commandHistory)
    }
}

@Composable
fun VoiceControlCard(
    isRecording: Boolean,
    asrResult: String,
    onMicClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "语音控制",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = asrResult.ifEmpty { if (enabled) "点击麦克风开始说话" else "请先连接机械狗" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onMicClick,
                enabled = enabled,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> MaterialTheme.colorScheme.surfaceVariant
                            isRecording -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止录音" else "开始录音",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    status: String,
    lastCommand: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isConnected) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (lastCommand != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "最后命令: $lastCommand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DirectionControlArea(
    onCommand: (String) -> Unit,
    enabled: Boolean,
    commandFrequency: Long = 200L
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "方向控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 方向控制布局
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 前进
                ControlButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    label = "前进",
                    command = "forward",
                    onCommand = onCommand,
                    enabled = enabled,
                    commandFrequency = commandFrequency
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 左转
                    ControlButton(
                        icon = Icons.Default.KeyboardArrowLeft,
                        label = "左转",
                        command = "left",
                        onCommand = onCommand,
                        enabled = enabled,
                        commandFrequency = commandFrequency
                    )

                    // 停止
                    ControlButton(
                        icon = Icons.Default.Stop,
                        label = "停止",
                        command = "stop",
                        onCommand = onCommand,
                        enabled = enabled,
                        isStopButton = true
                    )

                    // 右转
                    ControlButton(
                        icon = Icons.Default.KeyboardArrowRight,
                        label = "右转",
                        command = "right",
                        onCommand = onCommand,
                        enabled = enabled,
                        commandFrequency = commandFrequency
                    )
                }

                // 后退
                ControlButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    label = "后退",
                    command = "backward",
                    onCommand = onCommand,
                    enabled = enabled,
                    commandFrequency = commandFrequency
                )
            }
        }
    }
}

@Composable
fun ActionControlArea(
    onCommand: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "动作控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 动作按钮网格
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.ExpandLess,
                    label = "站立",
                    command = "stand",
                    onCommand = onCommand,
                    enabled = enabled
                )
                
                ActionButton(
                    icon = Icons.Default.ExpandMore,
                    label = "趴下",
                    command = "lie",
                    onCommand = onCommand,
                    enabled = enabled
                )
                
                ActionButton(
                    icon = Icons.Default.PlayArrow,
                    label = "快跑",
                    command = "run",
                    onCommand = onCommand,
                    enabled = enabled
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Pets,
                    label = "握手",
                    command = "shake",
                    onCommand = onCommand,
                    enabled = enabled
                )
                
                ActionButton(
                    icon = Icons.Default.Refresh,
                    label = "转圈",
                    command = "spin",
                    onCommand = onCommand,
                    enabled = enabled
                )
                
                ActionButton(
                    icon = Icons.Default.Home,
                    label = "回家",
                    command = "home",
                    onCommand = onCommand,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    label: String,
    command: String,
    onCommand: (String) -> Unit,
    enabled: Boolean,
    isStopButton: Boolean = false,
    commandFrequency: Long = 200L // 默认200ms间隔
) {
    var isPressed by remember { mutableStateOf(false) }

    // 使用LaunchedEffect来处理持续发送命令
    LaunchedEffect(isPressed, command, commandFrequency) {
        if (isPressed && enabled && !isStopButton) {
            // 立即发送第一个命令
            onCommand(command)

            // 然后按照配置的频率持续发送
            while (isPressed) {
                delay(commandFrequency) // 使用配置的发送间隔
                if (isPressed) { // 再次检查状态，防止在delay期间状态改变
                    onCommand(command)
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .size(80.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true

                            // 如果是停止按钮，立即发送停止命令
                            if (isStopButton) {
                                onCommand(command)
                            }

                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                                // 松开时发送停止命令（除非本身就是停止按钮）
                                if (!isStopButton) {
                                    onCommand("stop")
                                }
                            }
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant
                isStopButton -> MaterialTheme.colorScheme.errorContainer
                isPressed -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        border = if (isPressed) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) {
                    if (isStopButton) MaterialTheme.colorScheme.onErrorContainer
                    else if (isPressed) Color.White
                    else MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    if (isStopButton) MaterialTheme.colorScheme.onErrorContainer
                    else if (isPressed) Color.White
                    else MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    command: String,
    onCommand: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .size(width = 100.dp, height = 80.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = { 
                    if (enabled) {
                        onCommand(command)
                    }
                },
                enabled = enabled
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onTertiaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CommandHistoryCard(
    commandHistory: List<String>
) {
    if (commandHistory.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "命令历史",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                commandHistory.takeLast(5).forEach { command ->
                    Text(
                        text = "• $command",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}