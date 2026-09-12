# Kokoro-82M-Android-Plus

基于 [puff-dayo/Kokoro-82M-Android](https://github.com/puff-dayo/Kokoro-82M-Android) 二次开发的安卓语音合成与 AI 对话应用。

原项目是 Kokoro-82M 本地 TTS 引擎的安卓演示，只支持英文，中文报错，UI 基础。本项目在原项目基础上进行了大幅改造，把 TTS 引擎换成了安卓系统 TTS（支持中文），并加入了多提供商 AI 对话、会话管理、图片上传、云端 TTS 等功能。

---

## 界面预览

### 语音合成

![语音合成](preview/01-synthesis.png)

支持系统 TTS 和云端 TTS 两种模式，可调节语速，一键播放或保存到历史。

### AI 聊天

![AI 聊天](preview/02-chat.png)

支持 11 种 AI 提供商，会话管理，模型切换，图片上传。

### 历史记录

![历史记录](preview/03-history.png)

所有合成记录保存在本地，支持点击回放和导出 JSON。

### 设置

![设置](preview/04-settings.png)

管理多个 AI API 配置，支持测试连接和自动获取模型。

### 关于

![关于](preview/05-about.png)

项目信息、开源协议和特别感谢。

---

## 功能特性

### 语音合成

- 系统 TTS：调用安卓系统自带引擎，支持中文，离线可用
- 云端 TTS：兼容 OpenAI `/v1/audio/speech` 接口，音质更好，音色可选
- 语速调节：0.5x ~ 2.0x
- 一键播放与保存：保存后自动进入历史记录

### AI 对话

支持 11 种 AI 提供商：

| 提供商 | 类型 | 默认模型 |
|---|---|---|
| DeepSeek | OpenAI 兼容 | deepseek-chat |
| OpenAI | OpenAI 兼容 | gpt-4o-mini |
| Google Gemini | Gemini 原生 | gemini-1.5-flash |
| OpenRouter | OpenAI 兼容 | openai/gpt-3.5-turbo |
| Claude | Claude 原生 | claude-3-haiku |
| Ollama | 本地部署 | llama3 |
| 阿里云百炼 | OpenAI 兼容 | qwen-turbo |
| Moonshot | OpenAI 兼容 | moonshot-v1-8k |
| 智谱 | OpenAI 兼容 | glm-4 |
| xAI | OpenAI 兼容 | grok-1 |
| 自定义 | OpenAI / Gemini / Claude / Ollama | 手动填写 |

主要能力：

- API 配置池：添加、编辑、删除多个配置，自由切换
- 测试连接：发送一条测试消息，验证 API Key 和地址是否可用
- 自动获取模型：从 API 拉取可用模型列表并保存
- 会话式对话：每次新建是独立会话，支持重命名、删除
- 对话内切换模型：同一会话内可切换不同模型
- 消息自动保存：所有对话内容自动持久化
- 图片上传：支持发送图片，多模态模型可识别图片内容

### 历史记录

- 所有合成内容保存在本地 JSON 文件
- 点击记录即可回放
- 支持一键导出为 JSON 文件并分享

### 界面

- 卡片式布局，圆角风格
- 深色模式，一键切换
- 动画过渡：页面切换 Crossfade，消息和列表项淡入 + 上滑

---

## 与原项目的区别

| 项目 | 原项目 | 本项目 |
|---|---|---|
| TTS 引擎 | Kokoro-82M 本地模型 | 系统 TTS + 云端 TTS |
| 中文支持 | 不支持 | 支持 |
| AI 对话 | 无 | 支持 11 种提供商 |
| 会话管理 | 无 | 支持新建、重命名、删除 |
| 图片上传 | 无 | 支持 |
| 历史记录 | 无 | 支持回放与导出 JSON |
| 界面 | 基础 | 卡片式布局 + 动画 |
| 语言 | 英文 | 中文 |

原项目的 Kokoro 引擎相关文件（ModelManager、PhonemeConverter、Tokenizer 等）已在本项目中移除，改为使用系统 TTS。

---

## 技术栈

- 语言：Kotlin 2.0
- UI：Jetpack Compose + Material 3
- 网络：OkHttp 4.12
- 异步：Kotlin Coroutines
- 序列化：Android 内置 org.json
- TTS：Android TextToSpeech API + MediaPlayer

---

## 开发环境

- Android Studio 或 Termux
- Kotlin 2.0
- JDK 17
- Android SDK 34
- Min SDK 26
- Target SDK 34

---

## 编译与安装

### 方式一：GitHub Actions 云端编译（推荐）

本项目已配置 GitHub Actions，推送到 `master` 分支后自动构建。

1. 查看构建状态：https://github.com/ZW-SYS/Kokoro-82M-Android-Plus/actions
2. 构建完成后，点进绿色的运行记录，页面底部 Artifacts 区域下载 app-debug.zip
3. 解压得到 app-debug.apk，传到手机安装

### 方式二：本地编译

```
git clone https://github.com/ZW-SYS/Kokoro-82M-Android-Plus.git
cd Kokoro-82M-Android-Plus
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 项目结构

```
app/src/main/java/com/example/kokoro82m/
├── MainActivity.kt                主入口，所有界面
├── screens/
│   └── HistoryScreen.kt           历史记录页
└── utils/
    ├── ApiService.kt              AI 请求与 API 配置存储
    ├── ChatHistoryRepository.kt   会话数据存储
    ├── HistoryRepository.kt       合成历史存储
    ├── ExportHelper.kt            历史导出 JSON
    └── TtsService.kt              云端 TTS 服务
```

---

## 开源协议

GPL-3.0

本项目基于 [puff-dayo/Kokoro-82M-Android](https://github.com/puff-dayo/Kokoro-82M-Android) 二次开发，遵循 GPL-3.0 协议开源。任何基于本项目的衍生作品也必须以 GPL-3.0 协议开源。

---

## 特别感谢

- [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) (Apache 2.0)
- [Kokoro-ONNX](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX) (MIT)
- CMU 词典
- IPA 转写器 (GPL-3.0)
- Android NNAPI
- [puff-dayo](https://github.com/puff-dayo) — 原项目作者

---

## 开发者

ZW-SYS