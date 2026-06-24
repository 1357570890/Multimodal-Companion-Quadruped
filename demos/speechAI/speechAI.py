import os,sys,time,threading,requests
from audio import start_recording, detect_keyword
from language_recognize import test_one

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import (
    dog, clear_bottom,clear_top,lcd_draw_string, display, splash, 
    line_break, scroll_text_on_lcd, draw_offline, draw, 
    font2, Button, get_font,la
)

#define base fuctions
font2 = get_font(16)
button=Button()
'''
   Function Name:show_words_Dog
   Function Content:Cue word display 
'''


            
def open_AI_play():
    clear_bottom()
    
    if la == 'cn':
        text_lines = [
            ("颜色跟踪|颜色巡线|小球跟随|目标检测", 150),
            ("车牌识别|二维码识别|年龄检测", 170),
            ("背景分离|人脸跟踪|手势识别|手势跟踪", 190),
            ("隔空控制|骨骼识别|三维物体检测", 210),
        ]
    else:
       text_lines = [

            ("color track|color line|ball follow", 150),
            ("target|license plate|QR|Age" , 170),
            ("Background| Space control|3D object", 190),
            ("face track|Gesture track|Bone",210)

        ]
    
    for text, y in text_lines:
        lcd_draw_string(draw, 10, y, text, color=(0, 255, 255), scale=font2, mono_space=False)
    
    display.ShowImage(splash)



tt_stop = 0#线程停止标志 Thread stop flag

def button_listener():
    global tt_stop
    while True:
        if button.press_b() :
            print("Button B pressed, exiting...")
            os._exit(0) 
        if tt_stop == 1:
            break
        time.sleep(0.1)


#Start button listening thread
button_thread = threading.Thread(target=button_listener)
button_thread.daemon = True  
button_thread.start()

def actions_AI(act):
    global tt_stop
    if act == 0:
        return False

    command_actions_cn = [
            "颜色跟踪",
            "颜色巡线",
            "小球跟随",
            "目标检测",
            "车牌识别",
            "二维码识别",
            "年龄检测",
            "背景分离",
            "隔空控制",
            "三维物体检测",
            "人脸跟踪",
            "手势跟踪",
            "骨骼识别",
            "手势识别",
            "巡线",
            "车牌",
            "小球"
            "人脸",
        ]


    command_actions_en = [
             "color track",
              "color line",
              "ball follow",
              "target",
              "license plate",
              "QR",
              "Age",
              "Background",
              "Space control",
              "3D object",
              "face track",
              "Gesture track",
              "Bone",
              ]

    if la == 'cn':
        commands = command_actions_cn
    else :
        commands = command_actions_en
        
    mincmd=0
    minindex=len(commands)
    mark=False

    for i,cmd in enumerate(commands):
        ix=act.find(cmd)
        if ix>-1 and ix<=minindex:
            mincmd=i+1
            minindex=ix
            mark=True


    if mark:
        tt_stop = 1 #先退出线程 Exit thread first
        if mincmd==1 :
            time.sleep(1)
            os.system("python3 ./demos/color.py ") 
            os._exit(0)  

        elif mincmd==2 or mincmd==15:
            time.sleep(1)
            os.system("python3 ./demos/myfollow_line.py ")
            os._exit(0)  

        elif mincmd==3 or mincmd == 17:
            time.sleep(1)
            os.system("python3 ./demos/myBall_Follow.py ")
            os._exit(0)  

        elif mincmd==4:
            time.sleep(1)
            os.system("python3 ./demos/myyolo.py ")
            os._exit(0)  

        elif mincmd==5 or mincmd == 16:
            time.sleep(1)
            os.system("python3 ./demos/mycamera_license.py ")
            os._exit(0)  

        elif mincmd==6:
            time.sleep(1)
            os.system("python3 ./demos/myqr_sport.py ")
            os._exit(0)  

        elif mincmd==7:
            time.sleep(1)
            os.system("python3 ./demos/mySexandAge.py ")
            os._exit(0)  

        elif mincmd==8:
            time.sleep(1)
            os.system("python3 ./demos/mysegmentation.py ")
            os._exit(0)  

        elif mincmd==9:
            time.sleep(1)
            os.system("python3 ./demos/hp.py ")
            os._exit(0)  

        elif mincmd==10:
            time.sleep(1)
            os.system("python3 ./demos/myObjectron.py ")
            os._exit(0)  

        elif mincmd==11 or mincmd == 18:
            time.sleep(1)
            os.system("python3 ./demos/face_decetion.py ")
            os._exit(0)                  

        elif mincmd==12:
            time.sleep(1)
            os.system("python3 ./demos/myFingerCtrl.py ")
            os._exit(0)  

        elif mincmd==13:
            time.sleep(1)
            os.system("python3 ./demos/pose_dog.py ")
            os._exit(0)  

        elif mincmd==14:
            time.sleep(1)
            os.system("python3 ./demos/hands.py ")
            os._exit(0) 


        time.sleep(3)

    else:
        print("Command not found")
        error_msg = "错误命令词，请再次尝试" if la == "cn" else "Wrong command. Please try again"
        clear_top()
        lcd_draw_string(
            draw, 60, 70, error_msg, color=(255, 0, 0), scale=font2, mono_space=False
        )
        display.ShowImage(splash)
        time.sleep(1)
        dog.reset()
        return False

#Test the network
net = False
try:
    html = requests.get("http://www.baidu.com", timeout=2)
    net = True
except Exception as e:
    print(f"Network check failed: {e}")
    net = False

#Record and control the dog
if net:
    while True:
        open_AI_play()
        if detect_keyword():
            clear_top()
            start_recording()
            content = test_one()

            if content != "":
                clear_top()
                speech_list = line_break(content)
                print(speech_list)
               
                if la == "en":
                    english_only = ''.join(char for char in speech_list if ord(char) < 128)
                    display_text = english_only
                else:
                    display_text = speech_list
                    
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
                
                lines = len(display_text.split("\n"))  
                tick = 0.3
                if lines > 6:
                    scroll_text_on_lcd(re_e, 10, 111, 6, tick)
                
               
                if not actions_AI(content):
                    continue  

            if content == 0:
               break

        time.sleep(0.1)  
else:
    draw_offline()
    while True:
        time.sleep(0.1)

