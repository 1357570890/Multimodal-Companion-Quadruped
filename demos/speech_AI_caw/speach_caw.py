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
import PID


#初始化pid init pid
Px = 0.25 #0.35
Ix = 0
Dx = 0.0001
X_Middle_error = 160 #图像X轴中心 #Image X-axis center
X_track_PID = PID.PositionalPID(Px, Ix, Dx) 

pos = 210 #根据夹取的木块大小调节 0：完全打开 #Adjust the size of the clamped wooden block to 0: fully open

g_dog = XGO(port='/dev/ttyAMA0',version="xgolite")


g_dog.translation(['z'],[75])
time.sleep(0.3)
g_dog.attitude(['p'],[15])
time.sleep(0.3)
g_dog.pace('slow')



red=(255,0,0)
green=(0,255,0)
blue=(0,0,255)
yellow=(255,255,0)

button=Button()



g_mode=1 
 
mode=1 
color_lower = np.array([0, 43, 46])
color_upper = np.array([10, 255, 255])

def limit_fun(input,min,max):
    if input < min:
        input = min
    elif input > max:
        input = max
    return input


def change_color(colorball = 'red'):
    global color_lower,color_upper,mode
    if colorball=='red':  #red
        color_lower = np.array([0, 70, 72])
        color_upper = np.array([7, 255, 255])
        mode = 1
    elif colorball=='green': #green
        color_lower = np.array([54, 109, 78])
        color_upper = np.array([77, 255, 255])
        mode = 2
    elif colorball=='blue':   #blue
        color_lower = np.array([100, 150, 100])
        color_upper = np.array([120, 255, 255])
        mode = 3
    elif colorball=='yellow':   #yellow
        color_lower = np.array([26, 100, 91])
        color_upper = np.array([32, 255, 255])
        mode = 4


#-----------------------COMMON INIT-----------------------
cap=cv2.VideoCapture(0)
cap.set(3,320)
cap.set(4,240)


t_start = time.time()
fps = 0
color_x = 0
color_y = 0
color_radius = 0
step = 0 #用于记录到达目标的时间点 Used to record the time point of arrival at the target

change_color(sys.argv[1])#传进参数

try:
    while 1:
        ret, frame = cap.read()
        frame_ = cv2.GaussianBlur(frame,(5,5),0)                    
        hsv = cv2.cvtColor(frame,cv2.COLOR_BGR2HSV)
        mask = cv2.inRange(hsv,color_lower,color_upper)  
        mask = cv2.erode(mask,None,iterations=2)
        mask = cv2.dilate(mask,None,iterations=2)
        mask = cv2.GaussianBlur(mask,(3,3),0)     
        cnts = cv2.findContours(mask.copy(),cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)[-2] 

        
        if g_mode == 1:
            if len(cnts) > 0:
                cnt = max (cnts, key = cv2.contourArea)
                (color_x,color_y),color_radius = cv2.minEnclosingCircle(cnt)
                if color_radius > 10:
                    cv2.circle(frame,(int(color_x),int(color_y)),int(color_radius),(255,0,255),2)  
                           
                    X_track_PID.SystemOutput = color_x #X 
                    X_track_PID.SetStepSignal(X_Middle_error)
                    X_track_PID.SetInertiaTime(0.01, 0.1)               
                    x_real_value = int(X_track_PID.SystemOutput)
                    x_real_value = limit_fun(x_real_value ,-18,18)
                    g_dog.move('y',x_real_value)

                    if color_y > 205 or color_y ==0 :
                        g_dog.move('x',0) 
                    else :
                        g_dog.move('x',10) 

                #停止追踪，使用夹爪夹取  Stop tracking and use the gripper to pick it up
                if color_y > 205 :
                    if abs(color_x-160)<15:###6
                        step = step+1
                    else :
                        step = 0
                    if step > 5:
                        g_dog.stop()
                        g_mode = 2 #进入夹爪夹取 Enter the gripper for retrieval
                        print([color_x,color_y])

            else:
                color_x = 0
                color_y = 0
                g_dog.stop()
            
            #print([color_x,color_y])
            cv2.putText(frame, "X:%d, Y%d" % (int(color_x), int(color_y)), (40,40), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0,255,255), 3)
            t_start = time.time()
            fps = 0

        elif g_mode == 2: #夹取运动 Pinching motion
            g_dog.claw(0)
            time.sleep(1)

            g_dog.motor([52,53],[19,6])
            time.sleep(0.5)
            g_dog.motor([52,53],[-10,80]) #直接角度控制吧 #g_dog.arm(armx,-85)  #-45
            time.sleep(2)

            g_dog.claw(pos)
            time.sleep(1.5)
            g_dog.motor([52,53],[20,-20]) #g_dog.arm(armx,100) 
            time.sleep(0.5)
            g_dog.attitude(['p'],[0]) #让狗站立  Make the dog stand up
            time.sleep(0.5)
            g_dog.motor([52,53],[-13,-20]) #g_dog.arm(armx,100) 
            time.sleep(0.5)
            
            time.sleep(0.5)
            g_dog.turn(10) 
            time.sleep(6)
            g_dog.stop()

            
            time.sleep(1.5)
            g_dog.claw(0)#松开夹取物 Release the clamp to retrieve the item

            time.sleep(1)
            g_dog.claw(127)

            time.sleep(1)
            # g_dog.reset()
            # time.sleep(0.5)
            # g_dog.attitude(['p'],[15])
            # time.sleep(0.2)
            # g_dog.translation(['z'],[75])
            # time.sleep(0.3)
            # step = 0 #目标清0  Target Clear 0
            # g_dog.pace('slow')
            # g_mode = 1 

            g_dog.reset()
            cap.release()
            os.system("python3 ./demos/speech_AI_caw/speech_AI_caw.py")
            os._exit(0)

        else:
            fps = fps + 1
            mfps = fps / (time.time() - t_start)
            cv2.putText(frame, "FPS " + str(int(mfps)), (40,40), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0,255,255), 3)

        
        b,g,r = cv2.split(frame)
        img = cv2.merge((r,g,b))
        if mode==1:
            cv2.rectangle(img, (290, 10), (320, 40), red, -1)
        elif mode==2:
            cv2.rectangle(img, (290, 10), (320, 40), green, -1)
        elif mode==3:
            cv2.rectangle(img, (290, 10), (320, 40), blue, -1)
        elif mode==4:
            cv2.rectangle(img, (290, 10), (320, 40), yellow, -1)
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
            os.system("python3 ./demos/speech_AI_caw/speech_AI_caw.py")
            os._exit(0)


except:
    g_dog.stop()
    cap.release()
    cv2.destroyAllWindows() 
    os.system("python3 ./demos/speech_AI_caw/speech_AI_caw.py")
    os._exit(0)
