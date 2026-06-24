from dog_record import * #唤醒并录音 Wake up and record
from dog_speakiat_en import * #音频识别 Audio recognition
from DAgent_Image_en import * #动作编排 choreography

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
    try:
        Dog_speaktts(response)
        huanxin = 0
    except:
        huanxin = 0
        


#Start button listening thread
button_thread = threading.Thread(target=button_listener)
button_thread.daemon = True  
button_thread.start()


def play_agent_image():
    print("start")
    global response,huanxin
    while True:
        if detect_keyword() and huanxin == 0:
            huanxin = 1
            clear_top()

            if os.path.exists('./demos/dog_agent/myrec.wav'):
                os.remove('./demos/dog_agent/myrec.wav')
            time.sleep(0.2)

            try:
                start_recording()
                time.sleep(0.2)
                rectext = test_one()#进行语音识别 Perform speech recognition
                #print(rectext)
            except:
                display_text = "Cannot hear command,try again"
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
                continue

            #rectext = 'If you see yellow, dance; otherwise, lie down'
            if rectext != "":
                draw.rectangle([(0, 111), (320, 240)], fill=splash_theme_color)
                speech_list = line_break(rectext)
                ##print(speech_list)
               
                display_text = speech_list

                take_photo() #调用摄像头拍照  Call the camera to take photos
                time.sleep(0.5)
                
                #进行动作编排 Perform action choreography
                try:
                    agent_plan_output = eval(Dog_agent_plan_Image_en(rectext))
                    print('The specific image intelligent agent choreography actions are as follows\n', agent_plan_output)

                    response = agent_plan_output['response'] # 获取机器人想对我说的话 Get what the robot wants to say to me
                    print('Start speech synthesis and play:'+response)
                except:
                    draw.rectangle([(0, 111), (320, 240)], fill=splash_theme_color)
                    display_text = "try again..."
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
                

                #开启语音线程,使其能边动边用 Activate the voice thread so that it can be used while moving
                tts_thread = threading.Thread(target=Speak_Vioce)
                tts_thread.daemon = True  
                tts_thread.start()

                for each in agent_plan_output['function']: # 运行智能体规划编排的每个函数 Run each function of intelligent agent planning and orchestration
                    try: #有异常往下接着运行 There is an abnormality and it continues to run downwards
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
                "No information was recognized, try again", 
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
        play_agent_image()

    else:
        draw_offline()
        while True:
            time.sleep(0.1)