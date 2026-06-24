package com.example.voicecontrolapp

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 调试日志管理器
 * 收集和管理应用运行时的调试信息
 */
object DebugLogManager {
    
    private const val TAG = "DebugLogManager"
    private const val MAX_LOGS = 500 // 最大保存日志条数
    
    private val _logs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logs: StateFlow<List<DebugLogEntry>> = _logs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * 添加调试日志
     */
    fun addLog(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = System.currentTimeMillis()
        val timeString = dateFormat.format(Date(timestamp))
        
        val logEntry = DebugLogEntry(
            timestamp = timestamp,
            timeString = timeString,
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, logEntry) // 添加到开头，最新的在前面
        
        // 限制日志数量
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        
        _logs.value = currentLogs
        
        // 同时输出到系统日志
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
    
    /**
     * 添加网络请求日志
     */
    fun addNetworkLog(method: String, url: String, statusCode: Int? = null, response: String? = null, error: String? = null) {
        val message = buildString {
            append("[$method] $url")
            statusCode?.let { append(" -> $it") }
            response?.let { append("\n响应: $it") }
            error?.let { append("\n错误: $it") }
        }
        
        val level = when {
            error != null -> LogLevel.ERROR
            statusCode != null && statusCode >= 400 -> LogLevel.WARN
            statusCode != null && statusCode >= 200 && statusCode < 300 -> LogLevel.INFO
            else -> LogLevel.DEBUG
        }
        
        addLog(level, "Network", message)
    }
    
    /**
     * 添加连接测试日志
     */
    fun addConnectionTestLog(url: String, success: Boolean, details: String) {
        val message = "连接测试: $url\n结果: ${if (success) "成功" else "失败"}\n详情: $details"
        addLog(if (success) LogLevel.INFO else LogLevel.ERROR, "Connection", message)
    }
    
    /**
     * 清空所有日志
     */
    fun clearLogs() {
        _logs.value = emptyList()
        addLog(LogLevel.INFO, TAG, "日志已清空")
    }
    
    /**
     * 获取系统信息
     */
    fun getSystemInfo(): String {
        return buildString {
            appendLine("=== 系统信息 ===")
            appendLine("Android版本: ${android.os.Build.VERSION.RELEASE}")
            appendLine("SDK版本: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("设备型号: ${android.os.Build.MODEL}")
            appendLine("设备品牌: ${android.os.Build.BRAND}")
            appendLine("应用版本: VoiceControlApp 1.0")
            appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("================")
        }
    }
    
    /**
     * 导出所有日志为文本
     */
    fun exportLogsAsText(): String {
        val systemInfo = getSystemInfo()
        val logEntries = _logs.value
        
        return buildString {
            append(systemInfo)
            appendLine()
            appendLine("=== 调试日志 ===")
            
            logEntries.forEach { entry ->
                appendLine("${entry.timeString} [${entry.level.name}] ${entry.tag}: ${entry.message}")
                entry.throwable?.let { throwable ->
                    appendLine("异常: ${throwable.message}")
                    appendLine(Log.getStackTraceString(throwable))
                }
                appendLine("---")
            }
        }
    }
}

/**
 * 调试日志条目
 */
data class DebugLogEntry(
    val timestamp: Long,
    val timeString: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * 日志级别
 */
enum class LogLevel(val displayName: String, val color: androidx.compose.ui.graphics.Color) {
    DEBUG("调试", androidx.compose.ui.graphics.Color.Gray),
    INFO("信息", androidx.compose.ui.graphics.Color.Blue),
    WARN("警告", androidx.compose.ui.graphics.Color(0xFFFF9800)),
    ERROR("错误", androidx.compose.ui.graphics.Color.Red)
}