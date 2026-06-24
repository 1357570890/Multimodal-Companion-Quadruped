# 机械狗智能体主要程序入口文件 Main program entry files for mechanical dog intelligent agents

from dog_record import * #唤醒并录音 Wake up and record
from dog_speak_iat import * #音频识别 Audio recognition
from dog_agent import * #动作编排 choreography
from dog_base_control import * #基础动作 Basic actions
from dog_caw_api import * #抓取 grab
from dog_football_api import * #踢球 play football
from dog_tts import * #语音合成并播放 Speech synthesis and playback

import os,sys,time,threading,requests
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import (
    clear_top,lcd_draw_string, display, splash, 
    line_break, scroll_text_on_lcd, draw_offline, draw, 
    font2, Button, get_font,la
)


font2 = get_font(16)
button=Button()

def button_listener():
    while True:
        if button.press_b():
            print("Button B pressed, exiting...")
            os._exit(0) 
        time.sleep(0.1)

huanxin = 0
response = ''
def Speak_Vioce():
    global response,huanxin
    Dog_speaktts(response)
    huanxin = 0


#Start button listening thread
button_thread = threading.Thread(target=button_listener)
button_thread.daemon = True  
button_thread.start()



def play_agent():
    print("start")
    global response,huanxin
    while True:
        if detect_keyword() and huanxin ==0:
            huanxin = 1
            clear_top()
            #先把存在的录音删掉 First, delete the existing recording
            if os.path.exists('./demos/dog_agent/myrec.wav'):
                os.remove('./demos/dog_agent/myrec.wav')
            time.sleep(0.2)

            try:
                start_recording()
                time.sleep(0.2)
                rectext = rec_wav_music()#进行语音识别 Perform speech recognition
                #print(rectext)
            except:
                draw.rectangle([(0, 111), (320, 240)], fill=splash_theme_color)
                display_text = "听不到指令,请重试..."
                lcd_draw_string(
                    draw,
                    10,
                    110,
                    display_text, 
                    color=(255, 255, 255),
                    scale=font2,
                    mono_space=False,
                )
                display.ShowImage(splash)
                huanxin = 0
                continue

            if rectext != "":
                draw.rectangle([(0, 111), (320, 240)], fill=splash_theme_color)
                speech_list = line_break(rectext)
                print(speech_list)
               
                display_text = speech_list

                try:
                    agent_plan_output = eval(Dog_agent_plan(rectext))#进行动作编排  Perform action choreography
                    print('智能体编排动作如下\n', agent_plan_output)
                    response = agent_plan_output['response'] # 获取机器人想对我说的话 Get what the robot wants to say to me
                    print('开始语音合成并播放：'+response)  
                except:
                    display_text = "请重试..."
                    lcd_draw_string(
                        draw,
                        10,
                        110,
                        display_text, 
                        color=(255, 255, 255),
                        scale=font2,
                        mono_space=False,
                    )
                    display.ShowImage(splash)
                    huanxin = 0
                    continue

                display_text = "Q:"+ display_text + '\n'+"A:"+response

                lcd_draw_string(
                    draw,
                    10,
                    110,
                    display_text, 
                    color=(255, 255, 255),
                    scale=font2,
                    mono_space=False,
                )
                display.ShowImage(splash)
                
                ## lines = len(display_text.split("\n"))  
                ## tick = 0.3
                ## if lines > 6:
                ##     scroll_text_on_lcd(display_text, 10, 111, 6, tick)


                #开启语音线程,使其能边动边用 Activate the voice thread so that it can be used while moving
                tts_thread = threading.Thread(target=Speak_Vioce)
                tts_thread.daemon = True  
                tts_thread.start()
                

                for each in agent_plan_output['function']: # 运行智能体规划编排的每个函数  Run each function of intelligent agent planning and orchestration
                    print('开始执行动作', each)
                    try: 
                        eval(each)
                    except:
                        continue
                
                time.sleep(0.5)

            else :
                draw.rectangle([(0, 111), (320, 240)], fill=splash_theme_color) 
                lcd_draw_string(
                    draw,
                    10,
                    110,
                    "没有识别到任何信息,请重试", 
                    color=(255, 255, 255),
                    scale=font2,
                    mono_space=False,
                )
                display.ShowImage(splash)
                time.sleep(0.5)
                huanxin = 0
            if rectext == 0:
               break




if __name__ == '__main__':
    #Test the network
    net = False
    try:
        html = requests.get("http://www.baidu.com", timeout=2)
        net = True
    except Exception as e:
        print(f"Network check failed: {e}")
        net = False
    if net:    
        play_agent()

    else:
        draw_offline()
        while True:
            time.sleep(0.1)