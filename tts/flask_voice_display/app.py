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
VIDEO_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'videos')
COMMAND_LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'logs', 'commands.json')

# 远程摄像头URL
REMOTE_CAMERA_URL = "http://192.168.110.114:5001/video_feed"

# 确保必要的目录存在
os.makedirs(VIDEO_FOLDER, exist_ok=True)
os.makedirs(os.path.dirname(COMMAND_LOG), exist_ok=True)

# 如果命令日志不存在，创建一个空的日志文件
if not os.path.exists(COMMAND_LOG):
    with open(COMMAND_LOG, 'w') as f:
        json.dump([], f)

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

@app.route('/api/text_mode', methods=['POST'])
def receive_text_mode_command():
    """接收来自Android应用的纯文本模式命令"""
    try:
        # 记录请求详情
        logger.info(f"📝 收到纯文本模式请求 - IP: {request.remote_addr}")
        logger.info(f"请求头: {dict(request.headers)}")
        
        data = request.get_json()
        logger.info(f"请求数据: {data}")
        
        if not data or 'command' not in data:
            logger.warning(f"无效请求数据: {data}")
            return jsonify({'error': '无效的请求数据，需要command字段'}), 400
        
        command = data.get('command', '').strip()
        if not command:
            logger.warning("命令内容为空")
            return jsonify({'error': '命令内容为空'}), 400
        
        logger.info(f"✅ 成功处理纯文本命令: '{command}' (长度: {len(command)})")
        
        # 调用LLM分析处理
        try:
            from dog_agent_handler import process_text
            logger.info("调用dog_agent_handler进行LLM分析...")
            result = process_text(command)
            
            if result:
                logger.info(f"✅ LLM分析处理成功")
                return jsonify({
                    'status': 'success',
                    'message': '纯文本命令已接收',
                    'command': command,
                    'mode': 'TEXT',
                    'processed': True
                }), 200
            else:
                logger.error("❌ LLM分析处理失败")
                return jsonify({
                    'status': 'error',
                    'message': 'LLM分析处理失败',
                    'command': command,
                    'mode': 'TEXT',
                    'processed': False
                }), 500
                
        except ImportError:
            logger.warning("dog_agent_handler模块未找到，记录文字但无法进行LLM分析")
            logger.info(f"纯文本命令已记录: {command}")
            return jsonify({
                'status': 'success',
                'message': '纯文本命令已接收并记录（LLM模块未找到）',
                'command': command,
                'mode': 'TEXT',
                'processed': False
            }), 200
            
    except Exception as e:
        logger.error(f"❌ 处理纯文本命令时出错: {str(e)}", exc_info=True)
        return jsonify({'error': '服务器内部错误'}), 500

@app.route('/api/ai_mode', methods=['POST'])
def receive_ai_mode_command():
    """接收来自Android应用的AI推断模式命令"""
    try:
        # 记录请求详情
        logger.info(f"🤖 收到AI推断模式请求 - IP: {request.remote_addr}")
        logger.info(f"请求头: {dict(request.headers)}")
        
        data = request.get_json()
        logger.info(f"请求数据: {data}")
        
        if not data or 'command' not in data:
            logger.warning(f"无效请求数据: {data}")
            return jsonify({'error': '无效的请求数据，需要command字段'}), 400
        
        command = data.get('command', '').strip()
        if not command:
            logger.warning("AI命令内容为空")
            return jsonify({'error': 'AI命令内容为空'}), 400
        
        # 尝试解析AI生成的JSON
        try:
            import json
            ai_json = json.loads(command)
            functions = ai_json.get('function', [])
            response = ai_json.get('response', '')
            logger.info(f"🎯 AI生成的函数: {functions}")
            logger.info(f"💬 AI生成的回应: {response}")
        except:
            logger.warning(f"AI命令不是有效的JSON格式: {command}")
        
        logger.info(f"✅ 成功处理AI推断命令: '{command}' (长度: {len(command)})")
        
        # 调用LLM分析处理
        try:
            from dog_agent_handler import process_text
            logger.info("调用dog_agent_handler进行LLM分析...")
            result = process_result(command)
            
            if result:
                logger.info(f"✅ LLM分析处理成功")
                return jsonify({
                    'status': 'success',
                    'message': 'AI推断命令已接收',
                    'command': command,
                    'mode': 'AI',
                    'processed': True
                }), 200
            else:
                logger.error("❌ LLM分析处理失败")
                return jsonify({
                    'status': 'error',
                    'message': 'LLM分析处理失败',
                    'command': command,
                    'mode': 'AI',
                    'processed': False
                }), 500
                
        except ImportError:
            logger.warning("dog_agent_handler模块未找到，记录文字但无法进行LLM分析")
            logger.info(f"AI推断命令已记录: {command}")
            return jsonify({
                'status': 'success',
                'message': 'AI推断命令已接收并记录（LLM模块未找到）',
                'command': command,
                'mode': 'AI',
                'processed': False
            }), 200
            
    except Exception as e:
        logger.error(f"❌ 处理AI推断命令时出错: {str(e)}", exc_info=True)
        return jsonify({'error': '服务器内部错误'}), 500

# 保持原有的/api/upload_voice接口用于兼容性
@app.route('/api/upload_voice', methods=['POST'])
def upload_voice_compatibility():
    """兼容性接口，重定向到纯文本模式"""
    logger.info("📎 收到兼容性接口请求，重定向到纯文本模式")
    return receive_text_mode_command()

# 保持原有的/api/control接口用于兼容性
@app.route('/api/control', methods=['POST'])
def receive_control_command():
    """兼容性接口，重定向到纯文本模式"""
    logger.info("📎 收到兼容性接口请求，重定向到纯文本模式")
    return receive_text_mode_command()

@app.route('/api/control_dog', methods=['POST'])
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

def generate_mjpeg_stream(video_filename):
    """生成MJPEG视频流"""
    try:
        import cv2
        video_path = os.path.join(VIDEO_FOLDER, video_filename)
        cap = cv2.VideoCapture(video_path)
        
        while True:
            ret, frame = cap.read()
            if not ret:
                # 视频结束，重新开始
                cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                continue
            
            # 编码为JPEG
            ret, buffer = cv2.imencode('.jpg', frame)
            frame_data = buffer.tobytes()
            
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame_data + b'\r\n')
            
            time.sleep(0.033)  # 约30fps
    except ImportError:
        logger.warning("OpenCV未安装，无法生成视频流")
        yield generate_test_mjpeg_stream()
    except Exception as e:
        logger.error(f"生成MJPEG流失败: {str(e)}")
        yield generate_test_mjpeg_stream()

def generate_test_mjpeg_stream():
    """生成测试MJPEG流（静态图像）"""
    try:
        import cv2
        import numpy as np
        
        while True:
            # 创建一个测试图像
            img = np.zeros((480, 640, 3), dtype=np.uint8)
            img[:] = (64, 128, 64)  # 绿色背景
            
            # 添加文字
            cv2.putText(img, 'Camera Feed', (200, 200), cv2.FONT_HERSHEY_SIMPLEX, 2, (255, 255, 255), 3)
            cv2.putText(img, datetime.now().strftime('%Y-%m-%d %H:%M:%S'), (150, 300),
                       cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
            
            # 编码为JPEG
            ret, buffer = cv2.imencode('.jpg', img)
            frame_data = buffer.tobytes()
            
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + frame_data + b'\r\n')
            
            time.sleep(1)  # 1fps
    except ImportError:
        # 如果没有OpenCV，返回简单的文本响应
        test_frame = b"Camera feed not available (OpenCV not installed)"
        while True:
            yield (b'--frame\r\n'
                   b'Content-Type: text/plain\r\n\r\n' + test_frame + b'\r\n')
            time.sleep(1)

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
    logger.info(f"接收文字识别结果并处理控制命令")
    logger.info(f"可以访问 http://localhost:{port} 查看Web界面")
    app.run(host='0.0.0.0', port=port, debug=True)