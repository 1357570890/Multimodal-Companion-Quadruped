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
CHUNK = 1024
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 16000
RECORD_SECONDS = 600  # 最大录音时长 Maximum recording duration
SAVE_FILE = "recorded_audio.wav"
SAVE_KEYWORD="keyword_audio.wav"
KEYWORD_MODEL_PATH = "./demos/src/lulu_v3.1.907.premium"
KEYWORD_THRESHOLD = 0.7
PLAY_COMMAND = "aplay"  

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
def start_recording(timel = 3, save_file=SAVE_FILE):
    global automark, quitmark
    record_time = 10  # 固定录音时长为10秒
    
    CHUNK = 1024
    FORMAT = pyaudio.paInt16
    CHANNELS = 1
    RATE = 16000
    WAVE_OUTPUT_FILENAME = save_file 

    if automark:
        # 暂停0.5秒
        print("Waiting 0.5 seconds before recording...")
        time.sleep(0.5)
        
        p = pyaudio.PyAudio()       
        stream_a = p.open(format=FORMAT,
                        channels=CHANNELS,
                        rate=RATE,
                        input=True,
                        frames_per_buffer=CHUNK)
        
        frames = []
        print("Start recording for 10 seconds...")
        
        # 计算需要录制的总帧数
        total_frames = int(RATE / CHUNK * record_time)
        
        for i in range(total_frames):
            if not automark or quitmark == 1:
                break
                
            data = stream_a.read(CHUNK, exception_on_overflow=False)
            frames.append(data)
            
            # 计算并显示音量，仅用于可视化
            rt_data = np.frombuffer(data, dtype=np.int16)
            fft_temp_data = fftpack.fft(rt_data, rt_data.size, overwrite_x=True)
            fft_data = np.abs(fft_temp_data)[0 : fft_temp_data.size // 2 + 1]
            vol = sum(fft_data) // len(fft_data)
            
            # 计算当前进度
            current_time = (i + 1) / total_frames * record_time
            remaining_time = record_time - current_time
            progress_percent = (i + 1) / total_frames * 100
            
            # 清除顶部区域并绘制麦克风图标
            clear_top()
            draw.bitmap((145, 40), mic_logo, mic_purple)
            
            # 绘制波形
            draw_cir(int(vol / 10000))
            
            # 绘制进度条背景
            progress_bar_x = 50
            progress_bar_y = 100
            progress_bar_width = 220
            progress_bar_height = 15
            draw.rectangle(
                [(progress_bar_x, progress_bar_y), 
                 (progress_bar_x + progress_bar_width, progress_bar_y + progress_bar_height)],
                outline=(255, 255, 255),
                fill=splash_theme_color
            )
            
            # 绘制进度条填充部分
            fill_width = int(progress_bar_width * progress_percent / 100)
            draw.rectangle(
                [(progress_bar_x, progress_bar_y), 
                 (progress_bar_x + fill_width, progress_bar_y + progress_bar_height)],
                fill=mic_purple
            )
            
            # 显示当前时间和剩余时间
            time_text = f"{current_time:.1f}s / {record_time}s"
            lcd_draw_string(
                draw,
                progress_bar_x,
                progress_bar_y - 20,
                time_text,
                color=(255, 255, 255),
                scale=font1,
                mono_space=False
            )
            
            # 显示录音状态文本
            status_text = "正在录音..." if la == "cn" else "Recording..."
            lcd_draw_string(
                draw,
                progress_bar_x,
                progress_bar_y + progress_bar_height + 5,
                status_text,
                color=(255, 255, 255),
                scale=font1,
                mono_space=False
            )
            
            # 更新显示
            display.ShowImage(splash)
            
            # 在控制台显示进度
            if i % 10 == 0:  # 每10帧更新一次进度
                print(f"Recording progress: {progress_percent:.1f}%, Time: {current_time:.1f}s / {record_time}s")
                
        print("Recording complete")
        
        # 录音完成后显示完成信息
        clear_top()
        lcd_draw_string(
            draw,
            80,
            60,
            "录音完成" if la == "cn" else "Recording Complete",
            color=(0, 255, 0),
            scale=font1,
            mono_space=False
        )
        display.ShowImage(splash)
        time.sleep(0.5)  # 短暂显示完成信息

    if quitmark == 0:
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


    
