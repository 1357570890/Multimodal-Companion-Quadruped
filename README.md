# "感语智契" 多模态驱动智能陪伴机器狗 | Multimodal Companion Quadruped

[![Language](https://img.shields.io/badge/Language-Python%20%7C%20Java-blue.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](#)
[![Platform](https://img.shields.io/badge/Platform-Raspberry%20Pi%20CM4%20%7C%20Android-orange.svg)](#)

本工程是 **第七届中国研究生人工智能创新大赛全国三等奖** 与 **第二十届中国研究生电子设计竞赛省级一等奖** 的核心项目源码。项目基于大语言模型决策系统与边缘计算视觉，赋予四足机器人（机器狗）多模态自主决策、机器视觉感知与无线互联控制能力。

This project is the core source code of the award-winning works (National 3rd Prize in China Graduate AI Innovation Competition & Provincial 1st Prize in China Graduate Electronics Design Competition). It integrates a Large Language Model (LLM) decision system with edge computer vision to empower a quadruped robot with autonomous behavior, multimodal interaction, and wireless control.

---

## 🌟 核心技术亮点 / Key Features

### 1. 具身认知与大模型决策 (Embodied AI & LLM Decision Making)
- **多模态闭环控制**：集成 `ASR 语音输入 -> 讯飞星火大模型语义理解 -> 结构化 JSON 动作指令生成 -> 底层电机/舵机执行 -> TTS 实时语音反馈` 闭环。
- **动态行为编排**：设计 System Prompt 限制，模型能够自动提取命令中的意图，生成连贯动作序列（例如：“转个圈，帮我把绿色的木块夹起来，然后趴下” 对应生成包含三个动作函数的 JSON 指令并依次执行）。

### 2. 边缘端机器视觉与姿态跟踪 (Edge Vision & Pose Tracking)
- **目标检测与定位**：基于 `YOLOfast` 和 `OpenCV` 在树莓派 CM4 边缘端高效运行，实现对特定物体、人脸、球类的实时检测与跟踪。
- **肢体与手势控制**：结合 `MediaPipe` 框架，提供高帧率的人体姿态估计和手势识别追踪，实现非接触式的手势交互控制。

### 3. 跨平台网络集成与控制 (Cross-platform Integration & Streaming)
- **Android 控制终端**：基于 Java 开发配套的 Android App，通过 TCP/UDP 自定义协议链实现低延迟控制。
- **低延迟无线图传**：将树莓派摄像头的实时画面低延迟推送到手机端或 Flask Web 监控端。

---

## 📂 项目结构 / Directory Structure

```text
Multimodal-Companion-Quadruped/
├── demos/                    # 核心演示与AI模块 / Core AI & Demo scripts
│   ├── API_KEY.py            # API 密钥配置占位符 / API Credentials Configuration
│   ├── dog_agent/            # 大模型代理决策系统 / LLM Agent node for robot dog
│   ├── Free_QA/              # 语音聊天与问答模块 / Voice QA system
│   ├── Image_create/         # 文生图算法模块 / Text-to-Image creation
│   ├── pic_comprehension/    # 多模态图生文模块 / Image understanding & description
│   ├── speech_AI_caw/        # 语音抓取特定物体 / Voice-controlled claw task
│   ├── speech_AI_food/       # 语音抓取食品/球类 / Voice-controlled food tracking
│   ├── speech_AI_line/       # 语音引导巡线任务 / Voice-controlled line tracking
│   └── *.py                  # 各项传感器、视觉(MediaPipe/YOLO)控制脚本 / CV & hardware control scripts
├── treecer/                  # 辅助决策缓存 / Tracking cache
└── tts/                      # 语音合成与网联模块 / TTS & Web communication
    ├── VoiceControlApp/      # Android App 工程源码 / Android Application project
    ├── flask_voice_display/  # 基于 Flask 的可视化仪表盘 / Web dashboard
    └── streaming_tts_xunfei_demo.py  # 讯飞流式 TTS 交互节点 / Streaming TTS demo
```

---

## 🛠️ 安装与配置 / Setup & Configuration

### 1. 硬件依赖
- 搭载 Raspberry Pi CM4 的四足机器人底座 (如 XGO-Mini 等)。
- 树莓派摄像头、麦克风输入与音频输出设备。

### 2. 软件依赖安装
```bash
pip install -r requirements.txt
# 核心通信与控制库
pip install websocket-client
pip install --upgrade xgo-pythonlib
```

### 3. 配置 API 凭证
在使用大模型相关功能前，请先在 `demos/API_KEY.py` 中填入你的 API Key，或者直接设置系统环境变量：
```bash
# Linux / Raspberry Pi
export XF_APPID="your_appid"
export XF_API_KEY="your_api_key"
export XF_API_SECRET="your_api_secret"
```

---

## 🚀 快速启动 / Quick Start

### 启动大模型语音助手
进入 `tts` 目录并运行流式语音控制脚本：
```bash
cd tts
python streaming_tts_xunfei_demo.py
```

### 启动手势动作跟踪
运行 `demos` 目录下的 MediaPipe 手势识别控制脚本：
```bash
cd demos
python gesture_action.py
```
