import socket

def get_preferred_ip():
    """
    获取本机所有IPv4地址，并返回第一个以 '192.' 开头的IP。
    如果找不到，则回退到 '127.0.0.1'。
    """
    print("正在尝试获取本机IP地址...")
    try:
        # socket.gethostbyname_ex() 返回一个元组 (hostname, aliaslist, ipaddrlist)
        hostname, _, ipaddrlist = socket.gethostbyname_ex(socket.gethostname())
        print(f"检测到主机 '{hostname}' 的所有IP地址: {ipaddrlist}")

        # 筛选出以 '192.' 开头的IP地址
        preferred_ips = [ip for ip in ipaddrlist if ip.startswith('192.')]

        if preferred_ips:
            selected_ip = preferred_ips[0]
            print(f"已筛选出以 '192.' 开头的IP: {preferred_ips}")
            print(f"成功选择IP: {selected_ip}")
            return selected_ip
        else:
            print("警告: 未在列表中找到以 '192.' 开头的IP地址。将回退到 '127.0.0.1'。")
            return '127.0.0.1'

    except Exception as e:
        print(f"错误: 获取IP地址时发生异常: {e}。将回退到 '127.0.0.1'。")
        return '127.0.0.1'

# 调用函数并构建URL
local_ip = get_preferred_ip()
REMOTE_CAMERA_URL = f"http://{local_ip}:5001/video_feed"
print(f"\n最终构建的URL为: {REMOTE_CAMERA_URL}")

