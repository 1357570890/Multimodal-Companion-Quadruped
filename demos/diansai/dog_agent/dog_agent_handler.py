import os
import sys
import time
import threading
import logging
from datetime import datetime
print(1)
# 导入语音识别相关
from dog_speak_iat import *  # 音频识别

# 导入动作规划相关
from dog_agent import *  # 动作编排

# 导入基础动作控制
from dog_base_control import *

# 导入特殊动作
from dog_caw_api import * # 抓取
from dog_football_api import *  # 踢球

# 导入语音合成
from dog_tts import *  # 语音合成并播放

# 配置日志
logging.basicConfig(level=logging.INFO,
                   format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('dog_agent_handler')

# 全局变量
VOICE_FOLDER = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'uploads', 'voice')
response = ''

def Speak_Voice():
    """语音合成并播放"""
    global response
    try:
        Dog_speaktts(response)
        logger.info(f"语音播放: {response}")
    except Exception as e:
        logger.error(f"语音播放失败: {e}")

def process_voice():
    """处理语音文件并执行相应操作"""
    global response
    record_path = os.path.join(VOICE_FOLDER, 'record.mp3')
    
    try:
        # 语音识别
        rectext = rec_wav_music()
        logger.info(f"语音识别结果: {rectext}")
        
        if rectext:
            # 动作规划
            agent_plan_output = eval(Dog_agent_plan(rectext))
            logger.info(f'智能体编排动作: {agent_plan_output}')
            
            # 获取响应文本
            response = agent_plan_output['response']
            logger.info(f'准备播放响应: {response}')
            
            # 开启语音播放线程
            tts_thread = threading.Thread(target=Speak_Voice)
            tts_thread.daemon = True
            tts_thread.start()
            
            # 执行动作序列
            for action in agent_plan_output['function']:
                logger.info(f'执行动作: {action}')
                try:
                    eval(action)
                except Exception as e:
                    logger.error(f"动作执行失败: {action}, 错误: {e}")
                    continue
            
            return True
        else:
            logger.warning("没有识别到任何信息")
            return False
            
    except Exception as e:
        logger.error(f"处理语音命令失败: {e}")
        return False
    
    finally:
        # 处理完成后删除语音文件
        try:
            if os.path.exists(record_path):
                os.remove(record_path)
                logger.info("语音文件处理完成，已删除")
        except Exception as e:
            logger.error(f"删除语音文件失败: {e}")

if __name__ == '__main__':
    process_voice() 
