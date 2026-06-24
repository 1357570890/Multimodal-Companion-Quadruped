package com.example.voicecontrolapp

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iflytek.cloud.SpeechError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel(), IFlytekAsrManager.AsrListener {

    private lateinit var voiceRecorderManager: VoiceRecorderManager
    private lateinit var configManager: ConfigManager
    private val networkManager = NetworkManager()
    private lateinit var iFlytekAsrManager: IFlytekAsrManager
    private lateinit var qwenApiManager: QwenApiManager
    private lateinit var context: Context

    // UI状态
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _isSending = mutableStateOf(false)
    val isSending: State<Boolean> = _isSending

    private val _statusMessage = mutableStateOf("准备就绪")
    val statusMessage: State<String> = _statusMessage

    private val _raspberryPiUrl = mutableStateOf("192.168.110.21:8080")
    val raspberryPiUrl: State<String> = _raspberryPiUrl

    private val _recognizedText = mutableStateOf("")
    val recognizedText: State<String> = _recognizedText

    private val _uploadProgress = mutableStateOf(0)
    val uploadProgress: State<Int> = _uploadProgress

    private val _hasRecordPermission = mutableStateOf(false)
    val hasRecordPermission: State<Boolean> = _hasRecordPermission

    private var connectionCheckJob: Job? = null
    private val recognizedTextBuilder = StringBuilder()
    private var finalResultJob: Job? = null
    private var isWaitingForFinalResult = false


    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * 初始化ViewModel
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        voiceRecorderManager = VoiceRecorderManager(context)
        configManager = ConfigManager.getInstance(context)
        iFlytekAsrManager = IFlytekAsrManager()
        qwenApiManager = QwenApiManager()

        DebugLogManager.addLog(LogLevel.INFO, TAG, "MainViewModel初始化开始")

        // 初始化科大讯飞ASR
        val initSuccess = iFlytekAsrManager.init(context, this)
        if (initSuccess) {
            DebugLogManager.addLog(LogLevel.INFO, TAG, "科大讯飞ASR引擎初始化成功")
        } else {
            DebugLogManager.addLog(LogLevel.ERROR, TAG, "科大讯飞ASR引擎初始化失败")
            _statusMessage.value = "ASR引擎初始化失败"
        }

        configManager.raspberryPiUrl
            .onEach { url ->
                _raspberryPiUrl.value = url
                Log.d(TAG, "树莓派URL已更新: $url")
            }
            .launchIn(viewModelScope)

        startConnectionMonitoring()
        DebugLogManager.addLog(LogLevel.INFO, TAG, "MainViewModel初始化完成")
    }

    override fun onResult(result: String, isLast: Boolean) {
        Log.d(TAG, "ASR结果: '$result', isLast=$isLast, 等待最终结果=$isWaitingForFinalResult")
        
        // 更新builder以保存最新的完整文本，用于发送
        if (result.isNotBlank()) {
            recognizedTextBuilder.clear()
            recognizedTextBuilder.append(result)
        }

        // UI应反映builder的内容
        viewModelScope.launch(Dispatchers.Main) {
            _recognizedText.value = recognizedTextBuilder.toString()
        }

        // 如果正在等待最终结果且收到了isLast=true，则处理最终结果
        if (isWaitingForFinalResult && isLast) {
            Log.d(TAG, "触发发送: 收到最终结果 '${recognizedTextBuilder.toString()}'")
            isWaitingForFinalResult = false
            finalResultJob?.cancel()
            
            viewModelScope.launch {
                processFinalResult()
            }
        } else if (isLast && _isRecording.value) {
            Log.d(TAG, "ASR段落结束，继续录音")
        } else if (isLast && !_isRecording.value && !isWaitingForFinalResult) {
            // ASR自动结束但用户没有手动停止录音的情况
            Log.d(TAG, "ASR自动结束，触发发送: '${recognizedTextBuilder.toString()}'")
            viewModelScope.launch {
                processFinalResult()
            }
        }
    }

    override fun onError(errorMsg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _statusMessage.value = "识别错误: $errorMsg"
            // 发生错误时强制停止录音
            _isRecording.value = false
            forceStopRecording() // 强制停止以清理资源
            Log.e(TAG, "ASR Error: $errorMsg")
        }
    }

    override fun onVolumeChanged(volume: Int) {
        // 可以在这里处理音量UI更新，如果需要的话
    }

    /**
     * 设置录音权限状态
     */
    fun setRecordPermission(granted: Boolean) {
        _hasRecordPermission.value = granted
        _statusMessage.value = if (granted) "已获得录音权限" else "需要录音权限"
    }

    /**
     * 开始录音和识别
     */
    /**
     * 切换录音状态
     */
    fun toggleRecording() {
        Log.d(TAG, "toggleRecording() 被调用，当前录音状态: ${_isRecording.value}")
        if (_isRecording.value) {
            Log.d(TAG, "停止录音")
            stopRecordingAndSend()
        } else {
            Log.d(TAG, "开始录音")
            startRecording()
        }
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording() 开始执行")
        if (!_hasRecordPermission.value) {
            Log.d(TAG, "没有录音权限")
            _statusMessage.value = "请先授予录音权限"
            return
        }

        // 立即更新UI状态（乐观更新）
        _isRecording.value = true
        _statusMessage.value = "请开始说话..."
        recognizedTextBuilder.clear()
        _recognizedText.value = ""
        Log.d(TAG, "UI状态已更新为录音中: ${_isRecording.value}")

        viewModelScope.launch(Dispatchers.IO) {
            val listener = object : VoiceRecorderManager.AudioStreamListener {
                override fun onAudioStream(data: ByteArray) {
                    iFlytekAsrManager.writeAudio(data, 0, data.size)
                }

                override fun onRecordingError(error: String) {
                    // 从IO线程切换回主线程报告错误
                    launch(Dispatchers.Main) {
                        onError("录音模块错误: $error")
                    }
                }
            }

            val success = voiceRecorderManager.startRecording(listener)
            Log.d(TAG, "录音启动结果: $success")
            if (success) {
                iFlytekAsrManager.startListening()
                Log.d(TAG, "ASR监听已启动")
            } else {
                // 如果启动失败，则恢复UI状态
                launch(Dispatchers.Main) {
                    _isRecording.value = false
                    _statusMessage.value = "录音启动失败"
                    Log.d(TAG, "录音启动失败，状态已恢复")
                }
            }
        }
    }

    private fun stopRecordingAndSend() {
        Log.d(TAG, "停止录音并发送, 当前文本: '${recognizedTextBuilder.toString()}'")
        
        // 立即更新UI状态，提供即时反馈
        _isRecording.value = false
        _statusMessage.value = "处理中，等待最终结果..."

        viewModelScope.launch(Dispatchers.IO) {
            voiceRecorderManager.stopRecording()
            
            // 设置等待最终结果的标志
            isWaitingForFinalResult = true
            Log.d(TAG, "等待最终结果标志已设置")
            
            // 启动1.5秒超时任务
            finalResultJob = launch {
                delay(1500) // 等待1.5秒
                Log.d(TAG, "超时(1.5秒)，使用当前结果: '${recognizedTextBuilder.toString()}'")
                isWaitingForFinalResult = false
                processFinalResult()
            }
        }
    }

    private suspend fun processFinalResult() {
        // 确保停止ASR监听
        iFlytekAsrManager.stopListening()
        
        // 获取并清理当前已识别的文本
        val rawText = recognizedTextBuilder.toString().trim()
        val cleanedText = rawText.removeSuffix("。").removeSuffix(".").removeSuffix("？").removeSuffix("?").removeSuffix("！").removeSuffix("!").trim()
        
        Log.d(TAG, "处理最终结果: '$rawText' -> '$cleanedText'")
        
        // 更新UI显示为清理后的文本
        withContext(Dispatchers.Main) {
            _recognizedText.value = cleanedText
        }
        
        if (cleanedText.isNotBlank()) {
            Log.d(TAG, "发送文本: '$cleanedText'")
            
            // 根据语音模式设置决定处理方式
            if (configManager.isLLMInferenceEnabled()) {
                Log.d(TAG, "LLM推断模式已启用，调用通义千问API")
                processWithLLMInference(cleanedText)
            } else {
                Log.d(TAG, "纯文本模式，直接发送识别结果")
                sendRecognizedText(cleanedText)
            }
        } else {
            Log.d(TAG, "没有识别到文本内容")
            withContext(Dispatchers.Main) {
                _statusMessage.value = "未识别到语音内容"
                delay(2000)
                _statusMessage.value = "准备就绪"
            }
        }
    }

    /**
     * 取消录音
     */
    fun cancelRecording() {
        if (!_isRecording.value) return
        forceStopRecording()
        _statusMessage.value = "录音已取消"
    }

    /**
     * 强制停止录音（用于紧急情况）
     */
    fun forceStopRecording() {
        voiceRecorderManager.cancelRecording()
        iFlytekAsrManager.cancel()
        _isRecording.value = false
        _recognizedText.value = ""
        recognizedTextBuilder.clear()
        Log.d(TAG, "录音已强制停止")
    }

    /**
     * 使用LLM推断处理语音文字
     */
    private suspend fun processWithLLMInference(text: String) {
        try {
            Log.d(TAG, "======= 开始LLM推断流程 =======")
            Log.d(TAG, "🎤 用户语音输入: '$text'")
            Log.d(TAG, "🚀 启动通义千问API调用...")
            
            withContext(Dispatchers.Main) {
                _statusMessage.value = "正在调用AI分析..."
            }
            
            // 添加调试日志记录
            DebugLogManager.addLog(
                LogLevel.INFO,
                TAG,
                "LLM推断开始 - 用户语音输入: $text\n开始调用通义千问API..."
            )
            
            val result = qwenApiManager.getInference(text)
            
            when (result) {
                is QwenApiManager.ApiResult.Success -> {
                    Log.d(TAG, "✅ 通义千问推断成功")
                    Log.d(TAG, "🤖 AI回应内容: '${result.content}'")
                    Log.d(TAG, "📤 准备发送AI回应到服务器...")
                    
                    // 添加成功日志
                    DebugLogManager.addLog(
                        LogLevel.INFO,
                        TAG,
                        "LLM推断成功 - 用户输入: $text\nAI回应: ${result.content}\n即将发送AI回应到服务器"
                    )
                    
                    // 更新UI显示LLM的推断结果
                    withContext(Dispatchers.Main) {
                        _recognizedText.value = "语音: $text\n\nAI回应: ${result.content}"
                        _statusMessage.value = "AI分析完成，正在发送..."
                    }
                    
                    // 发送LLM推断结果到原始接口
                    Log.d(TAG, "📡 即将发送AI回应到服务器: '${result.content}'")
                    try {
                        sendRecognizedText(result.content)
                        Log.d(TAG, "📡 AI回应发送调用完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ AI回应发送调用异常: ${e.message}", e)
                    }
                }
                is QwenApiManager.ApiResult.Error -> {
                    Log.e(TAG, "❌ 通义千问推断失败: ${result.message}")
                    
                    // 添加失败日志
                    DebugLogManager.addLog(
                        LogLevel.ERROR,
                        TAG,
                        "LLM推断失败 - 用户输入: $text\n错误信息: ${result.message}\n回退发送原始文本"
                    )
                    
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "AI分析失败，发送原始文本"
                        _recognizedText.value = "$text\n\n(AI分析失败: ${result.message})"
                    }
                    
                    // 如果LLM调用失败，发送原始文本
                    Log.d(TAG, "📡 回退发送原始文本: '$text'")
                    sendRecognizedText(text)
                }
            }
            
            Log.d(TAG, "======= LLM推断流程完成 =======")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM推断过程异常: ${e.javaClass.simpleName} - ${e.message}", e)
            
            // 添加异常日志
            DebugLogManager.addLog(
                LogLevel.ERROR,
                TAG,
                "LLM推断异常 - 用户输入: $text\n异常信息: ${e.message}\n回退发送原始文本",
                e
            )
            
            withContext(Dispatchers.Main) {
                _statusMessage.value = "AI分析异常，发送原始文本"
                _recognizedText.value = "$text\n\n(AI分析异常: ${e.message})"
            }
            
            // 异常情况下发送原始文本
            Log.d(TAG, "📡 异常回退发送原始文本: '$text'")
            sendRecognizedText(text)
        }
    }

    /**
     * 发送识别后的文本到树莓派
     */
    private suspend fun sendRecognizedText(text: String) {
        Log.d(TAG, "🔥 sendRecognizedText方法被调用，文本内容: '$text'")
        
        if (text.isBlank()) {
            Log.d(TAG, "❌ 识别结果为空，不发送")
            _statusMessage.value = "识别结果为空，不发送"
            return
        }
        
        try {
            _isSending.value = true
            _statusMessage.value = "正在发送指令..."
            
            val currentUrl = configManager.getCurrentRaspberryPiUrl()
            val isLLMMode = configManager.isLLMInferenceEnabled()
            
            Log.d(TAG, "======= 开始发送数据到服务器 =======")
            Log.d(TAG, "📤 发送内容: '$text'")
            Log.d(TAG, "🎯 目标服务器: $currentUrl")
            Log.d(TAG, "🤖 当前模式: ${if (isLLMMode) "AI推断模式" else "纯文本模式"}")
            Log.d(TAG, "📡 使用接口: /api/upload_voice")
            
            // 添加详细的发送日志
            DebugLogManager.addLog(
                LogLevel.INFO,
                TAG,
                "发送数据到服务器 - 发送内容: $text\n目标服务器: $currentUrl\n当前模式: ${if (isLLMMode) "AI推断模式" else "纯文本模式"}\n使用接口: /api/upload_voice"
            )

            val result = networkManager.sendControlCommand(text, currentUrl, isLLMMode)

            when (result) {
                is NetworkResult.Success -> {
                    _statusMessage.value = "指令发送成功！"
                    Log.d(TAG, "✅ 发送成功")
                    Log.d(TAG, "📨 服务器响应: ${result.data}")
                    
                    // 添加成功日志
                    DebugLogManager.addLog(
                        LogLevel.INFO,
                        TAG,
                        "数据发送成功 - 发送内容: $text\n服务器响应: ${result.data}\n${if (isLLMMode) "AI回应已成功发送到服务器" else "语音识别结果已发送到服务器"}"
                    )
                }
                is NetworkResult.Error -> {
                    _statusMessage.value = "指令发送失败: ${result.message}"
                    Log.e(TAG, "❌ 发送失败: ${result.message}")
                    
                    // 添加失败日志
                    DebugLogManager.addLog(
                        LogLevel.ERROR,
                        TAG,
                        "数据发送失败 - 发送内容: $text\n错误信息: ${result.message}\n目标服务器: $currentUrl"
                    )
                }
            }

        } catch (e: Exception) {
            _statusMessage.value = "发送异常: ${e.message}"
            Log.e(TAG, "❌ 发送异常: ${e.javaClass.simpleName} - ${e.message}", e)
            
            // 添加异常日志
            DebugLogManager.addLog(
                LogLevel.ERROR,
                TAG,
                "数据发送异常 - 发送内容: $text\n异常信息: ${e.message}",
                e
            )
        } finally {
            _isSending.value = false
            Log.d(TAG, "======= 数据发送流程完成 =======")
            
            delay(3000)
            if (!_isRecording.value && !_isSending.value) {
                _statusMessage.value = "准备就绪"
            }
        }
    }
    
    // --- 保留的其他方法 ---
    fun updateRaspberryPiUrl(url: String) {
        if (::configManager.isInitialized) {
            configManager.updateRaspberryPiUrl(url)
            checkConnection()
        }
    }

    private fun checkConnection() {
        viewModelScope.launch {
            val currentUrl = configManager.getCurrentRaspberryPiUrl()
            _isConnected.value = networkManager.checkConnection(currentUrl)
        }
    }

    private fun startConnectionMonitoring() {
        connectionCheckJob = viewModelScope.launch {
            while (true) {
                checkConnection()
                delay(10000)
            }
        }
    }

    fun refreshConnection() {
        checkConnection()
    }

    fun performNetworkDiagnosis() {
        viewModelScope.launch {
            val currentUrl = configManager.getCurrentRaspberryPiUrl()
            DebugLogManager.addLog(LogLevel.INFO, TAG, "开始执行网络诊断...")
            networkManager.networkDiagnosis(currentUrl)
            DebugLogManager.addLog(LogLevel.INFO, TAG, "网络诊断完成")
        }
    }
    
    /**
     * 获取当前语音模式
     */
    fun getVoiceMode(): String {
        return configManager.getVoiceMode()
    }
    
    /**
     * 更新语音模式
     */
    fun updateVoiceMode(mode: String) {
        val success = configManager.setVoiceMode(mode)
        if (success) {
            Log.d(TAG, "语音模式已更新为: $mode")
            val modeText = when (mode) {
                ConfigManager.VOICE_MODE_TEXT_ONLY -> "纯文本模式"
                ConfigManager.VOICE_MODE_LLM_INFERENCE -> "AI智能推断模式"
                else -> "未知模式"
            }
            _statusMessage.value = "已切换到$modeText"
        } else {
            Log.e(TAG, "语音模式更新失败: $mode")
            _statusMessage.value = "语音模式更新失败"
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorderManager.release()
        iFlytekAsrManager.release()
        networkManager.cleanup()
        connectionCheckJob?.cancel()
        finalResultJob?.cancel()
        Log.d(TAG, "ViewModel资源已清理")
    }
}
