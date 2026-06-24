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

class CameraViewModel : ViewModel() {
    
    private lateinit var configManager: ConfigManager
    private val networkManager = NetworkManager()
    
    // UI状态
    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected
    
    private val _isStreaming = mutableStateOf(false)
    val isStreaming: State<Boolean> = _isStreaming
    private val _cameraUrl = mutableStateOf("http://192.168.110.21:8080/remote_camera_feed")
    val cameraUrl: State<String> = _cameraUrl
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage
    
    private var connectionCheckJob: Job? = null
    private var streamJob: Job? = null
    private var retryJob: Job? = null

    // 重连配置
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 5000L // 5秒

    companion object {
        private const val TAG = "CameraViewModel"
    }
    
    /**
     * 初始化ViewModel
     */
    fun initialize(context: Context) {
        configManager = ConfigManager.getInstance(context)
        
        // 监听配置变化
        configManager.cameraUrl
            .onEach { url ->
                _cameraUrl.value = url
                Log.d(TAG, "摄像头URL已更新: $url")
            }
            .launchIn(viewModelScope)
        
        configManager.raspberryPiUrl
            .onEach { _ ->
                // 当树莓派地址变化时，重新检查连接
                checkConnection()
            }
            .launchIn(viewModelScope)
        
        startConnectionMonitoring()
    }
    
    /**
     * 开始直播
     */
    fun startStream() {
        val currentUrl = if (::configManager.isInitialized) {
            configManager.getCurrentCameraUrl()
        } else {
            _cameraUrl.value
        }

        if (currentUrl.isEmpty()) {
            _errorMessage.value = "请先设置摄像头地址"
            return
        }

        // 重置重试计数
        retryCount = 0
        startStreamWithRetry(currentUrl)
    }

    /**
     * 带重试机制的开始直播
     */
    private fun startStreamWithRetry(url: String) {
        streamJob?.cancel()
        retryJob?.cancel()

        streamJob = viewModelScope.launch {
            try {
                _isStreaming.value = true
                _errorMessage.value = null
                Log.d(TAG, "开始摄像头直播: $url (重试次数: $retryCount)")

                // 检查流是否可用
                checkStreamAvailability()

                Log.d(TAG, "摄像头直播启动成功")

            } catch (e: Exception) {
                Log.e(TAG, "启动直播失败", e)
                handleStreamError(e, url)
            }
        }
    }

    /**
     * 处理流错误并决定是否重试
     */
    private fun handleStreamError(error: Exception, url: String) {
        val errorMsg = "启动直播失败: ${error.message}"

        if (retryCount < maxRetries) {
            retryCount++
            Log.w(TAG, "$errorMsg，将在${retryDelayMs/1000}秒后重试 ($retryCount/$maxRetries)")
            _errorMessage.value = "$errorMsg (正在重试 $retryCount/$maxRetries)"

            // 延迟重试
            retryJob = viewModelScope.launch {
                delay(retryDelayMs)
                if (_isStreaming.value) { // 确保用户没有手动停止
                    startStreamWithRetry(url)
                }
            }
        } else {
            Log.e(TAG, "$errorMsg，已达到最大重试次数")
            _errorMessage.value = "$errorMsg (已重试 $maxRetries 次)"
            _isStreaming.value = false
        }
    }
    
    /**
     * 停止直播
     */
    fun stopStream() {
        streamJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        _isStreaming.value = false
        _errorMessage.value = null
        Log.d(TAG, "停止摄像头直播")
    }
    
    /**
     * 刷新摄像头连接
     */
    fun refreshCamera() {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                Log.d(TAG, "刷新摄像头连接")
                
                // 重新检查连接
                checkConnection()
                
                // 如果正在直播，重新启动
                if (_isStreaming.value) {
                    stopStream()
                    delay(1000)
                    startStream()
                }
                
            } catch (e: Exception) {
                _errorMessage.value = "刷新失败: ${e.message}"
                Log.e(TAG, "刷新摄像头失败", e)
            }
        }
    }
    
    /**
     * 更新摄像头地址
     */
    fun updateCameraUrl(url: String) {
        if (::configManager.isInitialized) {
            val wasStreaming = _isStreaming.value
            
            // 如果正在直播，先停止
            if (wasStreaming) {
                stopStream()
            }
            
            val success = configManager.updateCameraUrl(url)
            if (success) {
                _errorMessage.value = null
                Log.d(TAG, "摄像头地址更新成功: $url")
                
                // 重新检查连接
                checkConnection()
                
                // 如果之前在直播，重新开始
                if (wasStreaming) {
                    viewModelScope.launch {
                        delay(500)
                        startStream()
                    }
                }
            } else {
                _errorMessage.value = "无效的摄像头地址格式"
                Log.w(TAG, "摄像头地址更新失败: $url")
            }
        } else {
            Log.e(TAG, "ConfigManager未初始化")
        }
    }
    
    /**
     * 检查流的可用性
     */
    private suspend fun checkStreamAvailability() {
        try {
            val currentUrl = if (::configManager.isInitialized) {
                configManager.getCurrentCameraUrl()
            } else {
                _cameraUrl.value
            }

            Log.d(TAG, "=== 检查摄像头流可用性 ===")
            Log.d(TAG, "URL: $currentUrl")
            Log.d(TAG, "URL长度: ${currentUrl.length}")
            Log.d(TAG, "ConfigManager已初始化: ${::configManager.isInitialized}")

            if (currentUrl.isEmpty()) {
                throw Exception("摄像头URL为空")
            }

            if (!currentUrl.startsWith("http")) {
                throw Exception("无效的URL格式，必须以http开头")
            }

            Log.d(TAG, "开始网络可用性检查...")

            // 实际检查MJPEG流是否可访问
            val isAvailable = networkManager.checkMjpegStreamAvailability(currentUrl)

            Log.d(TAG, "网络检查结果: $isAvailable")

            if (isAvailable) {
                Log.d(TAG, "✓ 摄像头流连接成功")
                _errorMessage.value = null
            } else {
                throw Exception("摄像头流不可访问 - 网络检查失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ 摄像头流检查失败", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            _errorMessage.value = "无法连接到摄像头: ${e.message}"
            _isStreaming.value = false
            throw e
        }
    }
    
    /**
     * 检查与机械狗的连接
     */
    private fun checkConnection() {
        viewModelScope.launch {
            try {
                val baseUrl = if (::configManager.isInitialized) {
                    configManager.getCurrentRaspberryPiUrl()
                } else {
                    extractBaseUrl(_cameraUrl.value)
                }
                
                if (baseUrl.isNotEmpty()) {
                    Log.d(TAG, "检查机械狗连接: $baseUrl")
                    DebugLogManager.addLog(LogLevel.INFO, TAG, "检查摄像头连接: $baseUrl")
                    
                    val isConnected = networkManager.checkConnection(baseUrl)
                    _isConnected.value = isConnected
                    
                    if (isConnected) {
                        Log.d(TAG, "机械狗连接正常: $baseUrl")
                        DebugLogManager.addLog(LogLevel.INFO, TAG, "摄像头连接成功")
                    } else {
                        Log.w(TAG, "机械狗连接失败: $baseUrl")
                        DebugLogManager.addLog(LogLevel.WARN, TAG, "摄像头连接失败")
                    }
                } else {
                    _isConnected.value = false
                    Log.w(TAG, "无效的机械狗地址")
                    DebugLogManager.addLog(LogLevel.WARN, TAG, "无效的摄像头地址")
                }
                
            } catch (e: Exception) {
                _isConnected.value = false
                Log.e(TAG, "检查连接失败", e)
                DebugLogManager.addLog(LogLevel.ERROR, TAG, "检查摄像头连接失败", e)
            }
        }
    }
    
    /**
     * 从摄像头URL提取基础URL
     */
    private fun extractBaseUrl(cameraUrl: String): String {
        return try {
            if (cameraUrl.startsWith("http")) {
                val parts = cameraUrl.split("/")
                if (parts.size >= 3) {
                    "${parts[0]}//${parts[2]}"
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 开始连接监控
     */
    private fun startConnectionMonitoring() {
        connectionCheckJob = viewModelScope.launch {
            while (true) {
                if (!_isStreaming.value) {
                    checkConnection()
                }
                delay(10000) // 每10秒检查一次
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopStream()
        connectionCheckJob?.cancel()
        Log.d(TAG, "CameraViewModel已清理")
    }
}