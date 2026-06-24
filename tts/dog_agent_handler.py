import logging
import requests
import json
from datetime import datetime
import os
from config import LLM_CONFIG, CURRENT_LLM_PROVIDER, DOG_CONTROL_CONFIG, SUPPORTED_COMMANDS

# 配置日志
logger = logging.getLogger('dog_agent_handler')

# 获取当前LLM配置
def get_current_llm_config():
    """获取当前LLM配置"""
    return LLM_CONFIG.get(CURRENT_LLM_PROVIDER, LLM_CONFIG["openai"])

def query_llm(user_input):
    """
    向LLM发送查询请求
    
    Args:
        user_input (str): 用户输入的文字
        
    Returns:
        dict: LLM的响应结果
    """
    try:
        config = get_current_llm_config()
        
        # 构造支持命令的文本
        movement_commands = ", ".join(SUPPORTED_COMMANDS["movement"])
        action_commands = ", ".join(SUPPORTED_COMMANDS["actions"])
        special_commands = ", ".join(SUPPORTED_COMMANDS["special"])
        
        # 构造LLM查询的prompt
        system_prompt = f"""你是一个机械狗控制助手。根据用户的语音指令，分析并返回对应的控制命令。

支持的控制命令包括：
移动命令: {movement_commands}
动作命令: {action_commands}
特殊命令: {special_commands}

请根据用户的输入，返回一个JSON格式的响应，包含：
{{
    "action": "命令名称",
    "confidence": 0.95,
    "reasoning": "解释为什么选择这个命令"
}}

如果无法识别命令，返回：
{{
    "action": "unknown",
    "confidence": 0.0,
    "reasoning": "无法识别的命令"
}}"""

        logger.info(f"发送LLM查询: {user_input} (提供商: {CURRENT_LLM_PROVIDER})")
        
        # 根据不同的LLM提供商构造请求
        if CURRENT_LLM_PROVIDER == "openai":
            return query_openai(user_input, system_prompt, config)
        elif CURRENT_LLM_PROVIDER == "claude":
            return query_claude(user_input, system_prompt, config)
        elif CURRENT_LLM_PROVIDER == "local":
            return query_local_llm(user_input, system_prompt, config)
        else:
            logger.error(f"不支持的LLM提供商: {CURRENT_LLM_PROVIDER}")
            return fallback_command_matching(user_input)
            
    except Exception as e:
        logger.error(f"LLM查询失败: {str(e)}")
        # 如果LLM不可用，回退到简单的关键词匹配
        return fallback_command_matching(user_input)

def query_openai(user_input, system_prompt, config):
    """查询OpenAI API"""
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {config['api_key']}"
    }
    
    data = {
        "model": config["model"],
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_input}
        ],
        "max_tokens": config["max_tokens"],
        "temperature": config["temperature"]
    }
    
    response = requests.post(config["api_url"], headers=headers, json=data, timeout=30)
    
    if response.status_code == 200:
        result = response.json()
        llm_response = result['choices'][0]['message']['content']
        return parse_llm_response(llm_response)
    else:
        logger.error(f"OpenAI API请求失败: {response.status_code}, {response.text}")
        return {"action": "unknown", "confidence": 0.0, "reasoning": "OpenAI API请求失败"}

def query_claude(user_input, system_prompt, config):
    """查询Claude API"""
    headers = {
        "Content-Type": "application/json",
        "x-api-key": config["api_key"],
        "anthropic-version": "2023-06-01"
    }
    
    data = {
        "model": config["model"],
        "max_tokens": config["max_tokens"],
        "temperature": config["temperature"],
        "system": system_prompt,
        "messages": [
            {"role": "user", "content": user_input}
        ]
    }
    
    response = requests.post(config["api_url"], headers=headers, json=data, timeout=30)
    
    if response.status_code == 200:
        result = response.json()
        llm_response = result['content'][0]['text']
        return parse_llm_response(llm_response)
    else:
        logger.error(f"Claude API请求失败: {response.status_code}, {response.text}")
        return {"action": "unknown", "confidence": 0.0, "reasoning": "Claude API请求失败"}

def query_local_llm(user_input, system_prompt, config):
    """查询本地LLM（如Ollama）"""
    data = {
        "model": config["model"],
        "prompt": f"{system_prompt}\n\n用户输入: {user_input}",
        "stream": False
    }
    
    response = requests.post(config["api_url"], json=data, timeout=30)
    
    if response.status_code == 200:
        result = response.json()
        llm_response = result.get('response', '')
        return parse_llm_response(llm_response)
    else:
        logger.error(f"本地LLM请求失败: {response.status_code}, {response.text}")
        return {"action": "unknown", "confidence": 0.0, "reasoning": "本地LLM请求失败"}

def parse_llm_response(llm_response):
    """解析LLM响应"""
    try:
        parsed_response = json.loads(llm_response)
        logger.info(f"LLM响应: {parsed_response}")
        return parsed_response
    except json.JSONDecodeError:
        logger.error(f"无法解析LLM响应: {llm_response}")
        return {"action": "unknown", "confidence": 0.0, "reasoning": "LLM响应格式错误"}

def fallback_command_matching(user_input):
    """
    LLM不可用时的回退命令匹配
    
    Args:
        user_input (str): 用户输入的文字
        
    Returns:
        dict: 匹配结果
    """
    text_lower = user_input.lower().strip()
    
    # 定义命令映射
    command_mapping = {
        "前进": "forward",
        "向前": "forward",
        "往前": "forward",
        "前面": "forward",
        "go forward": "forward",
        "move forward": "forward",
        
        "后退": "backward",
        "向后": "backward",
        "往后": "backward",
        "退后": "backward",
        "go backward": "backward",
        "move backward": "backward",
        
        "左转": "left",
        "向左": "left",
        "往左": "left",
        "turn left": "left",
        "go left": "left",
        
        "右转": "right",
        "向右": "right",
        "往右": "right",
        "turn right": "right",
        "go right": "right",
        
        "停止": "stop",
        "停下": "stop",
        "停": "stop",
        "stop": "stop",
        "halt": "stop",
        
        "坐下": "sit",
        "坐": "sit",
        "sit": "sit",
        "sit down": "sit",
        
        "站起": "stand",
        "站立": "stand",
        "起立": "stand",
        "stand": "stand",
        "stand up": "stand",
        
        "趴下": "lie",
        "躺下": "lie",
        "lie": "lie",
        "lie down": "lie",
        
        "握手": "shake",
        "shake": "shake",
        "shake hands": "shake",
        
        "跳跃": "jump",
        "跳": "jump",
        "jump": "jump",
        
        "转圈": "spin",
        "旋转": "spin",
        "spin": "spin",
        "turn around": "spin"
    }
    
    # 查找匹配的命令
    for keyword, cmd in command_mapping.items():
        if keyword in text_lower:
            return {
                "action": cmd,
                "confidence": 0.8,
                "reasoning": f"关键词匹配: {keyword}"
            }
    
    return {
        "action": "unknown",
        "confidence": 0.0,
        "reasoning": "无法识别的命令"
    }

def execute_dog_command(action, confidence, reasoning):
    """
    执行机械狗控制命令
    
    Args:
        action (str): 命令名称
        confidence (float): 置信度
        reasoning (str): 推理过程
        
    Returns:
        bool: 执行是否成功
    """
    try:
        if action == "unknown":
            logger.warning(f"未识别的命令，推理: {reasoning}")
            print(f"❓ 未识别的命令")
            print(f"💭 推理过程: {reasoning}")
            all_commands = []
            for commands in SUPPORTED_COMMANDS.values():
                all_commands.extend(commands)
            print(f"💡 支持的命令包括: {', '.join(all_commands)}")
            print("=" * 50)
            return False
        
        # 验证命令是否支持
        if not validate_command(action):
            logger.warning(f"不支持的命令: {action}")
            print(f"⚠️ 不支持的命令: {action}")
            print("=" * 50)
            return False
        
        logger.info(f"执行机械狗命令: {action}, 置信度: {confidence}")
        
        # 调用实际的机械狗控制API
        success = send_command_to_dog(action)
        
        print(f"🤖 执行机械狗命令: {action}")
        print(f"📊 置信度: {confidence:.2f}")
        print(f"💭 推理过程: {reasoning}")
        print(f"✅ 执行结果: {'成功' if success else '失败'}")
        print(f"⏰ 时间: {datetime.now().strftime('%H:%M:%S')}")
        print("=" * 50)
        
        return success
        
    except Exception as e:
        logger.error(f"执行机械狗命令失败: {str(e)}")
        return False

def send_command_to_dog(action):
    """
    发送命令到机械狗控制API
    
    Args:
        action (str): 命令名称
        
    Returns:
        bool: 是否成功
    """
    try:
        # 构造请求数据
        command_data = {
            "action": action,
            "timestamp": datetime.now().timestamp(),
            "source": "llm_voice_control"
        }
        
        # 发送请求到机械狗控制API
        headers = {
            "Content-Type": "application/json"
        }
        
        response = requests.post(
            DOG_CONTROL_CONFIG["api_url"],
            json=command_data,
            headers=headers,
            timeout=DOG_CONTROL_CONFIG["timeout"]
        )
        
        if response.status_code == 200:
            result = response.json()
            if result.get("status") == "success":
                logger.info(f"机械狗命令发送成功: {action}")
                return True
            else:
                logger.error(f"机械狗命令执行失败: {result.get('message', '未知错误')}")
                return False
        else:
            logger.error(f"机械狗API请求失败: {response.status_code}, {response.text}")
            return False
            
    except requests.exceptions.Timeout:
        logger.error(f"机械狗API请求超时")
        return False
    except requests.exceptions.ConnectionError:
        logger.error(f"无法连接到机械狗API")
        return False
    except Exception as e:
        logger.error(f"发送机械狗命令失败: {str(e)}")
        return False

def validate_command(action):
    """
    验证命令是否支持
    
    Args:
        action (str): 命令名称
        
    Returns:
        bool: 是否支持
    """
    all_commands = []
    for commands in SUPPORTED_COMMANDS.values():
        all_commands.extend(commands)
    return action in all_commands
def process_result(command):
    success = execute_dog_command(
    command["action"],
    command["confidence"],
    command["reasoning"]
    )
    return success
def process_text(recognized_text):
    """
    处理识别后的文字命令
    
    Args:
        recognized_text (str): 从app端发送过来的识别文字
        
    Returns:
        bool: 处理是否成功
    """
    try:
        logger.info(f"开始处理文字命令: {recognized_text}")
        
        # 1. 向LLM发送查询
        llm_result = query_llm(recognized_text)
        
        # 2. 执行机械狗控制命令
        success = execute_dog_command(
            llm_result["action"],
            llm_result["confidence"],
            llm_result["reasoning"]
        )
        
        return success
        
    except Exception as e:
        logger.error(f"处理文字命令失败: {str(e)}")
        return False

# 保持向后兼容，如果有代码还在调用process_voice
def process_voice():
    """
    向后兼容的函数，现在已经不再使用
    """
    logger.warning("process_voice函数已废弃，请使用process_text函数")
    return False