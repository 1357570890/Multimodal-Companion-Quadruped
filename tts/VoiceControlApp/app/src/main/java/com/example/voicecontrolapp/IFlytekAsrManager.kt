package com.example.voicecontrolapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.iflytek.cloud.*

/**
 * 管理科大讯飞ASR（自动语音识别）服务的类。
 *
 * 重要提示：
 * 1. 请确保已在 app/build.gradle.kts 中添加了科大讯飞的官方SDK依赖。
 * 2. 请确保已在 AndroidManifest.xml 中配置了必要的权限，例如：
 *    <uses-permission android:name="android.permission.RECORD_AUDIO" />
 *    <uses-permission android:name="android.permission.INTERNET" />
 *    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * 3. 使用前，必须在你的Application类或MainActivity中调用 SpeechUtility.createUtility() 来初始化SDK。
 */
class IFlytekAsrManager {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false
    private var asrListener: AsrListener? = null
    
    companion object {
        private const val TAG = "IFlytekAsrManager"
        // TODO: 请将 "YOUR_APP_ID" 替换为您的科大讯飞应用ID
        private const val IFLYTEK_APP_ID = "214cd2ae"
    }

    /**
     * ASR识别结果的回调监听器
     */
    interface AsrListener {
        /**
         * 当有识别结果返回时调用
         * @param result 识别到的文本
         * @param isLast 是否是最后一句
         */
        fun onResult(result: String, isLast: Boolean)

        /**
         * 识别出错时调用
         * @param errorMsg 错误信息
         */
        fun onError(errorMsg: String)

        /**
         * 音量变化时调用
         * @param volume 当前音量，范围0-30
         */
        fun onVolumeChanged(volume: Int)
    }

    /**
     * 初始化ASR引擎
     * @param context Application context
     * @return Boolean 初始化是否成功
     */
    fun init(context: Context, listener: AsrListener): Boolean {
        if (isInitialized) {
            Log.w(TAG, "ASR引擎已初始化")
            return true
        }
        
        this.asrListener = listener

        try {
            // 初始化SDK
            val initParams = "appid=$IFLYTEK_APP_ID"
            SpeechUtility.createUtility(context, initParams)

            // 创建SpeechRecognizer对象
            speechRecognizer = SpeechRecognizer.createRecognizer(context, null)
            if (speechRecognizer == null) {
                Log.e(TAG, "创建SpeechRecognizer失败")
                isInitialized = false
                return false
            }

            setAsrParameters()
            isInitialized = true
            Log.d(TAG, "科大讯飞ASR引擎初始化成功")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "科大讯飞ASR引擎初始化失败", e)
            isInitialized = false
            return false
        }
    }
    
    /**
     * 设置ASR参数
     */
    private fun setAsrParameters() {
        speechRecognizer?.apply {
            // 设置识别引擎为云端
            setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            // 设置语言
            setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            // 设置语言区域
            setParameter(SpeechConstant.ACCENT, "mandarin")
            // 设置返回结果格式
            setParameter(SpeechConstant.RESULT_TYPE, "plain")
            // 设置音频源为外部输入
            setParameter(SpeechConstant.AUDIO_SOURCE, "-1")
            // VAD（静音端点检测）
            // 设置前端点超时，即用户多长时间不说话开始检测
            setParameter(SpeechConstant.VAD_BOS, "5000")
            // 设置后端点超时，恢复到合理时长
            setParameter(SpeechConstant.VAD_EOS, "10000")
            // 设置标点符号, "0"无, "1"有
            setParameter(SpeechConstant.ASR_PTT, "1")
        }
    }

    /**
     * 开始监听
     */
    fun startListening() {
        if (!isInitialized || speechRecognizer == null) {
            Log.e(TAG, "ASR未初始化，无法开始监听")
            asrListener?.onError("ASR服务未初始化")
            return
        }

        val ret = speechRecognizer?.startListening(recognizerListener)
        if (ret != ErrorCode.SUCCESS) {
            val errorMsg = "识别启动失败, 错误码: $ret"
            Log.e(TAG, errorMsg)
            asrListener?.onError(errorMsg)
        } else {
            Log.d(TAG, "ASR监听已开始")
        }
    }

    /**
     * 停止监听
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        Log.d(TAG, "ASR监听已停止")
    }

    /**

     * 取消识别
     */
    fun cancel() {
        speechRecognizer?.cancel()
        Log.d(TAG, "ASR识别已取消")
    }

    /**
     * 写入音频流
     * @param data 音频数据
     * @param offset a
     * @param length a
     */
    fun writeAudio(data: ByteArray, offset: Int, length: Int) {
        if (isInitialized && speechRecognizer?.isListening == true) {
            speechRecognizer?.writeAudio(data, offset, length)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isInitialized = false
        Log.d(TAG, "ASR资源已释放")
    }
    
    /**
     * 识别监听器
     */
    private val recognizerListener = object : RecognizerListener {
        
        override fun onVolumeChanged(volume: Int, data: ByteArray?) {
            // Log.d(TAG, "当前音量: $volume")
            asrListener?.onVolumeChanged(volume)
        }

        override fun onBeginOfSpeech() {
            Log.d(TAG, "ASR回调: 开始说话")
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "ASR回调: 结束说话")
        }

        override fun onResult(results: RecognizerResult, isLast: Boolean) {
            Log.d(TAG, "ASR回调: 识别结果: ${results.resultString}, isLast: $isLast")
            asrListener?.onResult(results.resultString, isLast)
        }

        override fun onError(error: SpeechError) {
            val errorMsg = "识别错误: (${error.errorCode})${error.errorDescription}"
            Log.e(TAG, "ASR回调: $errorMsg")
            asrListener?.onError(errorMsg)
        }

        override fun onEvent(eventType: Int, arg1: Int, arg2: Int, obj: Bundle?) {
            Log.d(TAG, "ASR回调: 事件 eventType=$eventType, arg1=$arg1, arg2=$arg2")
        }
    }
}