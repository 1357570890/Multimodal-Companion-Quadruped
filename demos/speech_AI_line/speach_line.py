import os,sys,time,threading,requests,cv2
from audio import start_recording, detect_keyword
from language_recognize import test_one
from PIL import Image,ImageDraw,ImageFont
import numpy as np

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)

from uiutils import display, Button
from xgolib import XGO
import PID,HSV_Config_Two
from picamera2 import Picamera2


button=Button()

line_speed = 8 #巡线的速度 Speed of patrol line
line_color = 'blue' #yellow  blue  green  red


#初始化pid init pid
Px_line = 0.15 # move:0.25  trun 0.15
Ix_line = 0
Dx_line = 0.0001
X_line_Middle_error = 160 #图像X轴中心 #Image X-axis center
X_line_track_PID = PID.PositionalPID(Px_line, Ix_line, Dx_line) 


#识别障碍物pid Identify obstacle PID
Px_food = 0.25 # move:0.25  trun 0.15
Ix_food = 0
Dx_food = 0.0001
X_food_Middle_error = 160 #图像X轴中心 #Image X-axis center
X_food_track_PID = PID.PositionalPID(Px_food, Ix_food, Dx_food) 
food_error = 15 #允许的误差 Permissible error
g_mode = 1
pos = 220
g_step = 0 #定点计算 fixed-point computation

# armx = 129

g_dog = XGO(port='/dev/ttyAMA0',version="xgolite")
g_dog.attitude(['p'],[15])
time.sleep(0.2)
g_dog.translation(['z'],[75])
time.sleep(0.3)

g_dog.pace('slow') #巡线的速度 line speed slow
time.sleep(.2)



#要识别的颜色阈值 Color threshold to be recognized
color_hsv  = {"red"   : ((0, 70, 72), (7, 255, 255)),
              "green" : ((54, 109, 78), (77, 255, 255)),
              "blue"  : ((92, 100, 62), (121, 251, 255)),
              "yellow": ((26, 100, 91), (32, 255, 255))}



cap=cv2.VideoCapture(0)
cap.set(3,320)
cap.set(4,240)
update_hsv = HSV_Config_Two.update_hsv()


def change_color(colorball = 'red'):
    global line_color
    if colorball=='red':  #red
        line_color = 'red'
    elif colorball=='green': #green
        line_color = 'green'
    elif colorball=='blue':   #blue
       line_color = 'blue'
    elif colorball=='yellow':   #yellow
        line_color = 'yellow'



#抓取清障运动 Grab and clear obstacles movement
def Caw_sport():
    g_dog.claw(0)
    time.sleep(1)

    g_dog.motor([52,53],[19,6])
    time.sleep(0.5)
    g_dog.motor([52,53],[-20,80]) #直接角度控制吧 #g_dog.arm(armx,-85)  #-45
    time.sleep(2)


    g_dog.claw(pos)
    time.sleep(1.5)
    g_dog.motor([52,53],[20,-20]) #g_dog.arm(armx,100) 
    time.sleep(0.5)
    g_dog.attitude(['p'],[0]) #让狗站立  Make the dog stand up
    time.sleep(0.5)
    g_dog.motor([52,53],[-13,-20]) #g_dog.arm(armx,100) 
    time.sleep(0.5)

    #旋转约70度 Rotate about 70 degrees
    time.sleep(0.5)
    g_dog.turn(10) 
    time.sleep(7)
    g_dog.stop()

    g_dog.move('x',20)
    time.sleep(1)
    g_dog.stop()

    time.sleep(1.5)
    g_dog.claw(0)#松开夹取物 Release the clamp to retrieve the item

    time.sleep(1)
    g_dog.claw(127)

    g_dog.motor([52,53],[70,-85]) #扔完，夹爪放下
    time.sleep(0.5)
    
    g_dog.move('x',-15)
    time.sleep(1)
    g_dog.stop()

    time.sleep(0.5)
    g_dog.turn(-10) 
    time.sleep(7)
    g_dog.stop()


def limit_fun(input,min,max):
    if input < min:
        input = min
    elif input > max:
        input = max
    return input


#寻找颜色线 Search for color lines
def Find_color_line(colors_dict,num_len):
    global line_color
    if num_len == 0:
        return -1 #没找到 no find
    for i in range(num_len):
        if colors_dict[i] == line_color:
            return i #找到了 find!
    return -1 #没找到 no find

#寻找障碍物 Find obstacles
def Find_color_food(colors_dict,num_len):
    global line_color
    if num_len == 0:
        return -1 
    for i in range(num_len):
        if colors_dict[i] != line_color:
            return i 
    return -1 


def color_line():
    global color_hsv
    global g_mode,g_step,armx
    while True:
        ret,frame = cap.read()
        #frame = cv2.flip(frame, 1)
        
        frame, binary,hsvname,xylist=update_hsv.get_contours(frame,color_hsv)
        unique_colors = list(dict.fromkeys(hsvname))

        if line_color == 'blue':
            cv2.putText(frame, line_color, (40,40), cv2.FONT_HERSHEY_SIMPLEX, 1, (255,0,0), 2)
        elif line_color == 'green':
            cv2.putText(frame, line_color, (40,40), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,255,0), 2)
        elif line_color == 'red':
            cv2.putText(frame, line_color, (40,40), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,0,255), 2)
        elif line_color == 'yellow':
            cv2.putText(frame, line_color, (40,40), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,255,255), 2)
        else:
            cv2.putText(frame, line_color, (40,40), cv2.FONT_HERSHEY_SIMPLEX, 1, (255,0,255), 2)

        
        # 根据列表的长度来决定如何分割字符串 Determine how to split the string based on the length of the list
        num_colors = len(unique_colors)

 
        # print(unique_colors)
        # print(xylist)
        index = Find_color_line(unique_colors,num_colors)#寻找巡线颜色 Search for patrol colors

        if num_colors>1 or (num_colors ==1 and index<0): #不止巡线颜色 Not just patrol colors
            food_step = Find_color_food(unique_colors,num_colors)
            
            #夹取清障 Pick up and clear obstacles
            if g_mode == 1: 
                food_color_x = xylist[food_step][0]
                food_color_y = xylist[food_step][1]

                #print(food_color_x,food_color_y)

                X_food_track_PID.SystemOutput = food_color_x #X 
                X_food_track_PID.SetStepSignal(X_food_Middle_error)
                X_food_track_PID.SetInertiaTime(0.01, 0.1)               
                foodx_real_value = int(X_food_track_PID.SystemOutput)

                foodx_real_value = limit_fun(foodx_real_value ,-18,18)
                g_dog.move('y',foodx_real_value)


                if food_color_y > 205 or food_color_y ==0 :
                    g_dog.move('x',0) 
                else :
                    g_dog.move('x',8) 

                #停止追踪，使用夹爪夹取 Stop tracking and use the gripper to pick it up
                if food_color_y > 205 :
                    if abs(food_color_x-160)<food_error:
                        g_step = g_step+1
                    else :
                        g_step = 0
                    if g_step > 5:
                        g_dog.stop()
                        g_mode = 2 
                        if food_color_x < 155:
                            armx = 135
                        else :
                            armx = 129

            elif g_mode == 2:
                Caw_sport() #抓取清障运动 Grab and clear obstacles movement

                time.sleep(1)
                g_dog.reset()
                g_dog.attitude(['p'],[15])#蹲下 squat
                time.sleep(0.5)
                g_dog.translation(['z'],[75])
                time.sleep(0.3)
                g_step = 0 #目标清0 Target Clear 0
                g_dog.pace('slow')

                g_dog.move('y',-10) 
                time.sleep(2)
                g_dog.stop()#使其回归到线 

                g_mode = 1 #寻找目标 Find a target
   
        elif index >= 0: 
            # print(line_color,xylist[index]) 
            color_x = xylist[index][0]
            #color_y = xylist[index][1]
            #print(color_x,color_y)

            
            X_line_track_PID.SystemOutput = color_x 
            X_line_track_PID.SetStepSignal(X_line_Middle_error)
            X_line_track_PID.SetInertiaTime(0.01, 0.1)               
            x_line_real_value = int(X_line_track_PID.SystemOutput)

            #move
            # x_line_real_value = limit_fun(x_line_real_value ,-18,18)
            # g_dog.move('y',x_line_real_value)
            
            #trun
            x_line_real_value = limit_fun(x_line_real_value ,-150,150)
            g_dog.turn(x_line_real_value)

            g_dog.move('x',line_speed)
            
        
        else:
            g_dog.stop() #stop
           

        #显示在小车的lcd屏幕上 Display on the LCD screen of the car
        b,g,r = cv2.split(frame)
        img = cv2.merge((r,g,b))
        imgok = Image.fromarray(img)
        display.ShowImage(imgok)


        if button.press_b():
            g_dog.stop()
            g_dog.reset()
            cap.release()
            break

        if button.press_d():
            g_dog.stop()
            g_dog.reset()
            cap.release()
            os.system("python3 ./demos/speech_AI_line/speech_AI_line.py")
            os._exit(0)


        
 
try:
    change_color(sys.argv[1])
    color_line()
    
except:
    cap.release()
    g_dog.reset()
    os.system("python3 ./demos/speech_AI_line/speech_AI_line.py")
    os._exit(0)