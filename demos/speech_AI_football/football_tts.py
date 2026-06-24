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
import os,sys
from subprocess import Popen


current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)
from API_KEY import *



STATUS_FIRST_FRAME = 0  
STATUS_CONTINUE_FRAME = 1  
STATUS_LAST_FRAME = 2  

wsParam = ''

class Ws_Param(object):
    
    def __init__(self, APPID, APIKey, APISecret, Text):
        self.APPID = APPID
        self.APIKey = APIKey
        self.APISecret = APISecret
        self.Text = Text

        
        self.CommonArgs = {"app_id": self.APPID}
        
        self.BusinessArgs = {"aue": "lame", "sfl":1,"auf": "audio/L16;rate=16000", "vcn": "xiaoyan", "tte": "utf8"}
        self.Data = {"status": 2, "text": str(base64.b64encode(self.Text.encode('utf-8')), "UTF8")}
        
        #self.Data = {"status": 2, "text": str(base64.b64encode(self.Text.encode('utf-16')), "UTF8")}


    def create_url(self):
        url = 'wss://tts-api.xfyun.cn/v2/tts'
       
        now = datetime.now()
        date = format_date_time(mktime(now.timetuple()))

       
        signature_origin = "host: " + "ws-api.xfyun.cn" + "\n"
        signature_origin += "date: " + date + "\n"
        signature_origin += "GET " + "/v2/tts " + "HTTP/1.1"
        
        signature_sha = hmac.new(self.APISecret.encode('utf-8'), signature_origin.encode('utf-8'),
                                 digestmod=hashlib.sha256).digest()
        signature_sha = base64.b64encode(signature_sha).decode(encoding='utf-8')

        authorization_origin = "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"" % (
            self.APIKey, "hmac-sha256", "host date request-line", signature_sha)
        authorization = base64.b64encode(authorization_origin.encode('utf-8')).decode(encoding='utf-8')
        
        v = {
            "authorization": authorization,
            "date": date,
            "host": "ws-api.xfyun.cn"
        }
        
        url = url + '?' + urlencode(v)
        # print("date: ",date)
        # print("v: ",v)
        
        return url

def on_message(ws, message):
    try:
        message =json.loads(message)
        code = message["code"]
        sid = message["sid"]
        audio = message["data"]["audio"]
        audio = base64.b64decode(audio)
        status = message["data"]["status"]
        #print(message)
        if status == 2:
            #print("ws is closed")
            ws.close()
        if code != 0:
            errMsg = message["message"]
            print("sid:%s call error:%s code is:%s" % (sid, errMsg, code))
        else:
            with open('./demos/speech_AI_football/Football.mp3', 'ab') as f:
                f.write(audio)
                #print("iconm")

    except Exception as e:
        print("receive msg,but parse exception:", e)




def on_error(ws, error):
    print("### error:", error)



def on_close(ws):
    print("### closed ###")



def on_open(ws):
    def run(*args):
        d = {"common": wsParam.CommonArgs,
             "business": wsParam.BusinessArgs,
             "data": wsParam.Data,
             }
        d = json.dumps(d)
        #print("------>开始发送文本数据")
        ws.send(d)
        if os.path.exists('./demos/speech_AI_football/Football.mp3'):
            os.remove('./demos/speech_AI_football/Football.mp3')

    thread.start_new_thread(run, ())


def Football_speaktts(context="这个是合成音频程序"):
    global wsParam
    
    wsParam = Ws_Param(APPID=XINGHOU_APPID,
                       APISecret=XINGHOU_APISecret,
                       APIKey=XINGHOU_KEY,
                       Text=context)
    websocket.enableTrace(False)
    wsUrl = wsParam.create_url()
    ws = websocket.WebSocketApp(wsUrl, on_message=on_message, on_error=on_error, on_close=on_close)
    ws.on_open = on_open
    ws.run_forever(sslopt={"cert_reqs": ssl.CERT_NONE})

    
    proc = Popen("sudo mplayer ./demos/speech_AI_football/Football.mp3", shell=True)
    proc.wait()
    time.sleep(0.5)

