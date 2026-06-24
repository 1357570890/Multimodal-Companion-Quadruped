import os
import queue
import time
import wave
import base64
import hashlib
import hmac
import json
from urllib.parse import urlencode
from datetime import datetime
from wsgiref.handlers import format_date_time
import threading

import websocket # 需要 pip install websocket-client

# --- 讯飞配置 (请通过环境变量设置) ---
XF_APPID = os.environ.get("XF_APPID", "your_xf_appid_here")
XF_API_KEY = os.environ.get("XF_API_KEY", "your_xf_api_key_here")
XF_API_SECRET = os.environ.get("XF_API_SECRET", "your_xf_api_secret_here")

# --- ASR 服务地址 ---
XF_ASR_HOST = "iat-api.xfyun.cn"
XF_ASR_PATH = "/v2/iat"
XF_ASR_URL = f"wss://{XF_ASR_HOST}{XF_ASR_PATH}"

# --- 配置参数 ---
INPUT_WAV_FILE = "input_audio.wav" # 请准备一个中文WAV文件, e.g., 16-bit, 16kHz, 单声道
INPUT_SAMPLE_RATE = 16000 # 讯飞ASR推荐使用 16kHz
CHUNK_SIZE = 1280 # 讯飞推荐每次发送40ms音频，16000*2*0.04=1280

class XunfeiASR:
    """科大讯飞流式语音识别客户端"""
    def __init__(self, app_id, api_key, api_secret):
        if not all([app_id, api_key, api_secret]):
            raise ValueError(
                "讯飞凭证 (XF_APPID, XF_API_KEY, XF_API_SECRET) 未设置。 "
                "请将它们设置为环境变量。"
            )
        self.app_id = app_id
        self.api_key = api_key
        self.api_secret = api_secret
        self.ws = None
        self.result_queue = queue.Queue()
        self.audio_generator = None

    def _get_auth_url(self):
        """生成带鉴权的WebSocket URL"""
        now = datetime.now()
        date = format_date_time(time.mktime(now.timetuple()))

        # 构造签名源字符串
        signature_origin = f"host: {XF_ASR_HOST}\n"
        signature_origin += f"date: {date}\n"
        signature_origin += f"GET {XF_ASR_PATH} HTTP/1.1"

        # 进行 hmac-sha256 签名
        signature_sha = hmac.new(
            self.api_secret.encode('utf-8'),
            signature_origin.encode('utf-8'),
            digestmod=hashlib.sha256
        ).digest()
        signature_sha_base64 = base64.b64encode(signature_sha).decode('utf-8')

        # 构造授权参数
        authorization_origin = f'api_key="{self.api_key}", algorithm="hmac-sha256", headers="host date request-line", signature="{signature_sha_base64}"'
        authorization = base64.b64encode(authorization_origin.encode('utf-8')).decode('utf-8')

        # 构造最终URL
        v = {
            "authorization": authorization,
            "date": date,
            "host": XF_ASR_HOST
        }
        return XF_ASR_URL + "?" + urlencode(v)

    def _on_message(self, ws, message):
        """WebSocket 消息回调"""
        try:
            res = json.loads(message)
            code = res.get('code')
            if code != 0:
                error_msg = f"讯飞ASR错误: {code}, {res.get('message', 'Unknown error')}"
                print(error_msg)
                self.result_queue.put((error_msg, True)) # 标记为最终错误
                self.result_queue.put(None)
                return

            data = res.get('data', {})
            result = data.get('result', {})
            
            # 提取识别的文字片段
            words = []
            for w_part in result.get('ws', []):
                for cw_part in w_part.get('cw', []):
                    words.append(cw_part.get('w', ''))
            transcript_segment = "".join(words)

            if not transcript_segment:
                return

            is_final = result.get('ls', False)
            self.result_queue.put((transcript_segment, is_final))

            if data.get('status', 0) == 2:
                # 服务器确认流结束
                self.result_queue.put(None)

        except Exception as e:
            print(f"处理讯飞ASR消息时出错: {e}")
            self.result_queue.put(None)

    def _on_error(self, ws, error):
        print(f"讯飞ASR WebSocket错误: {error}")
        self.result_queue.put(None)

    def _on_close(self, ws, close_status_code, close_msg):
        # print("讯飞ASR WebSocket已关闭")
        # 确保即使异常关闭也能发出结束信号
        if self.result_queue.empty():
            self.result_queue.put(None)

    def _on_open(self, ws):
        """WebSocket 连接成功回调，启动音频发送线程"""
        def sender_thread():
            status = 0  # 0 for first frame, 1 for middle, 2 for last
            for chunk in self.audio_generator:
                if status == 0:
                    # 首帧
                    business_args = {
                        "language": "zh_cn",
                        "domain": "iat",
                        "accent": "mandarin",
                        "dwa": "wpgs"  # 动态修正，实现标点
                    }
                    data_payload = {
                        "common": {"app_id": self.app_id},
                        "business": business_args,
                        "data": {
                            "status": 0,
                            "format": f"audio/L16;rate={INPUT_SAMPLE_RATE}",
                            "encoding": "raw",
                            "audio": base64.b64encode(chunk).decode('utf-8')
                        }
                    }
                    status = 1
                else:
                    # 中间帧
                    data_payload = {
                        "data": {
                            "status": 1,
                            "format": f"audio/L16;rate={INPUT_SAMPLE_RATE}",
                            "encoding": "raw",
                            "audio": base64.b64encode(chunk).decode('utf-8')
                        }
                    }
                ws.send(json.dumps(data_payload))
            
            # 发送最后一帧
            ws.send(json.dumps({"data": {"status": 2}}))

        threading.Thread(target=sender_thread).start()

    def recognize_stream(self, audio_generator):
        """启动识别并返回一个(文本片段, 是否结束)的生成器"""
        self.audio_generator = audio_generator
        auth_url = self._get_auth_url()
        
        self.ws = websocket.WebSocketApp(
            auth_url,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close,
            on_open=self._on_open
        )
        
        # 在后台线程中运行WebSocket客户端
        wst = threading.Thread(target=self.ws.run_forever)
        wst.daemon = True
        wst.start()

        # 从队列中获取识别结果并 yield
        while True:
            result = self.result_queue.get()
            if result is None:
                break
            yield result
        
        self.ws.close()

def main():
    """主函数，编排整个流程"""
    if not os.path.exists(INPUT_WAV_FILE):
        print(f"错误: 输入文件 {INPUT_WAV_FILE} 不存在。")
        print("请手动准备一个中文WAV文件用于模拟输入(例如: 16-bit, 16kHz, 单声道)。")
        return

    # 实例化讯飞ASR客户端
    try:
        asr_client = XunfeiASR(XF_APPID, XF_API_KEY, XF_API_SECRET)
    except ValueError as e:
        print(e)
        return

    try:
        with wave.open(INPUT_WAV_FILE, 'rb') as wf:
            if wf.getnchannels() != 1 or wf.getsampwidth() != 2:
                 raise ValueError("输入WAV必须是16-bit单声道文件")
            if wf.getframerate() != INPUT_SAMPLE_RATE:
                 print(f"警告: 输入WAV采样率({wf.getframerate()})与配置({INPUT_SAMPLE_RATE})不符。")

            def audio_generator():
                print("\n🎤 [讯飞ASR] 开始从WAV文件模拟音频流...")
                while True:
                    data = wf.readframes(CHUNK_SIZE)
                    if not data:
                        break
                    yield data
                    # 模拟实时输入的时间间隔
                    time.sleep(float(CHUNK_SIZE) / INPUT_SAMPLE_RATE)

            print("\n👂 [讯飞ASR] 正在监听和识别...")
            
            # 用于保存上一份非最终结果，因为最终结果可能只包含标点
            last_segment_text = ""
            for segment, is_final in asr_client.recognize_stream(audio_generator()):
                if is_final:
                    # 将最终的片段（通常是标点）与之前最完整的句子拼接
                    final_sentence = last_segment_text + segment
                    # 清理行，然后打印最终结果
                    print(f"{' ' * 80}\r✅ 最终识别: {final_sentence}")
                    last_segment_text = "" # 为下一句做准备
                else:
                    last_segment_text = segment
                    print(f"   ... 中间结果: {segment}", end='\r')

    except Exception as e:
        print(f"发生错误: {e}")
    finally:
        print("\n\n流程结束。")

if __name__ == "__main__":
    main()
