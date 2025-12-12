# maimaiDX-Match-Sign (Tournament Sign-in System)

[English](README_EN.md) | [中文](README.md) | [Modify Docs](web_server/CONFIGURATION.md)

> ⚠️ **Disclaimer**: Parts of this project were collaboratively completed using **Gemini 3.0 Pro** and **GPT-5.1 Thinking**.

This is an open-source sign-in and tournament management system designed specifically for maimai DX tournaments, including a Web management dashboard and an Android client. It aims to provide an efficient and modern tournament organization experience.

Project Community Group: `https://qm.qq.com/q/QgljYbEA46`

## 📥 Installation

First, clone this repository to your local machine:

```bash
git clone https://github.com/KawaiiMira114/maimaiDX-Match-Sign.git
cd maimaiDX-Match-Sign
```

## ✨ Features

### 🖥️ Web Admin Dashboard
- **Modern UI Design**: Fully adopts **Material Design 3** language, beautiful and smooth.
- **Dashboard**: Real-time view of sign-in counts, group distribution, promotion status, and other key data.
- **Player Management**:
  - Support for batch import/export of player lists.
  - Support for fuzzy search of players.
  - CRUD operations for player information.
- **Tournament Process Control**:
  - **Automatic Grouping & Draw**: Support for randomly generating player numbers and groups.
  - **Schedule Management**: Covers Qualifiers, Top 16, Semi-finals, Finals, etc.
  - **Promotion/Elimination Management**: One-click operation for player promotion or elimination status.
- **Exclusive Sign-in Function**:
  - Generate exclusive QR codes and sign-in links for players.
  - Support for quick QR code scanning sign-in.

### 📱 Player Client (Web & Android)
- **Multi-platform Support**: Provides responsive Web pages and a native Android application.
- **Convenient Sign-in**:
  - Android client integrates **QR Code Scanning** for instant sign-in.
  - Web client supports link jump sign-in.
- **Status Inquiry**: Real-time view of own match number, group, and current status (whether on machine, whether promoted).

## 🛠️ Tech Stack

*   **Backend**: Python (Flask), SQLAlchemy (SQLite)
*   **Frontend**: HTML5, CSS3 (Material 3), JavaScript
*   **Mobile**: Android (Kotlin, Jetpack Compose)

## 📖 Usage Guide

**Before starting, please ensure you have a server running Linux. It is recommended to use `https://www.bt.cn/` (Baota Panel) for easier environment management and deployment.**

### I. Web Server Deployment

#### 1. Environment Preparation
Ensure your server has installed:
*   `https://www.python.org/downloads/`
*   `pip` (Python Package Manager)

#### 2. Install Dependencies
Open the terminal, navigate to the `web_server` directory, and install the required libraries:
```bash
cd web_server
pip install -r requirements.txt
```

#### 3. Start Service
Run the following command in the `web_server` directory:
```bash
python app.py
```
*   The first run will automatically create the database file `data.db`.
*   Default service address: `http://localhost:5000` (local access) or `http://YOUR_IP_ADDRESS:5000` (LAN access).

#### 4. Access Admin Panel
Open a browser and visit `http://localhost:5000/admin`.
*   **Default Admin Account**: `admin`
*   **Default Admin Password**: `admin888`
*   **Sensitive Operation Protection Password** (e.g., clearing data): `1145141919810ax`

> 💡 **Tip**: It is recommended to search for and modify these default passwords in the `app.py` file to ensure security.

#### 🛡️ Baota Panel Deployment Guide

If you are using **Baota Panel**, please follow these steps:

**1. Install Python Environment and Create Project**
1.  Upload the `web_server` folder to the server directory (e.g., `/www/wwwroot/game_sign`).
2.  Open Baota Panel, click **Websites** -> **Python Projects** in the left menu.
3.  If no Python version is installed, click **Python Version Manager** to install the latest Python 3.x version.
4.  Click **Add Python Project**:
    *   **Project Path**: Select the uploaded `web_server` folder.
    *   **Startup File**: Select `app.py`.
    *   **Port**: Enter `5000`.
    *   **Python Version**: Select the installed Python version.
    *   **Framework**: Select `Flask`.
    *   **Startup Method**: Recommended to select `uWSGI` or `Gunicorn`.
    *   Click **Submit** and wait for dependency installation and virtual environment creation to complete.

**2. Set Domain and External Mapping**
1.  Find the newly created project in the project list, click **Settings** -> **Domain Management**, and add a domain pointing to the server IP.
2.  Click **External Mapping** and ensure this option is enabled (if needed).
3.  **Open Port**: Return to the Baota Panel homepage, click **Security** -> **Add Port Rule**, and release port `5000` (protocol select TCP).

**3. Check Access Connectivity**
Enter your bound domain (or `http://SERVER_IP:5000`) in the browser to check if the page can be accessed normally. If access fails, please check security group rules and project run logs.

---

### II. Android Client Configuration & Compilation

If you need to use the Android App, you need to compile the APK yourself.

#### 1. Environment Preparation
*   `https://developer.android.com/studio` (Recommended latest version)
*   JDK 17 (Usually included with Android Studio)

#### 2. Modify Server Address (Critical!)
The App needs to know which server to connect to.
1.  Open file: `android_app/app/src/main/java/com/harbin/gamesign/data/api/ApiClient.kt`
2.  Find the `BASE_URL` variable:
    ```kotlin
    // Please modify this to the IP address of the computer where you deployed the Web Server
    // Example: `http://192.168.1.100:5000/`
    const val BASE_URL = "http://YOUR_SERVER_IP:5000/"
    ```
    *   ⚠️ Note: **Do NOT** use `localhost`, because on the phone `localhost` refers to the phone itself.
    *   Ensure the phone and computer are on the **same LAN** (connected to the same WiFi), or use a public IP/domain.

#### 3. Compile APK
1.  Open the `android_app` directory with Android Studio.
2.  Wait for Gradle sync to complete.
3.  Click menu bar **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
4.  After compilation is complete, send the generated APK to the phone to install.

## 📄 Open Source License

This project is open-sourced under the [MIT License](LICENSE).
