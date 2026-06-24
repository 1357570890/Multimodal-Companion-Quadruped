package com.example.voicecontrolapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用配置管理器
 * 负责管理和同步应用的配置信息，包括IP地址等设置
 */
class ConfigManager private constructor(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 树莓派URL状态流
    private val _raspberryPiUrl = MutableStateFlow(getDefaultRaspberryPiUrl())
    val raspberryPiUrl: StateFlow<String> = _raspberryPiUrl.asStateFlow()
    
    // 摄像头URL状态流
    private val _cameraUrl = MutableStateFlow(getDefaultCameraUrl())
    val cameraUrl: StateFlow<String> = _cameraUrl.asStateFlow()
    
    companion object {
        private const val TAG = "ConfigManager"
        private const val PREFS_NAME = "voice_control_app_config"
        private const val KEY_RASPBERRY_PI_URL = "raspberry_pi_url"
        private const val KEY_CAMERA_URL = "camera_url"
        private const val KEY_XF_APP_ID = "xf_app_id"
        private const val KEY_XF_API_KEY = "xf_api_key"
        private const val KEY_XF_API_SECRET = "xf_api_secret"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val DEFAULT_RASPBERRY_PI_URL = "192.168.110.21:5000"
        private const val DEFAULT_CAMERA_URL = "http://192.168.110.21:8080/remote_camera_feed"
        
        // 语音模式常量
        const val VOICE_MODE_TEXT_ONLY = "text_only"
        const val VOICE_MODE_LLM_INFERENCE = "llm_inference"
        private const val DEFAULT_VOICE_MODE = VOICE_MODE_TEXT_ONLY
        // 讯飞开放平台应用凭证
        private const val DEFAULT_XF_APP_ID = ""
        private const val DEFAULT_XF_API_KEY = ""
        private const val DEFAULT_XF_API_SECRET = ""
        
        @Volatile
        private var INSTANCE: ConfigManager? = null
        
        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    init {
        loadConfiguration()
        Log.d(TAG, "ConfigManager初始化完成")
    }
    
    /**
     * 加载配置
     */
    private fun loadConfiguration() {
        try {
            val savedRaspberryPiUrl = sharedPreferences.getString(KEY_RASPBERRY_PI_URL, DEFAULT_RASPBERRY_PI_URL) ?: DEFAULT_RASPBERRY_PI_URL
            val savedCameraUrl = sharedPreferences.getString(KEY_CAMERA_URL, DEFAULT_CAMERA_URL) ?: DEFAULT_CAMERA_URL
            
            _raspberryPiUrl.value = savedRaspberryPiUrl
            _cameraUrl.value = savedCameraUrl
            
            Log.d(TAG, "配置加载完成 - 树莓派URL: $savedRaspberryPiUrl, 摄像头URL: $savedCameraUrl")
        } catch (e: Exception) {
            Log.e(TAG, "加载配置失败", e)
            resetToDefaults()
        }
    }

    /**
     * 获取讯飞AppId
     */
    fun getXunfeiAppId(): String {
        return sharedPreferences.getString(KEY_XF_APP_ID, DEFAULT_XF_APP_ID) ?: DEFAULT_XF_APP_ID
    }

    /**
     * 获取讯飞ApiKey
     */
    fun getXunfeiApiKey(): String {
        return sharedPreferences.getString(KEY_XF_API_KEY, DEFAULT_XF_API_KEY) ?: DEFAULT_XF_API_KEY
    }

    /**
     * 获取讯飞ApiSecret
     */
    fun getXunfeiApiSecret(): String {
        return sharedPreferences.getString(KEY_XF_API_SECRET, DEFAULT_XF_API_SECRET) ?: DEFAULT_XF_API_SECRET
    }
    
    /**
     * 获取当前语音模式
     */
    fun getVoiceMode(): String {
        return sharedPreferences.getString(KEY_VOICE_MODE, DEFAULT_VOICE_MODE) ?: DEFAULT_VOICE_MODE
    }
    
    /**
     * 设置语音模式
     */
    fun setVoiceMode(mode: String): Boolean {
        return try {
            if (mode != VOICE_MODE_TEXT_ONLY && mode != VOICE_MODE_LLM_INFERENCE) {
                Log.w(TAG, "无效的语音模式: $mode")
                return false
            }
            
            sharedPreferences.edit()
                .putString(KEY_VOICE_MODE, mode)
                .apply()
            
            Log.d(TAG, "语音模式已更新: $mode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新语音模式失败", e)
            false
        }
    }
    
    /**
     * 检查是否启用LLM推断模式
     */
    fun isLLMInferenceEnabled(): Boolean {
        return getVoiceMode() == VOICE_MODE_LLM_INFERENCE
    }
    
    /**
     * 更新树莓派URL
     */
    fun updateRaspberryPiUrl(url: String): Boolean {
        return try {
            val cleanUrl = url.trim()
            if (cleanUrl.isEmpty()) {
                Log.w(TAG, "尝试设置空的树莓派URL")
                return false
            }
            
            // 保存到SharedPreferences
            sharedPreferences.edit()
                .putString(KEY_RASPBERRY_PI_URL, cleanUrl)
                .apply()
            
            // 更新状态流
            _raspberryPiUrl.value = cleanUrl
            
            // 自动更新摄像头URL（如果使用默认格式）
            if (_cameraUrl.value == generateDefaultCameraUrl(_raspberryPiUrl.value)) {
                updateCameraUrlInternal(generateDefaultCameraUrl(cleanUrl))
            }
            
            Log.d(TAG, "树莓派URL已更新: $cleanUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新树莓派URL失败", e)
            false
        }
    }
    
    /**
     * 更新摄像头URL
     */
    fun updateCameraUrl(url: String): Boolean {
        return try {
            val cleanUrl = url.trim()
            if (cleanUrl.isEmpty()) {
                Log.w(TAG, "尝试设置空的摄像头URL")
                return false
            }
            
            updateCameraUrlInternal(cleanUrl)
            Log.d(TAG, "摄像头URL已更新: $cleanUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新摄像头URL失败", e)
            false
        }
    }
    
    /**
     * 内部更新摄像头URL方法
     */
    private fun updateCameraUrlInternal(url: String) {
        // 保存到SharedPreferences
        sharedPreferences.edit()
            .putString(KEY_CAMERA_URL, url)
            .apply()
        
        // 更新状态流
        _cameraUrl.value = url
    }
    
    /**
     * 获取当前树莓派URL
     */
    fun getCurrentRaspberryPiUrl(): String = _raspberryPiUrl.value
    
    /**
     * 获取当前摄像头URL
     */
    fun getCurrentCameraUrl(): String = _cameraUrl.value
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        try {
            sharedPreferences.edit()
                .putString(KEY_RASPBERRY_PI_URL, DEFAULT_RASPBERRY_PI_URL)
                .putString(KEY_CAMERA_URL, DEFAULT_CAMERA_URL)
                .apply()
            
            _raspberryPiUrl.value = DEFAULT_RASPBERRY_PI_URL
            _cameraUrl.value = DEFAULT_CAMERA_URL
            
            Log.d(TAG, "配置已重置为默认值")
        } catch (e: Exception) {
            Log.e(TAG, "重置配置失败", e)
        }
    }
    
    /**
     * 验证URL格式
     */
    fun validateUrl(url: String): Boolean {
        if (url.trim().isEmpty()) return false
        
        return try {
            // 简单的URL验证
            val cleanUrl = url.trim()
            when {
                cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> true
                cleanUrl.contains(":") && !cleanUrl.contains("://") -> {
                    // IP:Port格式
                    val parts = cleanUrl.split(":")
                    parts.size == 2 && parts[1].toIntOrNull() != null
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取默认树莓派URL
     */
    private fun getDefaultRaspberryPiUrl(): String {
        return sharedPreferences.getString(KEY_RASPBERRY_PI_URL, DEFAULT_RASPBERRY_PI_URL) ?: DEFAULT_RASPBERRY_PI_URL
    }
    
    /**
     * 获取默认摄像头URL
     */
    private fun getDefaultCameraUrl(): String {
        return sharedPreferences.getString(KEY_CAMERA_URL, DEFAULT_CAMERA_URL) ?: DEFAULT_CAMERA_URL
    }
    
    /**
     * 根据树莓派URL生成默认的摄像头URL
     */
    private fun generateDefaultCameraUrl(raspberryPiUrl: String): String {
        return if (raspberryPiUrl.startsWith("http")) {
            "$raspberryPiUrl/remote_camera_feed"
        } else {
            "http://$raspberryPiUrl/remote_camera_feed"
        }
    }
    
    /**
     * 获取完整的配置信息
     */
    fun getConfigInfo(): String {
        return buildString {
            appendLine("=== 应用配置信息 ===")
            appendLine("树莓派地址: ${getCurrentRaspberryPiUrl()}")
            appendLine("摄像头地址: ${getCurrentCameraUrl()}")
            appendLine("配置文件: $PREFS_NAME")
            appendLine("==================")
        }
    }
    
    /**
     * 导出配置
     */
    fun exportConfig(): Map<String, String> {
        return mapOf(
            "raspberry_pi_url" to getCurrentRaspberryPiUrl(),
            "camera_url" to getCurrentCameraUrl(),
            "export_time" to System.currentTimeMillis().toString()
        )
    }
    
    /**
     * 导入配置
     */
    fun importConfig(config: Map<String, String>): Boolean {
        return try {
            config["raspberry_pi_url"]?.let { updateRaspberryPiUrl(it) }
            config["camera_url"]?.let { updateCameraUrl(it) }
            Log.d(TAG, "配置导入成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "配置导入失败", e)
            false
        }
    }
}