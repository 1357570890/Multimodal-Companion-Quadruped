"""
配置文件
"""

# LLM配置
LLM_CONFIG = {
    # OpenAI配置
    "openai": {
        "api_url": "https://api.openai.com/v1/chat/completions",
        "api_key": "your-openai-api-key-here",
        "model": "gpt-3.5-turbo",
        "max_tokens": 150,
        "temperature": 0.3
    },
    
    # Claude配置 (Anthropic)
    "claude": {
        "api_url": "https://api.anthropic.com/v1/messages",
        "api_key": "your-claude-api-key-here",
        "model": "claude-3-sonnet-20240229",
        "max_tokens": 150,
        "temperature": 0.3
    },
    
    # 本地模型配置
    "local": {
        "api_url": "http://localhost:11434/api/generate",  # Ollama示例
        "model": "llama2",
        "max_tokens": 150,
        "temperature": 0.3
    }
}

# 当前使用的LLM提供商
CURRENT_LLM_PROVIDER = "openai"  # 可选: "openai", "claude", "local"

# 机械狗控制配置
DOG_CONTROL_CONFIG = {
    "api_url": "http://localhost:8081/api/control",  # 机械狗控制API地址
    "timeout": 5,  # 请求超时时间
    "retry_count": 3  # 重试次数
}

# 支持的命令列表
SUPPORTED_COMMANDS = {
    "movement": ["forward", "backward", "left", "right", "stop"],
    "actions": ["sit", "stand", "lie", "shake", "jump", "spin"],
    "special": ["dance", "patrol", "follow", "guard"]
}

# 日志配置
LOG_CONFIG = {
    "level": "INFO",
    "format": "%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    "file": "logs/dog_agent.log"
}