import os,sys,time,threading,requests
from audio import start_recording, detect_keyword
from language_recognize import test_one
import cv2
from PIL import Image,ImageDraw,ImageFont
from xinghou_ImageAPI import *
from xinghou_tts import *
from audio import start_recording, detect_keyword
from language_recognize import test_one

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import (
    dog, clear_bottom,clear_top,lcd_draw_string, display, splash, 
    line_break, scroll_text_on_lcd, draw_offline, draw, lcd_rect,
    font2, Button, get_font,la,font1,font3
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

def take_photo():
    print("take a photo")
    time.sleep(0.5)
    cap=cv2.VideoCapture(0)
    cap.set(3,320)
    cap.set(4,240)
    path = "./pic_comprehension/"  
    ret, image = cap.read()
    filename = "rec"
    cv2.imwrite(path + filename + ".jpg", image)
    image = cv2.resize(image, (320, 240))
    b, g, r = cv2.split(image)
    image = cv2.merge((r, g, b))
    image = cv2.flip(image, 1)
    imgok = Image.fromarray(image)
    display.ShowImage(imgok)
    time.sleep(1)
    cap.release()
    cv2.destroyAllWindows()
    print("camera close")

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

                take_photo()
                time.sleep(1)
                sctext = "正在识别" if la == 'cn' else "Identifying"

                lcd_draw_string(draw, 30, 20, sctext, color=(0, 255, 255), scale=font2, mono_space=False)
                display.ShowImage(splash)

                if la == 'cn':
                    mymytext = xinghou_Image(content)#图像描述 image description
                else:
                    mymytext = dogGPT_Image_en(content)
                time.sleep(1)

                clear_top()
                image_list = line_break(mymytext)
                print(image_list)
                retext = image_list

                lcd_rect(0,40,320,290,splash_theme_color,-1)
                draw.rectangle((20,30,300,80), splash_theme_color, 'white',width=3)

                ananan = "正在回答" if la == 'cn' else "Answering now"
                lcd_draw_string(draw,35,40, ananan, color=(255,0,0), scale=font3, mono_space=False)


                lcd_draw_string(
                draw,
                10,
                111,
                retext, 
                color=(255, 255, 255),
                scale=font1,
                mono_space=False,
                )
                display.ShowImage(splash)

                mylines = len(retext.split("\n"))  
                ticks = 0.3
                if mylines > 6:
                    scroll_text_on_lcd(retext, 10, 111, 6, ticks)

                try:
                    Xinghou_speaktts(mymytext)#播放音频 play mp3
                except:
                    pass
            if content == 0:
               break

        time.sleep(0.1)  
else:
    draw_offline()
    while True:
        time.sleep(0.1)


