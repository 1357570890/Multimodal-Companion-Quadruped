#此为录音和唤醒文件
import pyaudio,wave,random
import wave
import numpy as np
from scipy import fftpack
import os,time
from datetime import datetime
import os,sys
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
sys.path.append(parent_dir)
from auto_platform import AudiostreamSource, play_command, default_libpath
from libnyumaya import AudioRecognition, FeatureExtractor
from uiutils import Button,mic_logo,mic_purple,splash_theme_color,clear_top,draw,splash,display,la,lcd_draw_string,get_font
font1=get_font(17)
button=Button()
# 录音参数 Recording parameters
CHUNK = 1024 #1024
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 16000
SAVE_FILE = "./demos/dog_agent/myrec.wav"
SAVE_KEYWORD="keyword_audio.wav"
ENDLAST = 30
start_threshold = 60000
end_threshold = 40000
endlast = 10


# 关键词检测参数 Keyword detection parameters
KEYWORD_MODEL_PATH = "./demos/src/lulu_v3.1.907.premium"
KEYWORD_THRESHOLD = 0.7
PLAY_COMMAND = "aplay"  # Linux 播放命令，Windows 可改为 "start" Linux playback command, Windows can change to 'start'

def calculate_volume(data):

    rt_data = np.frombuffer(data, dtype=np.int16)
    fft_temp_data = fftpack.fft(rt_data, rt_data.size, overwrite_x=True)
    fft_data = np.abs(fft_temp_data)[0: fft_temp_data.size // 2 + 1]
    return sum(fft_data) // len(fft_data)

def draw_cir(ch):
    if ch > 15:
        ch = 15
    clear_top()
    draw.bitmap((145, 40), mic_logo, mic_purple)
    radius = 4
    cy = 60
    centers = [(62, cy), (87, cy), (112, cy), (210, cy), (235, cy), (260, cy)]
    for center in centers:
        random_offset = random.randint(0, ch)
        new_y = center[1] + random_offset
        new_y2 = center[1] - random_offset

        draw.line([center[0], new_y2, center[0], new_y], fill=mic_purple, width=11)

        top_left = (center[0] - radius, new_y - radius)
        bottom_right = (center[0] + radius, new_y + radius)
        draw.ellipse([top_left, bottom_right], fill=mic_purple)
        top_left = (center[0] - radius, new_y2 - radius)
        bottom_right = (center[0] + radius, new_y2 + radius)
        draw.ellipse([top_left, bottom_right], fill=mic_purple)


def draw_wave(ch):
    ch = min(ch, 10)
    clear_top()
    draw.bitmap((145, 40), mic_logo, mic_purple)
    
    wave_params = [
        {"start_x": 40, "start_y": 32, "width": 80, "height": 50},
        {"start_x": 210, "start_y": 32, "width": 80, "height": 50}
    ]
    
    for params in wave_params:
        draw_single_wave(
            params["start_x"], 
            params["start_y"], 
            params["width"], 
            params["height"], 
            ch
        )

def draw_single_wave(start_x, start_y, width, height, ch):
    y_center = height // 2
    current_y = y_center
    previous_point = (start_x, y_center + start_y)
    
    if start_x > 200: 
        draw.rectangle(
            [(start_x - 1, start_y), (start_x + width, start_y + height)],
            fill=splash_theme_color,
        )
    
    x = 0
    while x < width:
        seg_len = random.randint(7, 25)
        gap_len = random.randint(4, 20)
        
        for _ in range(seg_len):
            if x >= width: break
            current_y = max(0, min(height - 1, current_y + random.randint(-ch, ch)))
            current_point = (x + start_x, current_y + start_y)
            draw.line([previous_point, current_point], fill=mic_purple)
            previous_point, x = current_point, x + 1
        
        for _ in range(gap_len):
            if x >= width: break
            current_point = (x + start_x, y_center + start_y)
            draw.line([previous_point, current_point], fill=mic_purple, width=2)
            previous_point, x = current_point, x + 1
            

def detect_keyword():
    # 初始化关键词检测 Initialize keyword detection
    audio_stream = AudiostreamSource()
    libpath = "./demos/src/libnyumaya_premium.so.3.1.0"
    extractor = FeatureExtractor(libpath)
    detector = AudioRecognition(libpath)
    extractor_gain = 1.0
    keyword_id = detector.addModel(KEYWORD_MODEL_PATH, KEYWORD_THRESHOLD)
    bufsize = detector.getInputDataSize()
    audio_stream.start()
 
    print("Waiting for keyword...")
    while True:
        # 读取音频数据 Read audio data
        frame = audio_stream.read(bufsize * 2, bufsize * 2)
        if not frame:
            continue

        # 绘制波形（直接使用当前帧） Draw waveform (using the current frame directly)
        rt_data = np.frombuffer(frame, dtype=np.int16)
        fft_temp_data = fftpack.fft(rt_data, rt_data.size, overwrite_x=True)
        fft_data = np.abs(fft_temp_data)[0:fft_temp_data.size // 2 + 1]
        vol = sum(fft_data) // len(fft_data)
        draw_wave(int(vol / 10000))  # 调整除数使波形显示合适 Adjust the divisor to make the waveform display appropriate
        display.ShowImage(splash)

        # 关键词检测 keyword spotting
        features = extractor.signalToMel(frame, extractor_gain)
        prediction = detector.runDetection(features)

        if prediction == keyword_id:
            now = datetime.now().strftime("%d.%b %Y %H:%M:%S")
            print(f"Keyword detected: {now}")
            os.system(f"{PLAY_COMMAND} ./demos/src/ding.wav")
            return True


quitmark = 0
automark = True 
def start_recording(timel = 3,save_file=SAVE_FILE):
    global automark,quitmark
    start_threshold = 120000
    end_threshold = 60000
    endlast = 15
    max_record_time = 20 
    
    CHUNK = 1024
    FORMAT = pyaudio.paInt16
    CHANNELS = 1
    RATE = 16000
    WAVE_OUTPUT_FILENAME = save_file 

    
    if automark:
        p = pyaudio.PyAudio()       
        stream_a = p.open(format=FORMAT,
                        channels=CHANNELS,
                        rate=RATE,
                        input=True,
                        frames_per_buffer=CHUNK)
        frames = []
        start_luyin = False
        break_luyin = False
        data_list =[0]*endlast
        sum_vol=0
        start_time = None

        while not break_luyin:
            if not automark or quitmark == 1:
                break

            data = stream_a.read(CHUNK, exception_on_overflow=False)
            rt_data = np.frombuffer(data, dtype=np.int16)
            fft_temp_data = fftpack.fft(rt_data, rt_data.size, overwrite_x=True)
            fft_data = np.abs(fft_temp_data)[0 : fft_temp_data.size // 2 + 1]
            vol = sum(fft_data) // len(fft_data)
            
            data_list.pop(0)
            data_list.append(vol)
            
            print(f"Current volume: {vol}, boot threshold: {start_threshold}, End threshold: {end_threshold}")
            
            if vol > start_threshold:
                sum_vol += 1
                if sum_vol == 1:
                    print("start recording")
                    start_luyin = True
                    start_time = time.time()
            
            if start_luyin:
                elapsed_time = time.time() - start_time
                
                if all(float(i) < end_threshold for i in data_list) or elapsed_time > max_record_time:
                    print("Recording ends: Low volume or recording time exceeds the limit")
                    break_luyin = True
                    frames = frames[:-5]
            
            if start_luyin:
                frames.append(data)
            print(start_threshold, vol)
            draw_cir(int(vol / 10000))
            display.ShowImage(splash)
        print("auto end")


    if quitmark==0:
        try:
            stream_a.stop_stream()
            stream_a.close()
        except:
            pass
        p.terminate()

        wf = wave.open(WAVE_OUTPUT_FILENAME, 'wb')  
        wf.setnchannels(CHANNELS)
        wf.setsampwidth(p.get_sample_size(FORMAT))
        wf.setframerate(RATE)
        wf.writeframes(b''.join(frames))
        wf.close()
        print(f"The recording has been saved as: {WAVE_OUTPUT_FILENAME}")


    
