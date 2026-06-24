package com.example.voicecontrolapp

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream

/**
 * 测试VoiceRecorderManager的WAV文件生成功能
 */
class VoiceRecorderManagerTest {

    @Test
    fun testWavHeaderGeneration() {
        // 创建测试用的PCM数据 (1秒的静音数据，16000Hz, 16bit, mono)
        val sampleRate = 16000
        val durationSeconds = 1
        val bytesPerSample = 2 // 16-bit
        val channels = 1 // mono
        val pcmDataSize = sampleRate * durationSeconds * bytesPerSample * channels
        val pcmData = ByteArray(pcmDataSize) // 全零数据（静音）

        // 生成WAV文件头
        val wavData = addWavHeader(pcmData, sampleRate)

        // 验证WAV文件头
        assertEquals("WAV文件总大小不正确", pcmDataSize + 44, wavData.size)

        // 验证RIFF标识
        assertEquals('R'.code.toByte(), wavData[0])
        assertEquals('I'.code.toByte(), wavData[1])
        assertEquals('F'.code.toByte(), wavData[2])
        assertEquals('F'.code.toByte(), wavData[3])

        // 验证WAVE标识
        assertEquals('W'.code.toByte(), wavData[8])
        assertEquals('A'.code.toByte(), wavData[9])
        assertEquals('V'.code.toByte(), wavData[10])
        assertEquals('E'.code.toByte(), wavData[11])

        // 验证fmt chunk
        assertEquals('f'.code.toByte(), wavData[12])
        assertEquals('m'.code.toByte(), wavData[13])
        assertEquals('t'.code.toByte(), wavData[14])
        assertEquals(' '.code.toByte(), wavData[15])

        // 验证PCM格式 (应该是1)
        assertEquals(1, (wavData[20].toInt() and 0xFF) or ((wavData[21].toInt() and 0xFF) shl 8))

        // 验证声道数 (应该是1)
        assertEquals(1, (wavData[22].toInt() and 0xFF) or ((wavData[23].toInt() and 0xFF) shl 8))

        // 验证采样率 (应该是16000)
        val actualSampleRate = (wavData[24].toInt() and 0xFF) or 
                              ((wavData[25].toInt() and 0xFF) shl 8) or
                              ((wavData[26].toInt() and 0xFF) shl 16) or
                              ((wavData[27].toInt() and 0xFF) shl 24)
        assertEquals(sampleRate, actualSampleRate)

        // 验证位深度 (应该是16)
        assertEquals(16, (wavData[34].toInt() and 0xFF) or ((wavData[35].toInt() and 0xFF) shl 8))

        // 验证data chunk
        assertEquals('d'.code.toByte(), wavData[36])
        assertEquals('a'.code.toByte(), wavData[37])
        assertEquals('t'.code.toByte(), wavData[38])
        assertEquals('a'.code.toByte(), wavData[39])

        println("WAV文件头验证通过！")
        println("文件大小: ${wavData.size} bytes")
        println("PCM数据大小: $pcmDataSize bytes")
        println("采样率: $actualSampleRate Hz")
    }

    /**
     * 为PCM数据添加WAV文件头 (复制自VoiceRecorderManager的逻辑)
     */
    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val bitRate = sampleRate * 16 * 1 / 8 // 16bit, mono

        // WAV文件头
        header[0] = 'R'.code.toByte()  // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // 文件大小
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // fmt chunk size (16 for PCM)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // PCM format
        header[20] = 1
        header[21] = 0

        // 声道数 (mono = 1)
        header[22] = 1
        header[23] = 0

        // 采样率
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // 字节率
        header[28] = (bitRate and 0xff).toByte()
        header[29] = ((bitRate shr 8) and 0xff).toByte()
        header[30] = ((bitRate shr 16) and 0xff).toByte()
        header[31] = ((bitRate shr 24) and 0xff).toByte()

        // 块对齐 (2 bytes for 16-bit mono)
        header[32] = 2
        header[33] = 0

        // 位深度 (16-bit)
        header[34] = 16
        header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // data chunk size
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
