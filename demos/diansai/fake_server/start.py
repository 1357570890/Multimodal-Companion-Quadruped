import os
import sys
import subprocess
import webbrowser
import time

def main():
    print("正在启动机械狗仿真服务器...")
    
    # 获取当前目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    app_path = os.path.join(script_dir, 'app.py')
    
    # 检查Python环境
    try:
        import flask
        import flask_cors
    except ImportError:
        print("未检测到必要的Python包，正在安装...")
        subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'flask', 'flask-cors'])
    
    # 检查视频目录
    videos_dir = os.path.join(script_dir, 'videos')
    if not os.path.exists(videos_dir):
        os.makedirs(videos_dir)
        print(f"创建视频目录: {videos_dir}")
    
    # 检查上传目录
    uploads_dir = os.path.join(script_dir, 'uploads', 'voice')
    if not os.path.exists(uploads_dir):
        os.makedirs(uploads_dir)
        print(f"创建上传目录: {uploads_dir}")
    
    # 启动服务器
    print("启动服务器...")
    try:
        # 设置环境变量，使Flask使用正确的IP地址
        env = os.environ.copy()
        env["FLASK_APP"] = app_path
        
        # 启动Flask应用
        server_process = subprocess.Popen([sys.executable, app_path], env=env)
        
        # 等待服务器启动
        time.sleep(2)
        
        # 如果服务器启动成功，打开浏览器
        url = "http://localhost:8088"
        print(f"服务器启动成功，正在打开浏览器: {url}")
        webbrowser.open(url)
        
        print("\n仿真服务器已启动，按Ctrl+C停止服务器...")
        server_process.wait()
        
    except KeyboardInterrupt:
        print("正在关闭服务器...")
        server_process.terminate()
        server_process.wait()
        print("服务器已关闭")
    except Exception as e:
        print(f"启动服务器时出错: {str(e)}")
        return 1
    
    return 0

if __name__ == "__main__":
    sys.exit(main()) 
