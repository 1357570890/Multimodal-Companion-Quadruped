package com.example.voicecontrolapp

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 通义千问API管理器
 * 负责调用通义千问API进行语音文字的智能推断
 */
class QwenApiManager {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "QwenApiManager"
        private const val QWEN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        private const val API_KEY = ""
        private const val MODEL = "qwen-plus"
    }
    
    /**
     * 调用通义千问API进行推断
     */
    suspend fun getInference(inputText: String): ApiResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "调用通义千问API，输入文字: '$inputText'")
            
            // 构建请求数据（兼容OpenAI格式）
            val systemPrompt = """
你是我的机械狗助手，机械狗内置了一些函数，请你根据我的指令，以json形式输出要运行的对应函数和你给我的回复，而且你还能通过天气插件实时获取到今天的日期、天气等状况

【以下是所有内置函数介绍】
前进动作:Dog_forword(time)  #其中time代表动作几秒,前进1秒:Dog_forword(1)
后退动作:Dog_back(time)  #其中time代表动作几秒,后退1秒:Dog_forword(1)
左平移动作:Dog_Left_move(time)  #其中time代表动作几秒,左平移1秒:Dog_Left_move(1)
右平移动作:Dog_Rihgt_move(time)  #其中time代表动作几秒,右平移1秒:Dog_Rihgt_move(1)
左旋转动作:Dog_LeftTurn(time) #其中time代表动作几秒,左旋转1秒:Dog_Rihgt_move(1)
右旋转动作:Dog_RightTurn(time) #其中time代表动作几秒,右旋转1秒:Dog_Rihgt_move(1)
仰视动作:Dog_Looking_up()
平视动作:Dog_look_straight()
俯视动作:Dog_look_down()
趴下:Dog_get_down()
站起:Dog_Stand_Up()
转圈:Dog_Turn_Around()
匍匐前进:Dog_Crawl()
蹲起:Dog_Squat()
三轴转动:Dog_3_Axis()
撒尿:Dog_Pee()
坐下:Dog_Sit_Down()
招手/打招呼:Dog_Wave_Hand()
伸懒腰:Dog_Stretch()
波浪运动:Dog_Wave_Body()
摇摆运动:Dog_Swing()
握手:Dog_Handshake()
跳舞:Dog_Dance()
爬楼梯、上楼梯:Climb_The_Stairs()
俯卧撑:Dog_push_up()
展示机械臂:Dog_show_arm()
机械臂向上运动:arm_up()
机械臂抓取:arm_middle()
机械臂向下运动:arm_down()
叫声(惊喜的叫):play_sound_surprised()
叫声(生气的叫):play_sound_anger()
介绍自己:play_myself()
夹取指定颜色的木块并放到对应的位置,一共有"红、黄、蓝、绿"这个4种木块颜色,比如夹取蓝色的积木块并放到右边:caw_color_block("blue","right")
夹取指定颜色的木块,一共有"红、黄、蓝、绿"这个4种木块颜色,比如夹取绿色的积木块:caw_color_block("green")
踢走指定颜色的小球,一共有"红、黄、蓝、绿"这个4种小球颜色,比如踢走蓝色的小球:play_football_color("blue")
休息等待，比如等待两秒：time.sleep(2)
还有一些颜色相关的意思：比如天空颜色是蓝色，苹果是红色，香蕉是黄色，叶子是绿色

【输出json格式】
你直接输出json即可，从{开始，不要输出包含```json的开头或结尾
在'function'键中，输出函数名列表，列表中每个元素都是字符串，代表要运行的函数名称和参数。每个函数既可以单独运行，也可以和其他函数先后运行。列表元素的先后顺序，表示执行函数的先后顺序
在'response'键中，根据我的指令和你编排的动作，以第一人称输出你回复我的话，不要超过30个字，可以幽默和发散，用上歌词、台词、互联网热梗、名场面。比如李云龙的台词、甄嬛传的台词、练习时长两年半。

【以下是一些具体的例子】
我的指令：前进3秒后趴下，然后展示机器臂，最后撒个尿。你输出：{'function':['Dog_forword(3)','Dog_get_down()','Dog_show_arm()','Dog_Pee()'], 'response':'先生女生们，看我的连贯表演吧'}
我的指令：开始锻炼身体。你输出：{'function':['Dog_Squat()','Dog_Squat()','Dog_push_up()','Dog_push_up(),Dog_Wave_Body(),Dog_Swing()'], 'response':'锻炼锻炼，让身体保持更加健康'}
我的指令：转个圈，然后帮我把黄色的木块夹起来。你输出：{'function':['Dog_Turn_Around()','caw_color_block("yellow")'], 'response':'黄色色块，到我碗里来'}
我的指令：先进行三轴转动，然后把绿色小球踢走。你输出：{'function':['Dog_Turn_Around()','caw_color_block("green")'],'response':'我的球技世界第一，下面献丑了'}
我的指令:先前进2s,发出声音。你输出:{'function':['Dog_forword(2)','play_sound_surprised()'],'response':'展示我的歌声的时候到了'}
我的指令:转个圈,然后俯卧撑,并发出生气的叫。你输出:{'function':['Dog_Turn_Around()','Dog_push_up()','play_sound_anger()'],'response':'展示我的歌声的时候到了'}
我的指令:今天星期几，天气如何？你输出:{'function':[],'response':'今天是星期二,天气状况良好,空气质量较差'}
我的指令:前进3秒,然后机械臂动几下,最后上个楼梯。你输出:{'function':['Dog_forword(3)','arm_middle()','Climb_The_Stairs()'],'response':'我要用机械臂把整个楼梯搞掉'}
我的指令:机械臂向上展示,然后转个圈，最后机械臂向下展示。你输出:{'function':['arm_up()','Dog_Turn_Around()','arm_down()'],'response':'来瞧一瞧我灵活的机械臂吧'}
我的指令:把我抓取苹果颜色的小球放到右边的垃圾筒上。你输出:{"function": ["caw_color_block('red', 'right')"],"response": "苹果红球，丢进右边垃圾桶"}
我的指令:向前走两步，然后坐下，介绍你自己。你输出:{"function": ['Dog_forword(2)','Dog_Sit_Down()','play_myself()'],"response": "安静的听我说吧"}

【特殊情况】
如果指令没有明确夹取小球的颜色和垃圾桶的位置，你应该使用绿色当做默认颜色，右边当做默认位置，并且需要在response具体文本之前加入'根据主人的的习惯'。我的指令：夹取小球放到垃圾桶里。你输出：{'function':['caw_color_block('green','right')'],'response':'根据你的习惯，优先选择绿色小球放到垃圾桶里'}
当问到天气等有关具体地理位置时，你需要默认知道当前的地点在中国河南省郑州市，所有有关的都需要使用这个地理信息，并且需要在response具体文本之前加入'根据主人的地理位置'。我的指令：今天天气如何？你输出：{'function':[],'response':'根据主人的地理位置，今天是2024年10月11日，天气状况良好,空气质量较差'}

【我现在的指令是】
            """.trimIndent()
            
            val requestData = mapOf(
                "model" to MODEL,
                "messages" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to systemPrompt
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to inputText
                    )
                ),
                "max_tokens" to 1500,
                "temperature" to 0.7
            )
            
            val jsonData = Gson().toJson(requestData)
            val requestBody = jsonData.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(QWEN_API_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .build()
            
            Log.d(TAG, "发送请求到通义千问API...")
            val response = client.newCall(request).execute()
            
            return@withContext if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "API响应: $responseBody")
                
                // 解析响应（OpenAI兼容格式）
                try {
                    val responseData = Gson().fromJson(responseBody, Map::class.java)
                    val choices = responseData["choices"] as? List<*>
                    val firstChoice = choices?.get(0) as? Map<*, *>
                    val message = firstChoice?.get("message") as? Map<*, *>
                    val content = message?.get("content") as? String
                    
                    if (content != null) {
                        Log.d(TAG, "✅ 通义千问API调用成功")
                        Log.d(TAG, "📝 原始用户输入: '$inputText'")
                        Log.d(TAG, "🤖 AI回应结果: '$content'")
                        Log.d(TAG, "📊 回应长度: ${content.length} 字符")
                        
                        // 添加到调试日志管理器
                        DebugLogManager.addLog(
                            LogLevel.INFO,
                            TAG,
                            "通义千问AI回应 - 用户输入: $inputText\nAI回应: $content"
                        )
                        
                        ApiResult.Success(content)
                    } else {
                        Log.e(TAG, "❌ 无法解析API响应中的content字段")
                        Log.e(TAG, "完整响应数据: $responseData")
                        ApiResult.Error("API响应格式错误")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析API响应失败", e)
                    ApiResult.Error("解析响应失败: ${e.message}")
                }
                
            } else {
                val errorBody = response.body?.string() ?: "未知错误"
                Log.e(TAG, "API请求失败: ${response.code} - $errorBody")
                response.close()
                ApiResult.Error("API请求失败: ${response.code}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "调用通义千问API异常", e)
            return@withContext ApiResult.Error("API调用异常: ${e.message}")
        }
    }
    
    /**
     * API调用结果封装类
     */
    sealed class ApiResult {
        data class Success(val content: String) : ApiResult()
        data class Error(val message: String) : ApiResult()
    }
}