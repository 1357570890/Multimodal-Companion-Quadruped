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
            ("可以说出追踪的物体", 150),
            ("追踪xx物体", 170),
            ("追踪xx上方的物体", 190),
            ("此案例需要注册账号才能使用", 210),
        ]
    else:
       text_lines = [
            ("Can identify the tracked object", 150),
            ("Track xx object" , 170),
            ("This case requires registering ", 190),
            ("an account to use",210)

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
    #print("act"+str(act))
    if act == 0:#空的
        return False

    command_actions_cn = [
                 '几种', #采用模糊识别 Using fuzzy recognition
                 '多少',
                 '什么',
                 '追踪',
                 '最终',
        ]


    command_actions_en = [
                 'much',
                 'many',
                 'what',
                 'track',
                 'TRACK',
                 'Much',
                 'Many',
                 'What',
                 'Track',
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
        if mincmd==1 or mincmd == 2 or mincmd == 3 or mincmd == 6 or mincmd == 7 or mincmd == 8: #调用拍照视觉！
            time.sleep(1)
            os.system("python3 ./demos/speech_AI_food/speech_picture.py "+act)
            os._exit(0) #退出此程序 ,自动也会退出 Exit thread first
        
        elif mincmd==4 or mincmd==5 or mincmd==9: #追踪 track
            time.sleep(1)
            os.system("python3 ./demos/speech_AI_food/speech_ai_food_image.py "+act)
            os._exit(0) #退出此程序 ,自动也会退出 Exit thread first



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
                    scroll_text_on_lcd(display_text, 10, 111, 6, tick)
                
               
                if not actions_AI(content):
                    continue  

            if content == 0:
               break

        time.sleep(0.1)  
else:
    draw_offline()
    while True:
        time.sleep(0.1)

