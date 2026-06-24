package com.example.voicecontrolapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 自定义MJPEG流显示组件
 * 专门用于显示multipart/x-mixed-replace格式的MJPEG流
 */
class MjpegStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private var streamJob: Job? = null
    private var isStreaming = false
    private var streamUrl: String? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "MjpegStreamView"
        private const val BOUNDARY_MARKER = "--frame"
        private const val CONTENT_TYPE_MARKER = "Content-Type: image/jpeg"
    }
    
    init {
        scaleType = ScaleType.FIT_CENTER
        setBackgroundColor(android.graphics.Color.BLACK)

        Log.d(TAG, "MjpegStreamView初始化完成")
        Log.d(TAG, "ScaleType: $scaleType")
        Log.d(TAG, "背景色: 黑色")
    }
    
    /**
     * 开始播放MJPEG流
     */
    fun startStream(url: String) {
        Log.d(TAG, "=== 开始MJPEG流 ===")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "当前线程: ${Thread.currentThread().name}")

        stopStream()

        streamUrl = url
        isStreaming = true

        streamJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "在IO线程中开始播放MJPEG流")
                playMjpegStream(url)
            } catch (e: Exception) {
                Log.e(TAG, "MJPEG流播放失败 - 异常类型: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "错误消息: ${e.message}")
                Log.e(TAG, "错误堆栈: ${e.stackTraceToString()}")

                withContext(Dispatchers.Main) {
                    Log.e(TAG, "在主线程中显示错误图像")
                    // 显示错误图像
                    setImageResource(android.R.drawable.ic_dialog_alert)
                }
            }
        }
    }
    
    /**
     * 停止播放MJPEG流
     */
    fun stopStream() {
        Log.d(TAG, "=== 停止MJPEG流 ===")
        isStreaming = false
        streamJob?.cancel()
        streamJob = null
        Log.d(TAG, "流已停止，作业已取消")
    }

    /**
     * 测试方法：显示一个测试图片
     */
    fun showTestImage() {
        Log.d(TAG, "=== 显示测试图片 ===")
        try {
            // 显示Android系统的信息图标作为测试
            setImageResource(android.R.drawable.ic_dialog_info)
            Log.d(TAG, "✓ 测试图片设置成功")
        } catch (e: Exception) {
            Log.e(TAG, "✗ 设置测试图片失败", e)
        }
    }
    
    /**
     * 播放MJPEG流的核心逻辑
     */
    private suspend fun playMjpegStream(url: String) {
        Log.d(TAG, "=== 构建HTTP请求 ===")

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "multipart/x-mixed-replace")
            .addHeader("User-Agent", "VoiceControlApp-MjpegViewer/1.0")
            .build()

        Log.d(TAG, "请求URL: ${request.url}")
        Log.d(TAG, "请求头: ${request.headers}")

        Log.d(TAG, "发送HTTP请求...")
        val response = client.newCall(request).execute()

        Log.d(TAG, "=== HTTP响应信息 ===")
        Log.d(TAG, "响应码: ${response.code}")
        Log.d(TAG, "响应消息: ${response.message}")
        Log.d(TAG, "响应协议: ${response.protocol}")

        // 打印所有响应头
        response.headers.forEach { (name, value) ->
            Log.d(TAG, "响应头 $name: $value")
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "无错误内容"
            Log.e(TAG, "HTTP请求失败 - 状态码: ${response.code}")
            Log.e(TAG, "错误响应体: $errorBody")
            throw IOException("HTTP错误: ${response.code} - ${response.message}")
        }

        val contentType = response.header("Content-Type")
        Log.d(TAG, "Content-Type: $contentType")

        if (contentType == null || !contentType.contains("multipart")) {
            Log.w(TAG, "警告: Content-Type不是multipart格式，可能不是MJPEG流")
        }

        val inputStream = response.body?.byteStream()
            ?: throw IOException("无法获取响应流")

        Log.d(TAG, "=== 开始解析MJPEG流 ===")

        try {
            parseMjpegStream(inputStream)
        } finally {
            Log.d(TAG, "关闭流和响应")
            inputStream.close()
            response.close()
        }
    }
    
    /**
     * 解析MJPEG流并显示帧
     */
    private suspend fun parseMjpegStream(inputStream: InputStream) {
        Log.d(TAG, "=== 开始解析MJPEG流数据 ===")

        val buffer = ByteArray(8192)
        val frameBuffer = ByteArrayOutputStream()
        var inFrame = false
        var frameCount = 0
        var totalBytesRead = 0L
        var boundaryFound = false
        var contentTypeFound = false

        Log.d(TAG, "查找边界标记: '$BOUNDARY_MARKER'")
        Log.d(TAG, "查找内容类型: '$CONTENT_TYPE_MARKER'")

        while (isStreaming && !streamJob?.isCancelled!!) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) {
                Log.d(TAG, "流结束 (bytesRead = -1)")
                break
            }

            totalBytesRead += bytesRead
            val data = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)

            // 每1MB数据打印一次统计
            if (totalBytesRead % (1024 * 1024) == 0L) {
                Log.d(TAG, "已读取数据: ${totalBytesRead / 1024}KB, 帧数: $frameCount")
            }

            // 查找帧边界
            if (data.contains(BOUNDARY_MARKER)) {
                if (!boundaryFound) {
                    Log.d(TAG, "✓ 找到边界标记: '$BOUNDARY_MARKER'")
                    boundaryFound = true
                }

                if (inFrame && frameBuffer.size() > 0) {
                    Log.d(TAG, "处理完整帧 #$frameCount, 大小: ${frameBuffer.size()} 字节")
                    // 处理完整的帧
                    processFrame(frameBuffer.toByteArray())
                    frameCount++
                    frameBuffer.reset()
                }
                inFrame = false
            }

            // 查找Content-Type标记
            if (data.contains(CONTENT_TYPE_MARKER)) {
                if (!contentTypeFound) {
                    Log.d(TAG, "✓ 找到Content-Type标记: '$CONTENT_TYPE_MARKER'")
                    contentTypeFound = true
                }

                inFrame = true
                frameBuffer.reset()

                // 跳过HTTP头部，找到实际的JPEG数据开始位置
                val jpegStart = data.indexOf("\r\n\r\n")
                if (jpegStart != -1) {
                    val jpegData = buffer.copyOfRange(jpegStart + 4, bytesRead)
                    frameBuffer.write(jpegData)
                    Log.d(TAG, "开始新帧，跳过头部 $jpegStart 字节，JPEG数据: ${jpegData.size} 字节")
                } else {
                    Log.w(TAG, "未找到JPEG数据开始位置 (\\r\\n\\r\\n)")
                }
            } else if (inFrame) {
                // 收集帧数据
                frameBuffer.write(buffer, 0, bytesRead)
            }

            // 每10帧打印一次详细日志
            if (frameCount > 0 && frameCount % 10 == 0) {
                Log.d(TAG, "处理进度: $frameCount 帧, 总数据: ${totalBytesRead / 1024}KB")
            }
        }

        Log.d(TAG, "=== MJPEG流解析完成 ===")
        Log.d(TAG, "总帧数: $frameCount")
        Log.d(TAG, "总数据量: ${totalBytesRead / 1024}KB")
        Log.d(TAG, "边界标记找到: $boundaryFound")
        Log.d(TAG, "Content-Type找到: $contentTypeFound")
    }
    
    /**
     * 处理单个JPEG帧
     */
    private suspend fun processFrame(frameData: ByteArray) {
        Log.d(TAG, "=== 处理JPEG帧 ===")
        Log.d(TAG, "帧数据大小: ${frameData.size} 字节")

        // 检查JPEG文件头
        if (frameData.size >= 2) {
            val header = String.format("%02X %02X", frameData[0], frameData[1])
            Log.d(TAG, "JPEG文件头: $header")

            if (frameData[0] == 0xFF.toByte() && frameData[1] == 0xD8.toByte()) {
                Log.d(TAG, "✓ 有效的JPEG文件头")
            } else {
                Log.w(TAG, "⚠ 无效的JPEG文件头，期望: FF D8")
            }
        } else {
            Log.e(TAG, "✗ 帧数据太小，无法包含JPEG头")
            return
        }

        // 检查JPEG文件尾
        if (frameData.size >= 2) {
            val tailIndex = frameData.size - 2
            val tail = String.format("%02X %02X", frameData[tailIndex], frameData[tailIndex + 1])
            Log.d(TAG, "JPEG文件尾: $tail")

            if (frameData[tailIndex] == 0xFF.toByte() && frameData[tailIndex + 1] == 0xD9.toByte()) {
                Log.d(TAG, "✓ 有效的JPEG文件尾")
            } else {
                Log.w(TAG, "⚠ 无效的JPEG文件尾，期望: FF D9")
            }
        }

        try {
            Log.d(TAG, "开始解码JPEG数据...")
            val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)

            if (bitmap != null) {
                Log.d(TAG, "✓ JPEG解码成功")
                Log.d(TAG, "图像尺寸: ${bitmap.width} x ${bitmap.height}")
                Log.d(TAG, "图像配置: ${bitmap.config}")

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "在主线程中设置图像")
                    setImageBitmap(bitmap)
                    Log.d(TAG, "✓ 图像设置完成")
                }
            } else {
                Log.e(TAG, "✗ JPEG解码失败，返回null")
                Log.e(TAG, "数据大小: ${frameData.size} 字节")

                // 打印前32字节的十六进制数据用于调试
                val hexData = frameData.take(32).joinToString(" ") {
                    String.format("%02X", it)
                }
                Log.e(TAG, "前32字节数据: $hexData")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ 处理帧异常", e)
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常消息: ${e.message}")
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopStream()
    }
}
