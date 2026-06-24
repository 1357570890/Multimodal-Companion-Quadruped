#这个是图片识别的 This is for image recognition

import os,sys,time,threading,requests
from audio import start_recording, detect_keyword
from language_recognize import test_one
import cv2
from PIL import Image,ImageDraw,ImageFont
from xinghou_ImageAPI import *
from xinghou_tts import *

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import (
    dog, clear_bottom,clear_top,lcd_draw_string, display, splash, 
    line_break, scroll_text_on_lcd, draw_offline, draw, lcd_rect,
    font2, Button, get_font,la,font1,font3
)

from picamera2 import Picamera2

splash_theme_color = (15,21,46)
font2 = get_font(16)

def take_photo():
    print("take a photo")
    time.sleep(0.5)
    picam2 = Picamera2()
    picam2.configure(
        picam2.create_preview_configuration(main={"format": "RGB888", "size": (640, 480)})
    )
    picam2.start()
    path = "./demos/speech_AI_caw/"  
    image = picam2.capture_array() 
    filename = "caw"
    cv2.imwrite(path + filename + ".jpg", image)
    image = cv2.resize(image, (320, 240))
    b, g, r = cv2.split(image)
    image = cv2.merge((r, g, b))
    image = cv2.flip(image, 1)
    imgok = Image.fromarray(image)
    display.ShowImage(imgok)
    time.sleep(1)
    picam2.stop()
    picam2.close()
    cv2.destroyAllWindows()
    print("camera close")


if __name__ == '__main__':
    try:
        dog.attitude(['p'],[15])
        time.sleep(2)

        take_photo()#拍照 take_photo
        time.sleep(1)

        sctext = "正在识别" if la == 'cn' else "Identifying"

        lcd_draw_string(draw, 30, 20, sctext, color=(0, 255, 255), scale=font2, mono_space=False)
        display.ShowImage(splash)

        mymytext = xinghou_Image(sys.argv[1])#图像描述 image description
        time.sleep(1)

        dog.reset()
        clear_top()
        speech_list = line_break(mymytext)
        print(speech_list)
        display_text = speech_list

        lcd_rect(0,40,320,290,splash_theme_color,-1)
        draw.rectangle((20,30,300,80), splash_theme_color, 'white',width=3)

        ananan = "正在回答" if la == 'cn' else "Answering now"
        lcd_draw_string(draw,35,40, ananan, color=(255,0,0), scale=font3, mono_space=False)


        lcd_draw_string(
        draw,
        10,
        111,
        display_text, 
        color=(255, 255, 255),
        scale=font1,
        mono_space=False,
        )
        display.ShowImage(splash)

        lines = len(display_text.split("\n"))  
        tick = 0.3
        if lines > 6:
            scroll_text_on_lcd(display_text, 10, 111, 6, tick)


        Xinghou_speaktts(mymytext)#播放音频 PLAY AUDIO
        time.sleep(0.5)

        dog.reset()
        os.system("python3 ./demos/speech_AI_caw/speech_AI_caw.py")
        os._exit(0)
    except : 
        dog.reset()
        os.system("python3 ./demos/speech_AI_caw/speech_AI_caw.py")
        os._exit(0)


