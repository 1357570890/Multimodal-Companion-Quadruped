import os,time,sys
import xgolib
import xgoscreen.LCD_2inch as LCD_2inch
from key import Button
from PIL import Image,ImageDraw,ImageFont

import sys
sys.path.append("..")
import uiutils
la=uiutils.load_language()

os.system('sudo chmod 777 /dev/ttyAMA0')
xgo = xgolib.XGO(port = '/dev/ttyAMA0',version='xgomini')

button=Button()

display = LCD_2inch.LCD_2inch()
try:
  display.Init()
except:
        pass
display.clear()
splash = Image.new("RGB",(320,240),"black")
display.ShowImage(splash)

#字体载入
font1 = ImageFont.truetype("/home/pi/model/msyh.ttc",35)
font2 = ImageFont.truetype("/home/pi/model/msyh.ttc",30)

def lcd_text(x,y,content):
        draw = ImageDraw.Draw(splash)
        draw.text((x,y),content,fill = "WHITE",font=font1)
        display.ShowImage(splash)

def lcd_text1(x,y,content):
        draw = ImageDraw.Draw(splash)
        draw.text((x,y),content,fill = "BLUE",font=font2)
        display.ShowImage(splash)        

fm1 = xgo.read_firmware()
fm2 = xgo.read_lib_version()
fm3 = "XgoYahboom" #这个改成yahboom的镜像

lcd_text1(20,10,la['DEVICE']['DEVICEINFO'])
lcd_text(20,50,"bin:")
lcd_text(90,50,fm1)
lcd_text(20,110,"python:")
lcd_text(150,110,fm2)
lcd_text(20,170,"os:")
lcd_text(70,170,fm3)

while True:
        if button.press_b():
                xgo.reset()
                sys.exit()
                break
