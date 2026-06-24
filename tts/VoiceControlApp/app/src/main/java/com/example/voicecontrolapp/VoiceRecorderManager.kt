package com.example.voicecontrolapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

class VoiceRecorderManager(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var audioStreamListener: AudioStreamListener? = null

    /**
     * 音频流监听器
     */
    interface AudioStreamListener {
        fun onAudioStream(data: ByteArray)
        fun onRecordingError(error: String)
    }

    companion object {
        private const val TAG = "VoiceRecorderManager"
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
    }
    
    /**
     * 开始录音并监听音频流
     */
    fun startRecording(listener: AudioStreamListener): Boolean {
        this.audioStreamListener = listener
        try {
            // 计算缓冲区大小
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                val errorMsg = "无法获取有效的缓冲区大小"
                Log.e(TAG, errorMsg)
                listener.onRecordingError(errorMsg)
                return false
            }

            // 初始化AudioRecord
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2 // 使用更大的缓冲区
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val errorMsg = "AudioRecord初始化失败"
                Log.e(TAG, errorMsg)
                audioRecord?.release()
                audioRecord = null
                listener.onRecordingError(errorMsg)
                return false
            }

            // 开始录音
            audioRecord?.startRecording()
            isRecording = true

            // 在协程中处理录音数据
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                processAudioStream(bufferSize)
            }

            Log.d(TAG, "录音开始")
            return true

        } catch (e: Exception) {
            val errorMsg = "录音开始失败: ${e.message}"
            Log.e(TAG, "录音开始失败", e)
            listener.onRecordingError(errorMsg)
            cleanup()
            return false
        }
    }

    /**
     * 处理音频流，通过监听器发送数据
     */
    private suspend fun processAudioStream(bufferSize: Int) {
        val audioData = ByteArray(bufferSize)
        while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val bytesRead = audioRecord?.read(audioData, 0, bufferSize) ?: 0
            if (bytesRead > 0) {
                // 回调音频数据
                audioStreamListener?.onAudioStream(audioData.copyOf(bytesRead))
            }
        }
    }

    /**
     * 停止录音
     */
    fun stopRecording() {
        try {
            if (isRecording && audioRecord != null) {
                isRecording = false

                // 等待录音协程自然结束
                runBlocking {
                    recordingJob?.join()
                }

                // 停止录音
                audioRecord?.apply {
                    try {
                        if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            stop()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "停止录音时出现异常: ${e.message}")
                    }
                }
                
                cleanup()
                Log.d(TAG, "录音结束")
            } else {
                Log.w(TAG, "停止录音：当前没有在录音或audioRecord为null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            cleanup()
        }
    }

    /**
     * 取消录音
     */
    fun cancelRecording() {
        try {
            if (isRecording) {
                isRecording = false
                recordingJob?.cancel() // 立即取消协程
                cleanup()
                Log.d(TAG, "录音已取消")
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消录音失败", e)
            cleanup()
        }
    }
    
    /**
     * 获取当前录音状态
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 清理资源
     */
    private fun cleanup() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        stop()
                    }
                    release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理AudioRecord时出现异常: ${e.message}")
        }

        audioRecord = null
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            if (isRecording) {
                stopRecording()
            }
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
}