#此为录音和唤醒文件
import pyaudio,wave,random
import wave
import numpy as np
from scipy import fftpack, signal
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
RECORD_SECONDS = 300 #600  # 最大录音时长，此处无效 Maximum recording duration，This is invalid
SAVE_FILE = "./demos/dog_agent/myrec.wav"
SAVE_KEYWORD="keyword_audio.wav"
ENDLAST = 30
start_threshold = 120000  # 开始录音阈值 Start recording threshold
end_threshold = 80000  # 结束录音阈值 End recording threshold
endlast = 10


# 关键词检测参数 Keyword detection parameters
KEYWORD_MODEL_PATH = "./demos/src/lulu_v3.1.907.premium"
KEYWORD_THRESHOLD = 0.7
PLAY_COMMAND = "aplay"  # Linux 播放命令，Windows 可改为 "start" Linux playback command, Windows can change to 'start'

# 添加噪声过滤函数
def filter_noise(audio_data, fs=16000):
    """
    使用带通滤波器过滤掉非人声频率范围的噪声
    人声主要集中在300Hz-3400Hz
    """
    # 创建带通滤波器
    nyquist = 0.5 * fs
    low = 300 / nyquist
    high = 3400 / nyquist
    b, a = signal.butter(4, [low, high], btype='band')
    
    # 应用滤波器
    filtered_data = signal.filtfilt(b, a, audio_data)
    return filtered_data

def calculate_volume(data):
    """改进的音量计算，包含噪声过滤"""
    rt_data = np.frombuffer(data, dtype=np.int16)
    
    # 应用带通滤波，突出人声
    filtered_data = filter_noise(rt_data)
    
    # 进行FFT
    fft_temp_data = fftpack.fft(filtered_data, filtered_data.size, overwrite_x=True)
    fft_data = np.abs(fft_temp_data)[0: fft_temp_data.size // 2 + 1]
    
    # 计算平均音量
    return sum(fft_data) // len(fft_data)

def draw_cir(ch, is_active=False):
    """
    绘制圆形音量指示器
    ch: 音量大小
    is_active: 是否处于活动状态（音量超过阈值）
    """
    if ch > 15:
        ch = 15  # 限制最大值
    
    clear_top()
    
    # 设置麦克风颜色 - 根据活动状态变化
    mic_color = mic_purple if is_active else (180, 180, 180)  # 非活动状态显示灰色
    draw.bitmap((145, 40), mic_logo, mic_color)
    
    radius = 4
    cy = 60
    centers = [(62, cy), (87, cy), (112, cy), (210, cy), (235, cy), (260, cy)]
    
    # 默认静止状态时的显示
    if not is_active:
        # 所有点都在中心位置绘制（无震动）
        for center in centers:
            x, y = center
            draw.ellipse((x-radius, y-radius, x+radius, y+radius), fill=mic_purple)
    else:
        # 有声音时的动态波纹
        for center in centers:
            # 根据音量计算偏移量
            amplitude = int(ch * 1.5)  # 增加放大系数，使视觉效果更明显
            offset = random.randint(0, amplitude) if amplitude > 0 else 0
            
            x, y = center
            # 绘制上下方向的圆点
            draw.ellipse((x-radius, y-offset-radius, x+radius, y-offset+radius), fill=mic_purple)
            draw.ellipse((x-radius, y+offset-radius, x+radius, y+offset+radius), fill=mic_purple)

def draw_wave(ch, is_active=False):
    """
    绘制波形音量显示
    ch: 音量大小
    is_active: 是否处于活动状态（音量超过阈值）
    """
    ch = min(ch, 10)  # 限制最大值为10
    clear_top()
    
    # 设置麦克风颜色 - 根据活动状态变化
    mic_color = mic_purple if is_active else (180, 180, 180)
    draw.bitmap((145, 40), mic_logo, mic_color)
    
    wave_params = [
        {"start_x": 40, "start_y": 32, "width": 80, "height": 50},
        {"start_x": 210, "start_y": 32, "width": 80, "height": 50}
    ]
    
    for params in wave_params:
        draw_single_wave(params["start_x"], params["start_y"], 
                         params["width"], params["height"], 
                         ch, is_active)

def draw_single_wave(start_x, start_y, width, height, ch, is_active=False):
    """
    绘制单个波形
    增加is_active参数控制是否显示动态波形
    """
    y_center = height // 2
    previous_point = (start_x, y_center + start_y)
    
    # 波形点数
    points = 20
    point_distance = width // points
    
    if not is_active:
        # 静止状态 - 绘制一条水平直线
        end_point = (start_x + width, y_center + start_y)
        draw.line([previous_point, end_point], fill=mic_purple, width=2)
    else:
        # 活动状态 - 绘制波浪形
        for i in range(points):
            x = start_x + i * point_distance
            
            # 创建更自然的波形
            phase = i / points * 2 * math.pi
            # 使用正弦波模拟波形
            if start_x > 200:  # 右侧波形
                amplitude = ch * 3 * math.sin(phase + math.pi/4)
            else:  # 左侧波形
                amplitude = ch * 3 * math.sin(phase)
            
            # 随机因子使波形更自然
            random_factor = random.uniform(-ch/3, ch/3) if ch > 0 else 0
            y = y_center + amplitude + random_factor + start_y
            
            # 绘制线段
            current_point = (x, y)
            draw.line([previous_point, current_point], fill=mic_purple, width=2)
            previous_point = current_point

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
    """修改录音函数，针对嘈杂环境优化"""
    global automark, quitmark
    
    # 针对嘈杂环境提高阈值
    start_threshold = 200000  # 提高开始录音阈值 (原120000)
    wave_threshold = 100000   # 提高波形显示阈值 (原80000)
    end_threshold = 100000    # 提高结束录音阈值 (原60000)
    
    # 连续帧检测 - 减少误触发
    consecutive_start_frames = 5  # 连续5帧超过阈值才开始 (提高要求)
    consecutive_end_frames = 10   # 连续10帧低于阈值才结束 (更稳定)
    start_frame_count = 0
    end_frame_count = 0
    
    # 滑动窗口存储最近音量值 - 用于平滑处理
    window_size = 5
    volume_window = [0] * window_size
    
    # 录音参数
    CHUNK = 1024
    FORMAT = pyaudio.paInt16
    CHANNELS = 1
    RATE = 16000
    WAVE_OUTPUT_FILENAME = save_file
    max_record_time = 20

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
        start_time = None

        # 环境校准 - 测量背景噪音
        print("正在校准环境噪音...")
        noise_samples = []
        for _ in range(30):  # 采样约2秒
            data = stream_a.read(CHUNK, exception_on_overflow=False)
            vol = calculate_volume(data)
            noise_samples.append(vol)
            time.sleep(0.05)
        
        # 计算噪音基准
        avg_noise = sum(noise_samples) / len(noise_samples)
        max_noise = max(noise_samples)
        print(f"环境噪音: 平均{avg_noise:.0f}, 最大{max_noise:.0f}")
        
        # 动态调整阈值
        if max_noise > 100000:  # 极度嘈杂环境
            start_threshold = max(start_threshold, max_noise * 1.8)
            wave_threshold = max(wave_threshold, max_noise * 1.5)
            end_threshold = max(end_threshold, max_noise * 1.2)
        
        print(f"调整后阈值: 开始{start_threshold:.0f}, 波形{wave_threshold:.0f}, 结束{end_threshold:.0f}")

        while not break_luyin:
            if not automark or quitmark == 1:
                break

            data = stream_a.read(CHUNK, exception_on_overflow=False)
            vol = calculate_volume(data)
            
            # 更新滑动窗口
            volume_window = volume_window[1:] + [vol]
            # 平滑音量 - 减少突发噪音影响
            smoothed_vol = sum(volume_window) / len(volume_window)
            
            # 音量标准化 - 更合理的缩放
            normalized_ch = int(min(15, (smoothed_vol / 20000)))
            
            # 阈值检测 - 决定是否激活波形
            is_active = smoothed_vol > wave_threshold
            
            # 开始录音逻辑
            if not start_luyin:
                if smoothed_vol > start_threshold:
                    start_frame_count += 1
                    print(f"检测到声音 {start_frame_count}/{consecutive_start_frames}, 音量: {smoothed_vol:.0f}")
                    
                    if start_frame_count >= consecutive_start_frames:
                        print("开始录音！")
                        start_luyin = True
                        start_time = time.time()
                        frames.append(data)
                else:
                    start_frame_count = max(0, start_frame_count - 1)  # 平滑递减
            else:
                frames.append(data)
                elapsed_time = time.time() - start_time
                
                # 结束录音逻辑
                if smoothed_vol < end_threshold:
                    end_frame_count += 1
                else:
                    end_frame_count = 0
                
                # 结束条件：连续静音或超时
                if end_frame_count >= consecutive_end_frames or elapsed_time > max_record_time:
                    print(f"录音结束 - 静音帧数: {end_frame_count}, 录音时长: {elapsed_time:.1f}s")
                    break_luyin = True
            
            # 绘制音量波形
            draw_cir(normalized_ch, is_active)
            # 或者使用波形显示
            # draw_wave(normalized_ch, is_active)
            display.ShowImage(splash)

        print("录音结束，保存文件...")
        # 保存录音文件的代码保持不变
        # ...existing code...



