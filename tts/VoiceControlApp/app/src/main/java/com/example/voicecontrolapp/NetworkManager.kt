package com.example.voicecontrolapp

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkManager {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d(TAG, "发送请求: ${request.url}")
            val response = chain.proceed(request)
            Log.d(TAG, "响应状态: ${response.code}")
            response
        }
        .build()
    
    companion object {
        private const val TAG = "NetworkManager"
        private const val UPLOAD_PATH = "/api/upload_voice"
        private const val PING_PATH = "/api/health"
        private const val TEXT_MODE_PATH = "/api/text_mode" // 纯文本模式路径
        private const val AI_MODE_PATH = "/api/ai_mode" // AI推断模式路径
    }
    
    /**
     * 检查与树莓派的连接状态
     */
    suspend fun checkConnection(raspberryPiUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = formatUrl(raspberryPiUrl, PING_PATH)
            Log.d(TAG, "开始连接检查，URL: $url")
            DebugLogManager.addLog(LogLevel.INFO, TAG, "开始连接检查: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "VoiceControlApp/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            val isConnected = response.isSuccessful
            val responseBody = response.body?.string()
            
            Log.d(TAG, "连接检查结果: $isConnected, 状态码: ${response.code}, URL: $url")
            if (responseBody != null) {
                Log.d(TAG, "响应内容: $responseBody")
            }
            
            // 记录详细的网络日志
            DebugLogManager.addNetworkLog(
                method = "GET",
                url = url,
                statusCode = response.code,
                response = responseBody,
                error = if (isConnected) null else "HTTP ${response.code}"
            )
            
            // 记录连接测试结果
            DebugLogManager.addConnectionTestLog(
                url = raspberryPiUrl,
                success = isConnected,
                details = if (isConnected) {
                    "HTTP ${response.code} - 响应: ${responseBody?.take(100) ?: "无响应内容"}"
                } else {
                    "HTTP ${response.code} - 连接失败"
                }
            )
            
            response.close()
            return@withContext isConnected
            
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "连接被拒绝或无法连接到服务器: $raspberryPiUrl", e)
            DebugLogManager.addNetworkLog(
                method = "GET",
                url = formatUrl(raspberryPiUrl, PING_PATH),
                error = "连接被拒绝: ${e.message}"
            )
            DebugLogManager.addConnectionTestLog(
                url = raspberryPiUrl,
                success = false,
                details = "连接被拒绝 - 可能原因: 1)服务器未启动 2)端口不正确 3)防火墙阻止"
            )
            return@withContext false
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "无法解析主机名: $raspberryPiUrl", e)
            DebugLogManager.addNetworkLog(
                method = "GET",
                url = formatUrl(raspberryPiUrl, PING_PATH),
                error = "无法解析主机名: ${e.message}"
            )
            DebugLogManager.addConnectionTestLog(
                url = raspberryPiUrl,
                success = false,
                details = "无法解析主机名 - 请检查IP地址格式是否正确"
            )
            return@withContext false
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "连接超时: $raspberryPiUrl", e)
            DebugLogManager.addNetworkLog(
                method = "GET",
                url = formatUrl(raspberryPiUrl, PING_PATH),
                error = "连接超时: ${e.message}"
            )
            DebugLogManager.addConnectionTestLog(
                url = raspberryPiUrl,
                success = false,
                details = "连接超时 - 可能原因: 1)网络不通 2)服务器响应慢 3)防火墙阻止"
            )
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "连接检查失败: $raspberryPiUrl", e)
            DebugLogManager.addNetworkLog(
                method = "GET",
                url = formatUrl(raspberryPiUrl, PING_PATH),
                error = "未知错误: ${e.message}"
            )
            DebugLogManager.addConnectionTestLog(
                url = raspberryPiUrl,
                success = false,
                details = "未知错误: ${e.javaClass.simpleName} - ${e.message}"
            )
            return@withContext false
        }
    }
    
    /**
     * 发送语音文件到树莓派
     */
    suspend fun sendVoiceToRaspberryPi(
        audioFile: File, 
        raspberryPiUrl: String,
        onProgress: ((Int) -> Unit)? = null
    ): NetworkResult = withContext(Dispatchers.IO) {
        
        try {
            if (!audioFile.exists()) {
                Log.e(TAG, "音频文件不存在: ${audioFile.absolutePath}")
                return@withContext NetworkResult.Error("音频文件不存在")
            }
            
            val url = formatUrl(raspberryPiUrl, UPLOAD_PATH)
            Log.d(TAG, "开始上传音频文件到: $url")
            Log.d(TAG, "文件大小: ${audioFile.length()} bytes")
            
            // 创建文件请求体
            val fileBody = audioFile.asRequestBody("audio/wav".toMediaType())
            
            // 创建多部分表单数据（简化版本，不包含进度监听）
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "voice",
                    audioFile.name,
                    fileBody
                )
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .build()
            
            // 模拟进度更新
            onProgress?.invoke(50)
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            return@withContext if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "上传成功，响应: $responseBody")
                response.close()
                NetworkResult.Success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: "未知错误"
                Log.e(TAG, "上传失败，状态码: ${response.code}, 错误: $errorBody")
                response.close()
                NetworkResult.Error("上传失败: ${response.code} - $errorBody")
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "网络连接失败", e)
            return@withContext NetworkResult.Error("网络连接失败: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "上传文件时发生未知错误", e)
            return@withContext NetworkResult.Error("上传失败: ${e.message}")
        }
    }

    /**
     * 发送文本控制命令到树莓派
     */
    suspend fun sendControlCommand(
        text: String,
        raspberryPiUrl: String,
        isAIMode: Boolean = false
    ): NetworkResult = withContext(Dispatchers.IO) {
        try {
            // 根据模式选择不同的端点
            val endpoint = if (isAIMode) AI_MODE_PATH else TEXT_MODE_PATH
            val url = formatUrl(raspberryPiUrl, endpoint)
            
            Log.d(TAG, "🚀 NetworkManager开始发送数据")
            Log.d(TAG, "🎯 发送模式: ${if (isAIMode) "AI推断模式" else "纯文本模式"}")
            Log.d(TAG, "发送内容: '$text' 到 $url")

            // 创建JSON（使用command字段保持兼容性）
            val commandData = mapOf("command" to text)
            val jsonData = Gson().toJson(commandData)
            Log.d(TAG, "📦 构建JSON数据: $jsonData")
            
            val requestBody = jsonData.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "VoiceControlApp/1.0")
                .addHeader("X-Mode", if (isAIMode) "AI" else "TEXT") // 添加模式标识头
                .build()

            Log.d(TAG, "📡 执行HTTP请求...")
            val response = client.newCall(request).execute()
            Log.d(TAG, "📨 收到HTTP响应，状态码: ${response.code}")

            return@withContext if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "✅ 数据发送成功，响应: $responseBody")
                response.close()
                NetworkResult.Success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: "未知错误"
                Log.e(TAG, "❌ 数据发送失败: ${response.code} - $errorBody")
                response.close()
                NetworkResult.Error("发送失败: ${response.code} - $errorBody")
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 发送数据异常: ${e.javaClass.simpleName} - ${e.message}")
            Log.e(TAG, "异常详情: ", e)
            return@withContext NetworkResult.Error("发送失败: ${e.message}")
        }
    }
    
    /**
     * 发送通用的JSON数据对象作为控制命令
     */
    suspend fun sendControlCommand(
        url: String,
        data: Map<String, @JvmSuppressWildcards Any>
    ): NetworkResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "发送JSON命令: $data 到 $url")

            val jsonData = Gson().toJson(data)
            val requestBody = jsonData.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url) // The full URL is passed directly
                .post(requestBody)
                .addHeader("User-Agent", "VoiceControlApp/1.0")
                .build()

            val response = client.newCall(request).execute()

            return@withContext if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "JSON命令发送成功, 响应: $responseBody")
                response.close()
                NetworkResult.Success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: "未知错误"
                Log.e(TAG, "JSON命令发送失败, 状态码: ${response.code}, 错误: $errorBody")
                response.close()
                NetworkResult.Error("发送失败: ${response.code} - $errorBody")
            }

        } catch (e: Exception) {
            Log.e(TAG, "发送JSON命令时发生错误", e)
            return@withContext NetworkResult.Error("发送失败: ${e.message}")
        }
    }
    
    /**
     * 格式化URL
     */
    private fun formatUrl(baseUrl: String, path: String): String {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        
        return if (cleanBaseUrl.startsWith("http://") || cleanBaseUrl.startsWith("https://")) {
            "$cleanBaseUrl/$cleanPath"
        } else {
            "http://$cleanBaseUrl/$cleanPath"
        }
    }
    
    /**
     * 网络诊断
     */
    suspend fun networkDiagnosis(raspberryPiUrl: String): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        
        try {
            report.appendLine("=== 网络诊断报告 ===")
            report.appendLine("目标地址: $raspberryPiUrl")
            report.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            report.appendLine()
            
            // 1. URL格式检查
            val formattedUrl = formatUrl(raspberryPiUrl, PING_PATH)
            report.appendLine("1. URL格式检查")
            report.appendLine("   原始地址: $raspberryPiUrl")
            report.appendLine("   格式化后: $formattedUrl")
            
            try {
                val uri = java.net.URI(formattedUrl)
                report.appendLine("   协议: ${uri.scheme}")
                report.appendLine("   主机: ${uri.host}")
                report.appendLine("   端口: ${uri.port}")
                report.appendLine("   ✓ URL格式正确")
            } catch (e: Exception) {
                report.appendLine("   ✗ URL格式错误: ${e.message}")
                DebugLogManager.addLog(LogLevel.ERROR, TAG, "URL格式错误: ${e.message}")
                return@withContext report.toString()
            }
            report.appendLine()
            
            // 2. 网络连接测试
            report.appendLine("2. 网络连接测试")
            val startTime = System.currentTimeMillis()
            
            try {
                val request = Request.Builder()
                    .url(formattedUrl)
                    .get()
                    .addHeader("User-Agent", "VoiceControlApp/1.0")
                    .build()
                
                val response = client.newCall(request).execute()
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                
                report.appendLine("   状态码: ${response.code}")
                report.appendLine("   响应时间: ${responseTime}ms")
                report.appendLine("   协议: ${response.protocol}")
                
                response.headers.forEach { (name, value) ->
                    report.appendLine("   响应头 $name: $value")
                }
                
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    report.appendLine("   响应内容: ${responseBody.take(200)}")
                    if (responseBody.length > 200) {
                        report.appendLine("   (内容已截断...)")
                    }
                }
                
                if (response.isSuccessful) {
                    report.appendLine("   ✓ 连接成功")
                } else {
                    report.appendLine("   ✗ 连接失败 (HTTP ${response.code})")
                }
                
                response.close()
                
            } catch (e: java.net.ConnectException) {
                report.appendLine("   ✗ 连接被拒绝")
                report.appendLine("   错误: ${e.message}")
                report.appendLine("   可能原因:")
                report.appendLine("     - 服务器未启动")
                report.appendLine("     - 端口号错误")
                report.appendLine("     - 防火墙阻止连接")
            } catch (e: java.net.UnknownHostException) {
                report.appendLine("   ✗ 无法解析主机名")
                report.appendLine("   错误: ${e.message}")
                report.appendLine("   可能原因:")
                report.appendLine("     - IP地址格式错误")
                report.appendLine("     - 主机名不存在")
                report.appendLine("     - DNS解析失败")
            } catch (e: java.net.SocketTimeoutException) {
                report.appendLine("   ✗ 连接超时")
                report.appendLine("   错误: ${e.message}")
                report.appendLine("   可能原因:")
                report.appendLine("     - 网络连接不稳定")
                report.appendLine("     - 服务器响应慢")
                report.appendLine("     - 防火墙延迟")
            } catch (e: Exception) {
                report.appendLine("   ✗ 未知错误")
                report.appendLine("   错误类型: ${e.javaClass.simpleName}")
                report.appendLine("   错误信息: ${e.message}")
            }
            
            report.appendLine()
            report.appendLine("=== 诊断完成 ===")
            
        } catch (e: Exception) {
            report.appendLine("诊断过程中发生错误: ${e.message}")
            DebugLogManager.addLog(LogLevel.ERROR, TAG, "网络诊断失败", e)
        }
        
        val finalReport = report.toString()
        DebugLogManager.addLog(LogLevel.INFO, TAG, "网络诊断报告:\n$finalReport")
        return@withContext finalReport
    }
    
    /**
     * 检查MJPEG流的可用性
     */
    suspend fun checkMjpegStreamAvailability(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== 检查MJPEG流可用性 ===")
            Log.d(TAG, "URL: $streamUrl")

            val request = Request.Builder()
                .url(streamUrl)
                .head() // 使用HEAD请求减少数据传输
                .addHeader("User-Agent", "VoiceControlApp/1.0")
                .addHeader("Accept", "multipart/x-mixed-replace,image/jpeg,*/*")
                .build()

            Log.d(TAG, "构建的请求:")
            Log.d(TAG, "  方法: ${request.method}")
            Log.d(TAG, "  URL: ${request.url}")
            request.headers.forEach { (name, value) ->
                Log.d(TAG, "  请求头 $name: $value")
            }

            // 创建一个短超时的客户端用于快速检查
            val quickClient = client.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            Log.d(TAG, "发送HEAD请求...")
            val response = quickClient.newCall(request).execute()

            Log.d(TAG, "=== 响应信息 ===")
            Log.d(TAG, "状态码: ${response.code}")
            Log.d(TAG, "状态消息: ${response.message}")
            Log.d(TAG, "协议: ${response.protocol}")

            response.headers.forEach { (name, value) ->
                Log.d(TAG, "响应头 $name: $value")
            }

            val isAvailable = response.isSuccessful
            Log.d(TAG, "HTTP请求成功: $isAvailable")

            // 检查Content-Type是否为MJPEG相关
            val contentType = response.header("Content-Type")
            Log.d(TAG, "Content-Type: $contentType")

            if (isAvailable && contentType != null) {
                val isMjpegType = contentType.contains("multipart/x-mixed-replace") ||
                                 contentType.contains("image/jpeg")
                Log.d(TAG, "是MJPEG类型: $isMjpegType")

                if (!isMjpegType) {
                    Log.w(TAG, "警告: Content-Type不是MJPEG格式")
                }

                response.close()
                return@withContext isMjpegType
            }

            if (!isAvailable) {
                Log.e(TAG, "HTTP请求失败: ${response.code} - ${response.message}")
            }

            response.close()
            return@withContext isAvailable

        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "MJPEG流连接被拒绝: $streamUrl", e)
            Log.e(TAG, "可能原因: 服务器未启动或端口错误")
            return@withContext false
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "MJPEG流连接超时: $streamUrl", e)
            Log.e(TAG, "可能原因: 网络不通或服务器响应慢")
            return@withContext false
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "无法解析主机: $streamUrl", e)
            Log.e(TAG, "可能原因: IP地址错误或DNS问题")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "检查MJPEG流失败: $streamUrl", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
            return@withContext false
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            client.cache?.close()
        } catch (e: Exception) {
            Log.e(TAG, "清理网络资源失败", e)
        }
    }
}

/**
 * 网络请求结果封装
 */
sealed class NetworkResult {
    data class Success(val data: String) : NetworkResult()
    data class Error(val message: String) : NetworkResult()
}