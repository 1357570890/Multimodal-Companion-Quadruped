import os,sys,time,threading,requests
from audio import start_recording, detect_keyword
from language_recognize import test_one
from creat_img import *
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import (
    dog, clear_bottom,clear_top,lcd_draw_string, display, splash, 
    line_break, scroll_text_on_lcd, draw_offline, draw, lcd_rect,
    font2, Button, get_font,la,font3,font1
)


splash_theme_color = (15,21,46)
font2 = get_font(16)
button=Button()


def button_listener():
    while True:
        if button.press_b():
            print("Button B pressed, exiting...")
            os._exit(0) 
        time.sleep(0.1)


#Start button listening thread
button_thread = threading.Thread(target=button_listener)
button_thread.daemon = True  
button_thread.start()


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
                
                #big model
                sctext = "正在识别" if la == 'cn' else "Identifying"
                lcd_draw_string(draw, 30, 40, sctext, color=(0, 255, 255), scale=font2, mono_space=False)
                display.ShowImage(splash)
                
                gpt_draw(content)
                
            if content == 0:
               break

        time.sleep(0.1)  
else:
    draw_offline()
    while True:
        time.sleep(0.1)

