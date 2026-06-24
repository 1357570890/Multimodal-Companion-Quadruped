package com.example.voicecontrolapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    onBackClick: () -> Unit
) {
    val logs by DebugLogManager.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var showSystemInfo by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试日志") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 显示系统信息按钮
                    TextButton(
                        onClick = { showSystemInfo = !showSystemInfo }
                    ) {
                        Text(if (showSystemInfo) "隐藏系统信息" else "系统信息")
                    }
                    
                    // 清空日志按钮
                    IconButton(
                        onClick = { DebugLogManager.clearLogs() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空日志"
                        )
                    }
                    
                    // 导出日志按钮
                    IconButton(
                        onClick = {
                            val exportText = DebugLogManager.exportLogsAsText()
                            clipboardManager.setText(AnnotatedString(exportText))
                            // 这里可以添加Toast提示
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "导出日志"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            
            // 系统信息卡片
            if (showSystemInfo) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = DebugLogManager.getSystemInfo(),
                            modifier = Modifier.padding(16.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // 日志统计信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LogLevelCounter(LogLevel.ERROR, logs.count { it.level == LogLevel.ERROR })
                    LogLevelCounter(LogLevel.WARN, logs.count { it.level == LogLevel.WARN })
                    LogLevelCounter(LogLevel.INFO, logs.count { it.level == LogLevel.INFO })
                    LogLevelCounter(LogLevel.DEBUG, logs.count { it.level == LogLevel.DEBUG })
                }
            }
            
            // 日志列表
            if (logs.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无日志记录\n\n请执行一些操作（如测试连接）来生成日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { logEntry ->
                        LogEntryCard(logEntry = logEntry)
                    }
                }
            }
        }
    }
}

@Composable
fun LogLevelCounter(
    level: LogLevel,
    count: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = level.color
        )
        Text(
            text = level.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = level.color
        )
    }
}

@Composable
fun LogEntryCard(
    logEntry: DebugLogEntry
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (logEntry.level) {
                LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 日志头部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日志级别标签
                    Surface(
                        color = logEntry.level.color,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = logEntry.level.displayName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // 标签
                    Text(
                        text = logEntry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 时间
                Text(
                    text = logEntry.timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 日志消息
            SelectionContainer {
                Text(
                    text = logEntry.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
            
            // 异常信息
            logEntry.throwable?.let { throwable ->
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = "异常: ${throwable.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}