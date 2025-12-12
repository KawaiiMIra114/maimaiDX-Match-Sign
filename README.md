# maimaiDX-Match-Sign (比赛签到系统)

[English](README_EN.md) | [中文](README.md) | [修改文档](web_server/CONFIGURATION.md)

> ⚠️ **声明**：本项目部分环节使用 **Gemini 3.0 Pro** 与 **GPT-5.1 Thinking** 协作完成。

这是一个专为 maimai DX 比赛设计的开源签到与赛事管理系统，包含 Web 管理端与 Android 客户端。旨在提供高效、现代化的赛事组织体验。

## 📥 获取项目 (Installation)

首先，你需要将本项目克隆到本地：

```bash
git clone https://github.com/KawaiiMira114/maimaiDX-Match-Sign.git
cd maimaiDX-Match-Sign
```

## ✨ 功能特性

### 🖥️ Web 管理端
- **现代化 UI 设计**：全面采用 **Material Design 3** 设计语言，美观流畅。
- **仪表盘 (Dashboard)**：实时查看签到人数、组别分布、晋级状况等关键数据。
- **选手管理**：
  - 支持批量导入/导出选手名单。
  - 支持模糊搜索选手。
  - 选手信息的增删改查（CRUD）。
- **赛事流程控制**：
  - **自动分组与抽签**：支持随机生成选手编号与分组。
  - **赛程管理**：涵盖海选（Qualifiers）、16强、半决赛、决赛等阶段。
  - **晋级/淘汰管理**：一键操作选手的晋级或淘汰状态。
- **专属签到功能**：
  - 为选手生成专属二维码与签到链接。
  - 支持扫码快速签到。

### 📱 选手端 (Web & Android)
- **多端支持**：提供响应式 Web 页面与原生 Android 应用。
- **便捷签到**：
  - Android 端集成 **QR Code 扫描** 功能，秒级签到。
  - Web 端支持链接跳转签到。
- **状态查询**：实时查看自己的比赛编号、分组、当前状态（是否在机、是否晋级）。

## 🛠️ 技术栈

*   **后端**: Python (Flask), SQLAlchemy (SQLite)
*   **前端**: HTML5, CSS3 (Material 3), JavaScript
*   **移动端**: Android (Kotlin, Jetpack Compose)

## 📖 使用指南

**在一切开始前，请确保你有一台运行 Linux 系统的服务器。推荐使用 [宝塔面板](https://www.bt.cn/) 进行操作，以便更轻松地管理环境和部署。**

### 一、Web 服务端部署

#### 1. 环境准备
确保你的服务器已安装：
*   [Python 3.8 或更高版本](https://www.python.org/downloads/)
*   `pip` (Python 包管理器)

#### 2. 安装依赖
打开终端（命令行），进入 `web_server` 目录并安装所需库：
```bash
cd web_server
pip install -r requirements.txt
```

#### 3. 启动服务
在 `web_server` 目录下运行：
```bash
python app.py
```
*   首次运行会自动创建数据库文件 `data.db`。
*   默认服务地址：`http://localhost:5000` (本机访问) 或 `http://你的IP地址:5000` (局域网访问)。

#### 4. 访问后台
打开浏览器访问 `http://localhost:5000/admin`。
*   **默认管理员账号**：`admin`
*   **默认管理员密码**：`admin888`
*   **敏感操作保护密码**（如清空数据）：`1145141919810ax`

> 💡 **提示**：建议在 `app.py` 文件中搜索并修改这些默认密码，以确保安全。

#### 🛡️ 宝塔面板部署指南

如果您使用 **宝塔面板**，请按照以下步骤操作：

**1. 安装 Python 环境并创建项目**
1.  将 `web_server` 文件夹上传到服务器目录（例如 `/www/wwwroot/game_sign`）。
2.  打开宝塔面板，点击左侧菜单栏的 **网站** -> **Python 项目**。
3.  如果没有安装 Python 版本，请点击 **Python 版本管理器** 安装最新的 Python 3.x 版本。
4.  点击 **添加 Python 项目**：
    *   **项目路径**：选择上传的 `web_server` 文件夹。
    *   **启动文件**：选择 `app.py`。
    *   **端口**：输入 `5000`。
    *   **Python 版本**：选择已安装的 Python 版本。
    *   **框架**：选择 `Flask`。
    *   **启动方式**：推荐选择 `uWSGI` 或 `Gunicorn`。
    *   点击 **提交**，等待依赖安装和虚拟环境创建完成。

**2. 设置域名与外网映射**
1.  在项目列表中找到刚刚创建的项目，点击 **设置** -> **域名管理**，添加解析到该服务器 IP 的域名。
2.  点击 **外网映射**，确保该选项已开启（如果需要）。
3.  **开放端口**：回到宝塔面板主页，点击 **安全** -> **添加端口规则**，放行 `5000` 端口（协议选择 TCP）。

**3. 检查访问连通性**
在浏览器中输入您绑定的域名（或 `http://服务器IP:5000`），检查是否可以正常访问页面。如果访问失败，请检查安全组规则和项目运行日志。

---

### 二、Android 客户端配置与编译

如果你需要使用 Android App，需要自行编译 APK。

#### 1. 环境准备
*   [Android Studio](https://developer.android.com/studio) (推荐最新版)
*   JDK 17 (通常 Android Studio 自带)

#### 2. 修改服务器地址 (关键！)
App 需要知道连接到哪台服务器。
1.  打开文件：`android_app/app/src/main/java/com/harbin/gamesign/data/api/ApiClient.kt`
2.  找到 `BASE_URL` 变量：
    ```kotlin
    // 请将此处修改为你部署 Web Server 的电脑 IP 地址
    // 例如：http://192.168.1.100:5000/
    const val BASE_URL = "http://YOUR_SERVER_IP:5000/"
    ```
    *   ⚠️ 注意：**不要**使用 `localhost`，因为在手机上 `localhost` 指的是手机自己。
    *   确保手机和电脑在**同一局域网**（连接同一个 WiFi），或者使用公网 IP/域名。

#### 3. 编译 APK
1.  用 Android Studio 打开 `android_app` 目录。
2.  等待 Gradle 同步完成。
3.  点击菜单栏 **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**。
4.  编译完成后，将生成的 APK 发送到手机安装即可。

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。
