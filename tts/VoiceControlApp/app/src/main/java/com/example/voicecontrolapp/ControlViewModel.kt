package com.example.voicecontrolapp

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ControlViewModel : ViewModel(), XunfeiAsrClient.AsrListener, VoiceRecorderManager.AudioStreamListener {
    
    // 管理器
    private val networkManager = NetworkManager()
    private lateinit var configManager: ConfigManager
    private lateinit var voiceRecorderManager: VoiceRecorderManager
    private lateinit var xunfeiAsrClient: XunfeiAsrClient
    
    // UI状态
    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected
    
    private val _connectionStatus = mutableStateOf("检查连接中...")
    val connectionStatus: State<String> = _connectionStatus
    
    private val _robotUrl = mutableStateOf("192.168.1.100:8080")
    val robotUrl: State<String> = _robotUrl
    
    private val _lastCommand = mutableStateOf<String?>(null)
    val lastCommand: State<String?> = _lastCommand
    
    private val _commandHistory = mutableStateOf<List<String>>(emptyList())
    val commandHistory: State<List<String>> = _commandHistory

    // 语音识别相关状态
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _asrResult = mutableStateOf("")
    val asrResult: State<String> = _asrResult

    private var isFirstAudioFrame = true
    private var connectionCheckJob: Job? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    // 控制命令发送频率配置
    private val _commandFrequency = mutableStateOf(200L) // 默认200ms间隔，即5Hz
    val commandFrequency: State<Long> = _commandFrequency

    companion object {
        private const val TAG = "ControlViewModel"
        private const val CONTROL_PATH = "/api/control"
        const val MIN_FREQUENCY = 100L // 最小间隔100ms (10Hz)
        const val MAX_FREQUENCY = 500L // 最大间隔500ms (2Hz)
    }
    
    /**
     * 初始化ViewModel
     */
    fun initialize(context: Context) {
        configManager = ConfigManager.getInstance(context)
        voiceRecorderManager = VoiceRecorderManager(context)

        // 初始化讯飞ASR客户端
        xunfeiAsrClient = XunfeiAsrClient(
            appId = configManager.getXunfeiAppId(),
            apiKey = configManager.getXunfeiApiKey(),
            apiSecret = configManager.getXunfeiApiSecret()
        )
        
        // 监听配置变化
        configManager.raspberryPiUrl
            .onEach { url ->
                _robotUrl.value = url
                Log.d(TAG, "机械狗URL已更新: $url")
            }
            .launchIn(viewModelScope)
        
        startConnectionMonitoring()
    }
    
    /**
     * 发送控制命令
     */
    fun sendCommand(command: String) {
        viewModelScope.launch {
            try {
                val timestamp = dateFormat.format(Date())
                val commandWithTime = "$command (${timestamp})"
                
                Log.d(TAG, "发送控制命令: $command")
                
                // 立即更新UI状态
                _lastCommand.value = commandWithTime
                addToHistory(commandWithTime)
                
                // 发送命令到机械狗
                val success = sendCommandToRobot(command)
                
                if (success) {
                    Log.d(TAG, "命令发送成功: $command")
                } else {
                    Log.e(TAG, "命令发送失败: $command")
                    // 可以在这里添加错误处理，比如重试或显示错误消息
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "发送命令异常: $command", e)
                addToHistory("错误: $command 发送失败")
            }
        }
    }
    
    /**
     * 实际发送命令到机械狗
     */
    private suspend fun sendCommandToRobot(command: String): Boolean {
        return try {
            val currentUrl = if (::configManager.isInitialized) {
                configManager.getCurrentRaspberryPiUrl()
            } else {
                _robotUrl.value
            }

            Log.d(TAG, "=== 发送命令到机械狗 ===")
            Log.d(TAG, "命令: $command")
            Log.d(TAG, "目标URL: $currentUrl")

            // 构建完整的API URL
            val fullUrl = "http://$currentUrl$CONTROL_PATH"
            Log.d(TAG, "完整URL: $fullUrl")

            // 构建命令数据
            val commandData = mapOf(
                "action" to command,
                "timestamp" to System.currentTimeMillis(),
                "source" to "manual_control"
            )

            Log.d(TAG, "命令数据: $commandData")

            // 使用NetworkManager发送实际的HTTP请求
            val result = networkManager.sendControlCommand(fullUrl, commandData)

            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "✓ 命令发送成功: $command")
                    Log.d(TAG, "服务器响应: ${result.data}")
                    true
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "✗ 命令发送失败: $command")
                    Log.e(TAG, "错误信息: ${result.message}")
                    false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "发送命令到机械狗异常", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            false
        }
    }
    
    /**
     * 更新机械狗地址
     */
    fun updateRobotUrl(url: String) {
        if (::configManager.isInitialized) {
            val success = configManager.updateRaspberryPiUrl(url)
            if (success) {
                Log.d(TAG, "机械狗地址更新成功: $url")
                // 重新检查连接
                checkConnection()
            } else {
                _connectionStatus.value = "无效的地址格式"
                Log.w(TAG, "机械狗地址更新失败: $url")
            }
        } else {
            Log.e(TAG, "ConfigManager未初始化")
        }
    }
    
    /**
     * 添加命令到历史记录
     */
    private fun addToHistory(command: String) {
        val currentHistory = _commandHistory.value.toMutableList()
        currentHistory.add(command)
        
        // 保持最近的20条记录
        if (currentHistory.size > 20) {
            currentHistory.removeAt(0)
        }
        
        _commandHistory.value = currentHistory
    }
    
    /**
     * 检查与机械狗的连接
     */
    private fun checkConnection() {
        viewModelScope.launch {
            try {
                _connectionStatus.value = "检查连接中..."
                
                val currentUrl = if (::configManager.isInitialized) {
                    configManager.getCurrentRaspberryPiUrl()
                } else {
                    _robotUrl.value
                }
                
                // 使用NetworkManager检查连接
                val connected = networkManager.checkConnection(currentUrl)
                
                _isConnected.value = connected
                _connectionStatus.value = if (connected) {
                    "已连接到机械狗"
                } else {
                    "无法连接到机械狗"
                }
                
                Log.d(TAG, "连接检查结果: $connected, URL: $currentUrl")
                
            } catch (e: Exception) {
                _isConnected.value = false
                _connectionStatus.value = "连接检查失败"
                Log.e(TAG, "连接检查异常", e)
            }
        }
    }
    
    /**
     * 开始连接监控
     */
    private fun startConnectionMonitoring() {
        connectionCheckJob = viewModelScope.launch {
            while (true) {
                checkConnection()
                delay(15000) // 每15秒检查一次连接
            }
        }
    }
    
    /**
     * 手动刷新连接
     */
    fun refreshConnection() {
        checkConnection()
    }
    
    /**
     * 紧急停止所有动作
     */
    fun emergencyStop() {
        viewModelScope.launch {
            repeat(3) { // 发送3次停止命令确保执行
                sendCommand("stop")
                delay(100)
            }
            Log.d(TAG, "执行紧急停止")
        }
    }
    
    /**
     * 清空命令历史
     */
    fun clearHistory() {
        _commandHistory.value = emptyList()
        Log.d(TAG, "清空命令历史")
    }

    /**
     * 设置命令发送频率
     * @param intervalMs 发送间隔（毫秒），范围在MIN_FREQUENCY到MAX_FREQUENCY之间
     */
    fun setCommandFrequency(intervalMs: Long) {
        val clampedInterval = intervalMs.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
        _commandFrequency.value = clampedInterval
        Log.d(TAG, "命令发送频率已设置为: ${clampedInterval}ms (${1000f/clampedInterval}Hz)")
    }

    /**
     * 获取当前命令发送频率
     */
    fun getCurrentFrequency(): Long = _commandFrequency.value
    
    /**
     * 获取连接状态信息
     */
    fun getConnectionInfo(): String {
        val currentUrl = if (::configManager.isInitialized) {
            configManager.getCurrentRaspberryPiUrl()
        } else {
            _robotUrl.value
        }
        
        return buildString {
            appendLine("机械狗地址: $currentUrl")
            appendLine("连接状态: ${if (_isConnected.value) "已连接" else "未连接"}")
            appendLine("最后命令: ${_lastCommand.value ?: "无"}")
            appendLine("命令历史: ${_commandHistory.value.size} 条")
        }
    }
    
    // =================================================================================
    // 语音识别相关方法
    // =================================================================================

    /**
     * 开始语音识别
     */
    fun startVoiceRecognition() {
        if (_isRecording.value) return
        Log.d(TAG, "开始语音识别流程...")
        _asrResult.value = "正在连接服务..."
        isFirstAudioFrame = true // 重置首帧标记
        xunfeiAsrClient.connect(this)
    }

    /**
     * 停止语音识别
     */
    fun stopVoiceRecognition() {
        if (!_isRecording.value) return
        Log.d(TAG, "停止语音识别流程...")
        voiceRecorderManager.stopRecording()
        xunfeiAsrClient.stopSending()
        _isRecording.value = false
    }

    // --- XunfeiAsrClient.AsrListener 实现 ---

    override fun onAsrReady() {
        Log.d(TAG, "ASR服务已就绪，开始录音...")
        _asrResult.value = "请开始说话..."
        val success = voiceRecorderManager.startRecording(this)
        if (success) {
            _isRecording.value = true
        } else {
            _asrResult.value = "录音启动失败"
        }
    }

    override fun onAsrResult(message: String) {
        _asrResult.value = message
    }

    override fun onAsrFinalResult(message: String) {
        _asrResult.value = "最终结果: $message"
        if (message.isNotBlank()) {
            sendCommand(message) // 将识别结果作为命令发送
        }
        _isRecording.value = false
    }

    override fun onAsrError(error: String) {
        Log.e(TAG, "ASR错误: $error")
        _asrResult.value = "错误: $error"
        if (_isRecording.value) {
            voiceRecorderManager.cancelRecording()
        }
        _isRecording.value = false
    }
    
    override fun onAsrClose() {
        Log.d(TAG, "ASR连接已关闭")
        if (_isRecording.value) {
            // 如果仍在录音，说明是异常关闭
            _isRecording.value = false
            voiceRecorderManager.cancelRecording()
            _asrResult.value = "连接意外断开"
        }
    }

    // --- VoiceRecorderManager.AudioStreamListener 实现 ---

    override fun onAudioStream(data: ByteArray) {
        // 将音频流发送到讯飞
        xunfeiAsrClient.sendAudio(data, isFirstFrame = isFirstAudioFrame)
        if (isFirstAudioFrame) {
            isFirstAudioFrame = false
        }
    }

    override fun onRecordingError(error: String) {
        Log.e(TAG, "录音错误: $error")
        onAsrError("录音失败: $error") // 将录音错误传递给ASR错误处理
    }


    override fun onCleared() {
        super.onCleared()
        connectionCheckJob?.cancel()
        networkManager.cleanup()
        xunfeiAsrClient.disconnect()
        if (_isRecording.value) {
            voiceRecorderManager.cancelRecording()
        }
        Log.d(TAG, "ControlViewModel已清理")
    }
}