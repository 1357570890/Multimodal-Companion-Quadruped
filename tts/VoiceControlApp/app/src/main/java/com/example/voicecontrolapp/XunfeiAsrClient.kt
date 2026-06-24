package com.example.voicecontrolapp

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class XunfeiAsrClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String
) {

    companion object {
        private const val TAG = "XunfeiAsrClient"
        private const val HOST = "iat-api.xfyun.cn"
        private const val PATH = "/v2/iat"
        private const val ALGORITHM = "hmac-sha256"
    }

    private var webSocket: WebSocket? = null
    private val httpClient = OkHttpClient.Builder().build()
    private var listener: AsrListener? = null

    /**
     * ASR事件监听器
     */
    interface AsrListener {
        fun onAsrResult(message: String) // 返回中间或最终结果
        fun onAsrFinalResult(message: String) // 仅返回最终识别结果
        fun onAsrError(error: String)
        fun onAsrReady() // WebSocket连接成功，可以开始发送音频
        fun onAsrClose()
    }

    /**
     * 连接到讯飞ASR服务
     */
    fun connect(listener: AsrListener) {
        this.listener = listener
        try {
            val authUrl = createAuthUrl()
            val request = Request.Builder().url(authUrl).build()
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket 连接成功")
                    listener.onAsrReady()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "收到消息: $text")
                    handleMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 正在关闭: $code / $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket 已关闭: $code / $reason")
                    listener.onAsrClose()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket 连接失败", t)
                    listener.onAsrError("连接失败: ${t.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "创建鉴权URL失败", e)
            listener.onAsrError("创建鉴权URL失败: ${e.message}")
        }
    }

    /**
     * 处理从服务器返回的消息
     */
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val code = json.getInt("code")
            if (code != 0) {
                val message = json.getString("message")
                listener?.onAsrError("服务错误: $code - $message")
                return
            }

            val data = json.getJSONObject("data")
            val result = data.getJSONObject("result")
            val status = data.getInt("status") // 0:首帧, 1:中间, 2:末帧

            val wsArray = result.getJSONArray("ws")
            val sb = StringBuilder()
            for (i in 0 until wsArray.length()) {
                val ws = wsArray.getJSONObject(i)
                val cwArray = ws.getJSONArray("cw")
                for (j in 0 until cwArray.length()) {
                    val cw = cwArray.getJSONObject(j)
                    sb.append(cw.getString("w"))
                }
            }
            val recognizedText = sb.toString()
            listener?.onAsrResult(recognizedText)

            if (status == 2) {
                val isFinal = result.getBoolean("ls")
                if (isFinal) {
                    listener?.onAsrFinalResult(recognizedText)
                }
                // 收到末帧，可以安全关闭
                disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析JSON消息失败", e)
            listener?.onAsrError("解析消息失败: ${e.message}")
        }
    }

    /**
     * 发送音频数据
     * @param data 原始PCM音频数据
     * @param isFirstFrame 是否是第一帧
     */
    fun sendAudio(data: ByteArray, isFirstFrame: Boolean) {
        if (webSocket == null) {
            Log.w(TAG, "WebSocket未连接，无法发送数据")
            return
        }

        val audioBase64 = Base64.encodeToString(data, Base64.NO_WRAP)
        val json = JSONObject()

        if (isFirstFrame) {
            val common = JSONObject().put("app_id", appId)
            val business = JSONObject().apply {
                put("language", "zh_cn")
                put("domain", "iat")
                put("accent", "mandarin")
                put("dwa", "wpgs") // 动态修正，获取标点
            }
            val dataJson = JSONObject().apply {
                put("status", 0)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", audioBase64)
            }
            json.put("common", common)
            json.put("business", business)
            json.put("data", dataJson)
        } else {
            val dataJson = JSONObject().apply {
                put("status", 1)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", audioBase64)
            }
            json.put("data", dataJson)
        }

        webSocket?.send(json.toString())
    }

    /**
     * 发送结束帧，通知服务器音频已发送完毕
     */
    fun stopSending() {
        if (webSocket == null) return
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("status", 2)
            })
        }
        webSocket?.send(json.toString())
        Log.d(TAG, "发送结束帧")
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    /**
     * 创建带鉴权的URL
     */
    @Throws(Exception::class)
    private fun createAuthUrl(): String {
        val url = URL("https", HOST, PATH)
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        val builder = StringBuilder("host: ").append(HOST).append("\n")
            .append("date: ").append(date).append("\n")
            .append("GET ").append(PATH).append(" HTTP/1.1")

        val sha = hmacSha256(builder.toString(), apiSecret)
        val authorization = Base64.encodeToString(
            String.format(
                "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, ALGORITHM, "host date request-line", sha
            ).toByteArray(Charset.forName("UTF-8")), Base64.NO_WRAP
        )

        return "https://$HOST$PATH?authorization=$authorization&date=${date.replace(" ", "%20")}&host=$HOST"
    }

    @Throws(Exception::class)
    private fun hmacSha256(value: String, key: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key.toByteArray(Charset.forName("UTF-8")), ALGORITHM))
        return Base64.encodeToString(mac.doFinal(value.toByteArray(Charset.forName("UTF-8"))), Base64.NO_WRAP)
    }
}