import os
import urllib.parse
import requests
import re
from flask import Flask, request, jsonify, render_template, send_from_directory, Response, stream_with_context
from flask_cors import CORS
import time
import json
import logging
from datetime import datetime
from pydub import AudioSegment
import tempfile
import sys
from pathlib import Path



'''
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if project_root not in sys.path:
    sys.path.append(project_root) 
'''
sys.path.append(str(Path(__file__).resolve().parent.parent / "dog_agent"))

# 配置应用
app = Flask(__name__)
CORS(app)  # 允许跨域请求

# 配置日志
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('fake_server')

# 配置目录
UPLOAD_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'uploads')
VOICE_FOLDER = os.path.join(UPLOAD_FOLDER, 'voice')
VIDEO_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'videos')
COMMAND_LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'logs', 'commands.json')



import socket
def get_local_ip():
    """获取本机IP地址"""
    """获取本机局域网IP地址"""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        try:
            ip = socket.gethostbyname(socket.gethostname())
        except Exception:
            # 如果所有方法都失败，则回退到 localhost。
            ip = '127.0.0.1'
    finally:
        s.close()
    return ip
REMOTE_CAMERA_URL = f"http://{get_local_ip()}:5001/video_feed"   # 远程摄像头URL














# 确保必要的目录存在
os.makedirs(VOICE_FOLDER, exist_ok=True)
os.makedirs(VIDEO_FOLDER, exist_ok=True)
os.makedirs(os.path.dirname(COMMAND_LOG), exist_ok=True)

# 如果命令日志不存在，创建一个空的日志文件
if not os.path.exists(COMMAND_LOG):
    with open(COMMAND_LOG, 'w') as f:
        json.dump([], f)

def fix_audio_file(file_path):
    """修复音频文件，确保浏览器可以正确播放"""
    try:
        # 检查文件是否存在
        if not os.path.exists(file_path):
            logger.error(f"音频文件不存在: {file_path}")
            return file_path

        # 获取文件信息
        file_size = os.path.getsize(file_path)
        if file_size == 0:
            logger.error(f"音频文件为空: {file_path}")
            return file_path

        logger.info(f"开始修复音频文件: {file_path}, 大小: {file_size} bytes")

        # 尝试使用pydub加载和重新编码音频
        try:
            from pydub import AudioSegment

            # 加载音频文件
            audio = AudioSegment.from_file(file_path)

            # 获取音频信息
            duration = len(audio) / 1000.0  # 转换为秒
            channels = audio.channels
            frame_rate = audio.frame_rate

            logger.info(f"音频信息: 时长={duration:.2f}s, 声道={channels}, 采样率={frame_rate}Hz")

            # 如果音频时长为0或太短，可能有问题
            if duration < 0.1:
                logger.warning(f"音频时长过短: {duration}s")
                return file_path

            # 创建修复后的文件路径
            base_name = os.path.splitext(file_path)[0]
            fixed_path = f"{base_name}_fixed.mp3"

            # 重新编码为标准MP3格式
            audio.export(
                fixed_path,
                format="mp3",
                bitrate="128k",
                parameters=["-ar", "44100", "-ac", "2"]  # 44.1kHz, 立体声
            )

            # 检查修复后的文件
            fixed_size = os.path.getsize(fixed_path)
            if fixed_size > 0:
                logger.info(f"音频文件修复成功: {fixed_path}, 新大小: {fixed_size} bytes")

                # 替换原文件
                if os.path.exists(file_path):
                    os.remove(file_path)
                os.rename(fixed_path, file_path)

                return file_path
            else:
                logger.error(f"修复后的文件为空: {fixed_path}")
                if os.path.exists(fixed_path):
                    os.remove(fixed_path)
                return file_path

        except ImportError:
            logger.warning("pydub未安装，跳过音频修复")
            return file_path
        except Exception as e:
            logger.error(f"使用pydub修复音频失败: {str(e)}")
            return file_path

    except Exception as e:
        logger.error(f"修复音频文件时出错: {str(e)}")
        return file_path

@app.route('/')
def index():
    """提供简单的Web界面"""
    return render_template('index.html')

@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查API，用于确认服务器是否在线"""
    return jsonify({
        "status": "ok",
        "timestamp": time.time()
    }), 200

@app.route('/api/upload_voice', methods=['POST'])
def upload_voice():
    """接收并保存语音文件"""
    logger.info("收到语音上传请求")
    
    try:
        if 'voice' not in request.files:
            logger.warning("没有收到语音文件")
            return jsonify({"error": "No file provided"}), 400
            
        voice_file = request.files['voice']
        
        if voice_file.filename == '':
            logger.warning("文件名为空")
            return jsonify({"error": "No file selected"}), 400
        
        record_path = os.path.join(VOICE_FOLDER, 'record.wav')
        if os.path.exists(record_path):
            os.remove(record_path)
            logger.info("已删除旧的 record.mp3")
        
        # 保存文件
        voice_file.save(record_path)
        logger.info(f"语音文件已保存为: {record_path}")
        
        # 调用处理程序
        from dog_agent_handler import process_voice
        result = process_voice()
        
        if result:
            return jsonify({
                "status": "success", 
                "message": "Voice command processed successfully"
            }), 200
        else:
            return jsonify({
                "status": "error",
                "message": "Failed to process voice command"
            }), 500
            
    except Exception as e:
        logger.error(f"处理上传文件失败: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/control', methods=['POST'])
def control_dog():
    """接收控制命令"""
    # 详细记录请求信息
    logger.info("=== 收到控制命令请求 ===")
    logger.info(f"请求方法: {request.method}")
    logger.info(f"请求URL: {request.url}")
    logger.info(f"请求头: {dict(request.headers)}")
    logger.info(f"Content-Type: {request.content_type}")
    logger.info(f"原始数据: {request.get_data()}")

    data = request.json
    logger.info(f"解析后的JSON数据: {data}")

    if not data or 'action' not in data:
        logger.error("请求数据无效或缺少action字段")
        return jsonify({"error": "No action specified"}), 400

    action = data['action']
    timestamp = data.get('timestamp', time.time())
    source = data.get('source', 'unknown')

    logger.info(f"控制命令详情: action={action}, timestamp={timestamp}, source={source}")

    # 使用eval语句输出提示信息，方便后续扩展功能
    try:
        # 根据不同的命令执行不同的eval语句
        if action == "forward":
            eval('print("🚀 机械狗前进命令已接收！")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加前进控制逻辑")')
        elif action == "backward":
            eval('print("🔙 机械狗后退命令已接收！")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加后退控制逻辑")')
        elif action == "left":
            eval('print("↩️ 机械狗左转命令已接收！")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加左转控制逻辑")')
        elif action == "right":
            eval('print("↪️ 机械狗右转命令已接收！")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加右转控制逻辑")')
        elif action == "stop":
            eval('print("⏹️ 机械狗停止命令已接收！")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加停止控制逻辑")')
        else:
            # 其他动作命令
            eval(f'print("🎯 机械狗动作命令已接收: {action}")')
            eval('print(f"⏰ 时间: {datetime.now().strftime(\"%H:%M:%S\")}")')
            eval('print("📍 可在此处添加动作控制逻辑")')

        # 通用的扩展提示
        eval('print("=" * 50)')
        eval(f'print("💡 提示: 可以在此处扩展 {action} 命令的具体实现")')
        eval('print("🔧 例如: 调用机械狗API、发送串口命令等")')
        eval('print("=" * 50)')

    except Exception as e:
        logger.error(f"执行eval语句失败: {str(e)}")

    # 记录命令到日志文件
    try:
        with open(COMMAND_LOG, 'r') as f:
            commands = json.load(f)

        commands.append({
            "action": action,
            "timestamp": timestamp,
            "source": source,
            "time": datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        })

        # 只保留最近的50条命令
        if len(commands) > 50:
            commands = commands[-50:]

        with open(COMMAND_LOG, 'w') as f:
            json.dump(commands, f, indent=2)

    except Exception as e:
        logger.error(f"记录命令失败: {str(e)}")

    return jsonify({
        "status": "success",
        "message": f"Command '{action}' received",
        "command_executed": action,
        "eval_executed": True
    }), 200

@app.route('/api/video_stream')
def video_stream():
    """提供视频流页面"""
    # 获取可用的视频文件列表
    videos = []
    if os.path.exists(VIDEO_FOLDER):
        try:
            for f in os.listdir(VIDEO_FOLDER):
                if f.lower().endswith(('.mp4', '.webm', '.avi', '.mov')):
                    videos.append(f)
            logger.info(f"找到视频文件: {videos}")
        except Exception as e:
            logger.error(f"读取视频目录失败: {str(e)}")
    
    # 添加一个特殊选项用于远程摄像头
    has_remote_camera = True
    
    return render_template('video_stream.html', videos=videos, has_remote_camera=has_remote_camera)

@app.route('/videos/<path:filename>')
def serve_video(filename):
    """提供视频文件服务"""
    try:
        # URL解码文件名以处理中文
        decoded_filename = urllib.parse.unquote(filename)
        logger.info(f"请求视频文件: {decoded_filename}")
        
        # 检查文件是否存在
        file_path = os.path.join(VIDEO_FOLDER, decoded_filename)
        if not os.path.exists(file_path):
            logger.error(f"视频文件不存在: {file_path}")
            return jsonify({"error": "Video file not found"}), 404
        
        # 根据文件扩展名设置正确的MIME类型
        ext = decoded_filename.lower().split('.')[-1]
        mimetype_map = {
            'mp4': 'video/mp4',
            'webm': 'video/webm',
            'avi': 'video/x-msvideo',
            'mov': 'video/quicktime'
        }
        mimetype = mimetype_map.get(ext, 'video/mp4')
        
        logger.info(f"提供视频文件: {file_path}, MIME类型: {mimetype}")
        
        # 使用Response来更好地控制视频流
        def generate():
            with open(file_path, 'rb') as f:
                data = f.read(1024)
                while data:
                    yield data
                    data = f.read(1024)
        
        response = Response(generate(), mimetype=mimetype)
        response.headers.add('Accept-Ranges', 'bytes')
        response.headers.add('Content-Length', str(os.path.getsize(file_path)))
        
        return response
        
    except Exception as e:
        logger.error(f"提供视频文件时出错: {str(e)}")
        return jsonify({"error": "Error serving video file"}), 500

@app.route('/api/commands', methods=['GET'])
def get_commands():
    """获取最近的命令历史"""
    try:
        with open(COMMAND_LOG, 'r') as f:
            commands = json.load(f)
        return jsonify(commands), 200
    except Exception as e:
        logger.error(f"获取命令历史失败: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/voice_files', methods=['GET'])
def get_voice_files():
    """获取语音文件列表"""
    try:
        voice_files = []
        if os.path.exists(VOICE_FOLDER):
            for f in os.listdir(VOICE_FOLDER):
                if f.lower().endswith(('.mp3', '.wav', '.m4a')):
                    file_path = os.path.join(VOICE_FOLDER, f)
                    file_stats = os.stat(file_path)
                    
                    # 获取音频文件时长（需要安装 mutagen）
                    duration = "未知"
                    try:
                        from mutagen.mp3 import MP3
                        audio = MP3(file_path)
                        duration = f"{int(audio.info.length)}秒"
                    except:
                        pass
                    
                    voice_files.append({
                        "filename": f,
                        "size": file_stats.st_size,
                        "duration": duration,
                        "upload_time": datetime.fromtimestamp(file_stats.st_mtime).strftime('%Y-%m-%d %H:%M:%S'),
                        "url": f"/voice/{f}",
                        "full_path": file_path
                    })
        
        # 按时间排序，最新的在前面
        voice_files.sort(key=lambda x: x['upload_time'], reverse=True)
        
        return jsonify({
            "status": "success",
            "voice_files": voice_files,
            "count": len(voice_files)
        }), 200
        
    except Exception as e:
        logger.error(f"获取语音文件列表失败: {str(e)}")
        return jsonify({
            "status": "error",
            "message": str(e),
            "voice_files": [],
            "count": 0
        }), 500

@app.route('/voice/<filename>')
def serve_voice(filename):
    """提供语音文件服务，支持Range请求以便浏览器正确播放"""
    try:
        # 获取文件扩展名
        ext = filename.lower().split('.')[-1]

        # 设置对应的 MIME 类型
        mime_types = {
            'mp3': 'audio/mpeg',
            'wav': 'audio/wav',
            'm4a': 'audio/mp4',
            '3gp': 'audio/3gpp'
        }
        mimetype = mime_types.get(ext, 'audio/mpeg')

        # 确保文件存在
        file_path = os.path.join(VOICE_FOLDER, filename)
        if not os.path.exists(file_path):
            logger.error(f"文件不存在: {file_path}")
            return jsonify({"error": "File not found"}), 404

        # 检查文件大小
        file_size = os.path.getsize(file_path)
        if file_size == 0:
            logger.error(f"文件大小为0: {file_path}")
            return jsonify({"error": "Empty file"}), 400

        # 检查文件是否为有效的音频文件
        try:
            # 尝试读取文件头来验证格式
            with open(file_path, 'rb') as f:
                header = f.read(10)
                if ext == 'mp3':
                    # 检查MP3文件头 (ID3 tag 或 MP3 frame sync)
                    if not (header.startswith(b'ID3') or header[0:2] == b'\xff\xfb' or header[0:2] == b'\xff\xfa'):
                        logger.warning(f"可能不是有效的MP3文件: {filename}")
                elif ext == 'wav':
                    # 检查WAV文件头
                    if not header.startswith(b'RIFF'):
                        logger.warning(f"可能不是有效的WAV文件: {filename}")
        except Exception as e:
            logger.warning(f"无法验证音频文件格式: {filename}, 错误: {e}")

        logger.info(f"提供音频文件: {filename}, 大小: {file_size} bytes, MIME类型: {mimetype}")

        # 处理Range请求（用于音频流播放）
        range_header = request.headers.get('Range')
        if range_header:
            return handle_range_request(file_path, file_size, mimetype, range_header)

        # 普通请求
        response = send_from_directory(
            VOICE_FOLDER,
            filename,
            mimetype=mimetype,
            as_attachment=False
        )

        # 添加必要的响应头
        response.headers['Accept-Ranges'] = 'bytes'
        response.headers['Cache-Control'] = 'public, max-age=3600'  # 允许缓存1小时
        response.headers['Content-Length'] = str(file_size)
        response.headers['Content-Type'] = mimetype

        # 添加CORS头
        response.headers['Access-Control-Allow-Origin'] = '*'
        response.headers['Access-Control-Allow-Methods'] = 'GET, HEAD, OPTIONS'
        response.headers['Access-Control-Allow-Headers'] = 'Range'

        return response

    except Exception as e:
        logger.error(f"提供语音文件时出错: {str(e)}")
        return jsonify({"error": str(e)}), 500

def handle_range_request(file_path, file_size, mimetype, range_header):
    """处理Range请求，支持音频流播放"""
    try:
        # 解析Range头
        range_match = re.match(r'bytes=(\d+)-(\d*)', range_header)
        if not range_match:
            return Response("Invalid Range header", status=416)

        start = int(range_match.group(1))
        end = int(range_match.group(2)) if range_match.group(2) else file_size - 1

        # 确保范围有效
        if start >= file_size or end >= file_size or start > end:
            return Response("Range Not Satisfiable", status=416)

        content_length = end - start + 1

        def generate():
            with open(file_path, 'rb') as f:
                f.seek(start)
                remaining = content_length
                while remaining:
                    chunk_size = min(8192, remaining)
                    chunk = f.read(chunk_size)
                    if not chunk:
                        break
                    yield chunk
                    remaining -= len(chunk)

        response = Response(
            generate(),
            status=206,  # Partial Content
            mimetype=mimetype,
            direct_passthrough=True
        )

        response.headers['Content-Range'] = f'bytes {start}-{end}/{file_size}'
        response.headers['Accept-Ranges'] = 'bytes'
        response.headers['Content-Length'] = str(content_length)
        response.headers['Cache-Control'] = 'public, max-age=3600'

        # 添加CORS头
        response.headers['Access-Control-Allow-Origin'] = '*'
        response.headers['Access-Control-Allow-Methods'] = 'GET, HEAD, OPTIONS'
        response.headers['Access-Control-Allow-Headers'] = 'Range'

        logger.info(f"Range请求: {start}-{end}/{file_size} for {file_path}")

        return response

    except Exception as e:
        logger.error(f"处理Range请求失败: {str(e)}")
        return Response("Internal Server Error", status=500)

@app.route('/api/video_list', methods=['GET'])
def get_video_list():
    """获取可用的视频文件列表"""
    try:
        videos = []
        if os.path.exists(VIDEO_FOLDER):
            for f in os.listdir(VIDEO_FOLDER):
                if f.lower().endswith(('.mp4', '.webm', '.avi', '.mov')):
                    file_path = os.path.join(VIDEO_FOLDER, f)
                    file_size = os.path.getsize(file_path)
                    videos.append({
                        "filename": f,
                        "url": f"/videos/{urllib.parse.quote(f)}",
                        "size": file_size
                    })
        
        logger.info(f"返回视频列表: {len(videos)} 个文件")
        return jsonify({
            "status": "success",
            "videos": videos,
            "count": len(videos)
        }), 200
        
    except Exception as e:
        logger.error(f"获取视频列表失败: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/video_feed')
def video_feed():
    """提供默认视频流（用于app访问）"""
    try:
        # 获取第一个可用的视频文件
        if os.path.exists(VIDEO_FOLDER):
            videos = [f for f in os.listdir(VIDEO_FOLDER) if f.lower().endswith(('.mp4', '.webm', '.avi', '.mov'))]
            if videos:
                # 返回第一个视频的URL
                first_video = videos[0]
                video_url = f"/videos/{urllib.parse.quote(first_video)}"
                return jsonify({
                    "status": "success",
                    "video_url": video_url,
                    "filename": first_video
                }), 200
        
        return jsonify({
            "status": "error",
            "message": "No video files available"
        }), 404
        
    except Exception as e:
        logger.error(f"获取视频流失败: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/video_feed')
def mjpeg_video_feed():
    """提供MJPEG视频流（模拟摄像头直播）"""
    try:
        # 获取第一个可用的视频文件
        if os.path.exists(VIDEO_FOLDER):
            videos = [f for f in os.listdir(VIDEO_FOLDER) if f.lower().endswith(('.mp4', '.webm', '.avi', '.mov'))]
            if videos:
                first_video = videos[0]
                logger.info(f"开始MJPEG视频流: {first_video}")
                return Response(
                    generate_mjpeg_stream(first_video),
                    mimetype='multipart/x-mixed-replace; boundary=frame'
                )
        
        # 如果没有视频文件，返回测试图像
        return Response(
            generate_test_mjpeg_stream(),
            mimetype='multipart/x-mixed-replace; boundary=frame'
        )
        
    except Exception as e:
        logger.error(f"MJPEG视频流失败: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/remote_camera_feed')
def remote_camera_feed():
    """代理远程MJPEG摄像头流，如果远程不可用则回退到本地流"""
    try:
        logger.info(f"尝试连接远程摄像头: {REMOTE_CAMERA_URL}")

        # 使用简单的请求头
        headers = {
            'Accept': 'multipart/x-mixed-replace;boundary=frame'
        }

        # 尝试获取远程MJPEG流
        resp = requests.get(REMOTE_CAMERA_URL,
                          stream=True,
                          timeout=5,  # 减少超时时间
                          headers=headers)

        if resp.status_code == 200:
            logger.info("成功连接到远程摄像头流")
            # 直接转发视频流
            return Response(
                stream_with_context(resp.iter_content(chunk_size=1024)),
                mimetype='multipart/x-mixed-replace; boundary=frame'
            )
        else:
            logger.warning(f"远程摄像头返回错误状态码: {resp.status_code}，回退到本地流")

    except requests.RequestException as e:
        logger.warning(f"连接远程摄像头失败: {str(e)}，回退到本地流")
    except Exception as e:
        logger.warning(f"处理远程摄像头流失败: {str(e)}，回退到本地流")

    # 回退到本地MJPEG流
    logger.info("使用本地MJPEG流作为摄像头源")
    try:
        # 获取第一个可用的视频文件
        if os.path.exists(VIDEO_FOLDER):
            videos = [f for f in os.listdir(VIDEO_FOLDER) if f.lower().endswith(('.mp4', '.webm', '.avi', '.mov'))]
            if videos:
                first_video = videos[0]
                logger.info(f"使用本地视频生成MJPEG流: {first_video}")
                return Response(
                    generate_mjpeg_stream(first_video),
                    mimetype='multipart/x-mixed-replace; boundary=frame'
                )

        # 如果没有视频文件，返回测试图像
        logger.info("使用测试图像生成MJPEG流")
        return Response(
            generate_test_mjpeg_stream(),
            mimetype='multipart/x-mixed-replace; boundary=frame'
        )

    except Exception as e:
        logger.error(f"本地MJPEG流也失败: {str(e)}")
        return jsonify({"error": f"All camera sources failed: {str(e)}"}), 500

if __name__ == '__main__':
    port = 8080
    logger.info(f"启动仿真服务器在端口 {port}")
    logger.info(f"语音文件将保存到: {VOICE_FOLDER}")
    logger.info(f"可以访问 http://localhost:{port} 查看Web界面")
    app.run(host='0.0.0.0', port=port, debug=True) 
