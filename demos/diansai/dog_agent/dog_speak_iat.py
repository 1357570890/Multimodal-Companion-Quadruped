# -*- coding:utf-8 -*-
#语音识别文件

# # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # # #
import websocket
import datetime
import hashlib
import base64
import hmac
import json
from urllib.parse import urlencode
import time
import ssl
from wsgiref.handlers import format_date_time
from datetime import datetime
from time import mktime
import _thread as thread
from API_KEY import *
import os
import wave
import librosa
import soundfile as sf
import numpy as np

xufei = ''
Ws_Param = ''

STATUS_FIRST_FRAME = 0  # 第一帧的标识
STATUS_CONTINUE_FRAME = 1  # 中间帧标识
STATUS_LAST_FRAME = 2  # 最后一帧的标识


class Ws_Param(object):
    # 初始化
    def __init__(self, APPID, APIKey, APISecret, AudioFile):
        self.APPID = APPID
        self.APIKey = APIKey
        self.APISecret = APISecret
        self.AudioFile = AudioFile

        # 公共参数(common)
        self.CommonArgs = {"app_id": self.APPID}
        # 业务参数(business)，更多个性化参数可在官网查看
        self.BusinessArgs = {"domain": "iat", "language": "zh_cn", "accent": "mandarin", "vinfo":1,"vad_eos":10000}

    # 生成url
    def create_url(self):
        url = 'wss://ws-api.xfyun.cn/v2/iat'
        # 生成RFC1123格式的时间戳
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))

        # 拼接字符串
        signature_origin = "host: " + "ws-api.xfyun.cn" + "\n"
        signature_origin += "date: " + date + "\n"
        signature_origin += "GET " + "/v2/iat " + "HTTP/1.1"
        # 进行hmac-sha256进行加密
        signature_sha = hmac.new(self.APISecret.encode('utf-8'), signature_origin.encode('utf-8'),
                                 digestmod=hashlib.sha256).digest()
        signature_sha = base64.b64encode(signature_sha).decode(encoding='utf-8')

        authorization_origin = "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"" % (
            self.APIKey, "hmac-sha256", "host date request-line", signature_sha)
        authorization = base64.b64encode(authorization_origin.encode('utf-8')).decode(encoding='utf-8')
        # 将请求的鉴权参数组合为字典
        v = {
            "authorization": authorization,
            "date": date,
            "host": "ws-api.xfyun.cn"
        }
        # 拼接鉴权参数，生成url
        url = url + '?' + urlencode(v)
        # print("date: ",date)
        # print("v: ",v)
        # 此处打印出建立连接时候的url,参考本demo的时候可取消上方打印的注释，比对相同参数时生成的url与自己代码生成的url是否一致
        # print('websocket url :', url)
        return url


# 简化on_message函数，只保留关键调试信息
def on_message(ws, message):
    try:
        message_json = json.loads(message)
        code = message_json["code"]
        sid = message_json["sid"]
        
        if code != 0:
            print(f"错误: 代码={code}, 信息={message_json.get('message', '未知错误')}")
        else:
            try:
                data = message_json["data"]["result"]["ws"]
                result = ""
                for i in data:
                    for w in i["cw"]:
                        result += w["w"]
                
                print(f"识别结果: '{result}'")
                
                global xufei
                xufei += result
                
            except KeyError as ke:
                print(f"解析错误: 缺少键 {ke}")
    except Exception as e:
        print(f"处理消息异常: {e}")


# 简化on_error函数
def on_error(ws, error):
    print(f"WebSocket错误: {error}")


# 简化on_close函数
def on_close(ws, a, b):
    print(f"WebSocket连接关闭: code={a}")


# 修改on_open函数中的run方法
def on_open(ws):
    print("WebSocket连接已建立")
    
    def run(*args):
        frameSize = 8000
        intervel = 0.04
        status = STATUS_FIRST_FRAME

        try:
            if not os.path.exists(wsParam.AudioFile):
                print(f"错误: 音频文件不存在: {wsParam.AudioFile}")
                return
                
            with open(wsParam.AudioFile, "rb") as fp:
                frame_count = 0
                
                while True:
                    buf = fp.read(frameSize)
                    
                    if not buf:
                        status = STATUS_LAST_FRAME
                        print("读取完毕，发送最后一帧")
                    
                    if status == STATUS_FIRST_FRAME:
                        print("发送第一帧音频数据")
                        d = {"common": wsParam.CommonArgs,
                             "business": wsParam.BusinessArgs,
                             "data": {"status": 0, "format": "audio/L16;rate=16000",
                                      "audio": str(base64.b64encode(buf), 'utf-8'),
                                      "encoding": "raw"}}
                        ws.send(json.dumps(d))
                        status = STATUS_CONTINUE_FRAME
                        
                    elif status == STATUS_CONTINUE_FRAME:
                        d = {"data": {"status": 1, "format": "audio/L16;rate=16000",
                                      "audio": str(base64.b64encode(buf), 'utf-8'),
                                      "encoding": "raw"}}
                        ws.send(json.dumps(d))
                        frame_count += 1
                        if frame_count % 30 == 0:
                            print(f"已发送 {frame_count} 帧")
                            
                    elif status == STATUS_LAST_FRAME:
                        d = {"data": {"status": 2, "format": "audio/L16;rate=16000",
                                      "audio": str(base64.b64encode(buf), 'utf-8'),
                                      "encoding": "raw"}}
                        ws.send(json.dumps(d))
                        print("发送最后一帧后等待服务器处理...")
                        # 增加更长的等待时间，确保服务器有足够时间处理并返回结果
                        time.sleep(5)  # 等待5秒钟
                        break
                        
                    time.sleep(intervel)
            
            print("音频数据发送完成，等待最终识别结果...")
            # 不要立即关闭连接，再额外等待一些时间确保接收所有结果
            time.sleep(2)
            
        except Exception as e:
            print(f"发送音频数据错误: {e}")
            
        finally:
            print("正在关闭WebSocket连接")
            ws.close()

    thread.start_new_thread(run, ())


# 简化音频文件检查函数
def check_audio_file(file_path):
    """检查音频文件是否存在及基本格式"""
    print(f"检查音频文件: {file_path}")
    
    if not os.path.exists(file_path):
        print(f"错误: 文件不存在!")
        return False
    
    try:
        with wave.open(file_path, 'rb') as wf:
            channels = wf.getnchannels()
            sample_width = wf.getsampwidth()
            frame_rate = wf.getframerate()
            
            print(f"WAV信息: {channels}声道, {sample_width*8}位, {frame_rate}Hz")
            
            if frame_rate != 16000 or sample_width != 2 or channels != 1:
                print("警告: 音频格式不符合要求(需要16kHz, 16位, 单声道)")
            
        return True
    except Exception as e:
        print(f"音频格式检查失败: {e}")
        return False


# 简化rec_wav_music函数
def rec_wav_music(audio_file='../fake_server/uploads/voice/record.wav'):
    global xufei, wsParam
    
    print("语音识别开始")
    xufei = ''  # 重置全局变量
    
    # 检查音频文件
    check_audio_file(audio_file)
    
    # 检查API密钥
    if not XINGHOU_APPID or not XINGHOU_APISecret or not XINGHOU_KEY:
        print("错误: 讯飞API密钥未设置")
        return xufei
    
    # 初始化参数
    wsParam = Ws_Param(APPID=XINGHOU_APPID, 
                       APISecret=XINGHOU_APISecret,
                       APIKey=XINGHOU_KEY,
                       AudioFile=audio_file)
    
    # 创建连接
    wsUrl = wsParam.create_url()
    websocket.enableTrace(False)  # 关闭详细WebSocket日志
    ws = websocket.WebSocketApp(wsUrl, 
                               on_message=on_message, 
                               on_error=on_error, 
                               on_close=on_close)
    ws.on_open = on_open
    
    # 开始识别
    ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})
    
    print(f"语音识别结束，结果: '{xufei}'")
    
    return xufei
