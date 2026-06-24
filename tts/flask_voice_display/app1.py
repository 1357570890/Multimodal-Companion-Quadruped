from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO, emit
import json
from datetime import datetime
import logging

app = Flask(__name__)
app.config['SECRET_KEY'] = 'voice_control_secret_key'
socketio = SocketIO(app, cors_allowed_origins="*")

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 存储最近的语音识别结果
recent_results = []
MAX_RESULTS = 100  # 最多保存100条记录

@app.route('/')
def index():
    """主页面，显示实时语音识别结果"""
    return render_template('index.html')

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
        
        # 创建结果记录
        result = {
            'id': len(recent_results) + 1,
            'text': command,
            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'source': 'Android VoiceControlApp - 纯文本模式',
            'mode': 'TEXT',
            'ip': request.remote_addr
        }
        
        # 添加到结果列表
        recent_results.append(result)
        
        # 保持列表大小限制
        if len(recent_results) > MAX_RESULTS:
            recent_results.pop(0)
        
        logger.info(f"✅ 成功处理纯文本命令: '{command}' (长度: {len(command)})")
        
        # 通过WebSocket实时推送到前端
        socketio.emit('new_voice_result', result)
        logger.info("📡 已通过WebSocket推送到前端")
        
        return jsonify({
            'status': 'success',
            'message': '纯文本命令已接收',
            'command': command,
            'mode': 'TEXT',
            'id': result['id']
        })
        
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
        
        # 创建结果记录
        result = {
            'id': len(recent_results) + 1,
            'text': command,
            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'source': 'Android VoiceControlApp - AI推断模式',
            'mode': 'AI',
            'ip': request.remote_addr
        }
        
        # 添加到结果列表
        recent_results.append(result)
        
        # 保持列表大小限制
        if len(recent_results) > MAX_RESULTS:
            recent_results.pop(0)
        
        logger.info(f"✅ 成功处理AI推断命令: '{command}' (长度: {len(command)})")
        
        # 通过WebSocket实时推送到前端
        socketio.emit('new_voice_result', result)
        logger.info("📡 已通过WebSocket推送到前端")
        
        return jsonify({
            'status': 'success',
            'message': 'AI推断命令已接收',
            'command': command,
            'mode': 'AI',
            'id': result['id']
        })
        
    except Exception as e:
        logger.error(f"❌ 处理AI推断命令时出错: {str(e)}", exc_info=True)
        return jsonify({'error': '服务器内部错误'}), 500

# 保持原有的/api/control接口用于兼容性
@app.route('/api/control', methods=['POST'])
def receive_control_command():
    """兼容性接口，重定向到纯文本模式"""
    logger.info("📎 收到兼容性接口请求，重定向到纯文本模式")
    return receive_text_mode_command()

@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查接口，对应Android应用的连接检查"""
    return jsonify({
        'status': 'healthy',
        'service': 'Flask Voice Display Server',
        'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    })

@app.route('/api/results', methods=['GET'])
def get_recent_results():
    """获取最近的语音识别结果"""
    try:
        limit = request.args.get('limit', 50, type=int)
        limit = min(limit, MAX_RESULTS)  # 限制最大数量
        
        results = recent_results[-limit:] if recent_results else []
        
        return jsonify({
            'status': 'success',
            'results': results,
            'total': len(recent_results)
        })
        
    except Exception as e:
        logger.error(f"获取结果时出错: {str(e)}")
        return jsonify({'error': '服务器内部错误'}), 500

@app.route('/api/clear', methods=['POST'])
def clear_results():
    """清空所有结果"""
    try:
        global recent_results
        recent_results.clear()
        
        # 通知前端清空结果
        socketio.emit('results_cleared')
        
        return jsonify({
            'status': 'success',
            'message': '所有结果已清空'
        })
        
    except Exception as e:
        logger.error(f"清空结果时出错: {str(e)}")
        return jsonify({'error': '服务器内部错误'}), 500

@socketio.on('connect')
def handle_connect():
    """客户端连接时发送最近的结果"""
    logger.info('客户端已连接')
    emit('initial_results', {
        'results': recent_results[-20:] if recent_results else [],
        'total': len(recent_results)
    })

@socketio.on('disconnect')
def handle_disconnect():
    """客户端断开连接"""
    logger.info('客户端已断开连接')

if __name__ == '__main__':
    print("启动Flask语音识别结果显示服务器...")
    print("访问地址: http://localhost:5000")
    print("控制命令接口: POST http://localhost:5000/api/control")
    print("健康检查接口: GET http://localhost:5000/api/health")
    print("与Android VoiceControlApp完全兼容！")
    socketio.run(app, host='0.0.0.0', port=5000, debug=True)