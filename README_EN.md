# maimaiDX-Match-Sign (Arcade Rhythm Game Tournament Management System)

[English](README_EN.md) | [中文](README.md) | [Modify Docs](web_server/CONFIGURATION.md)

> **Disclaimer**: Parts of this project were collaboratively completed using **Gemini 3.0 Pro** and **GPT-5.1 Thinking**.

This is an open-source sign-in and tournament management system designed specifically for maimai DX tournaments, including a Web management dashboard and an Android client. It aims to provide an efficient and modern tournament organization experience.

Project Community Group: [571239223](https://qm.qq.com/q/YEgUKiWNEW)

## Installation

**Before starting, please ensure you have a server running Linux, with a configuration of at least 2C4G 10Mbps. It is recommended to use [Baota Panel](https://www.bt.cn/) for easier environment management and deployment.**

First, clone this repository to your local machine:

```bash
git clone https://github.com/KawaiiMira114/maimaiDX-Match-Sign.git
cd maimaiDX-Match-Sign
```


## Usage Guide


### I. Web Server Deployment

#### 1. Environment Preparation
Ensure your server has installed:
*   [Python 3.8 or higher](https://www.python.org/downloads/)
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
*   Default service address: `http://Your_Server_IP:5000`

#### 4. Access Admin Panel
Open a browser and visit `http://Your_Server_IP:5000/admin`.
*   **Default Admin Account**: `admin`
*   **Default Admin Password**: `admin888`
*   **Sensitive Operation Protection Password** (e.g., clearing data): `1145141919810ax`

>**If you need to change these, please search for relevant fields in `app.py`**

#### Deploy in Baota Panel

If you are using **Baota Panel**, please follow these steps:

**1. Install Python Environment and Create Project**
1.  Upload the `web_server` folder to the server directory (e.g., `/www/wwwroot/game_sign`).
2.  Open Baota Panel, click **Websites** -> **Python Projects** in the left menu.
3.  If no Python version is installed, click **Python Version Manager** to install the **latest** Python 3.x version.
4.  **Create Virtual Environment**: Please create a virtual environment in **Websites** -> **Python Environment Management**. The environment source here should be the version we just installed, not pyenv.
5.  Click **Add Python Project**:
    *   **Project Path**: Select the uploaded `web_server` folder.
    *   **Startup File**: Select `app.py`.
    *   **Port**: Enter `5000`.
    *   **Python Version**: Select the **virtual environment just created**.
    *   **Framework**: Select `Flask`.
    *   **Startup Method**: Select `uWSGI`.
    *   Click **Confirm** and wait for dependency installation and virtual environment creation to complete.

**2. Set Domain and External Mapping**
1.  Find the newly created project in the project list, click **Settings** -> **Domain Management**, and add a domain pointing to the server IP.
2.  Click **External Mapping** and ensure this option is enabled.
3.  **Open Port**: Return to the Baota Panel homepage, click **Security** -> **System Firewall** -> **Add Port Rule**, and release port `5000` (protocol select TCP).

**3. Check Access Connectivity**
Enter your bound domain (or `http://Your_Server_IP:5000`) in the browser to check if the page can be accessed normally. If access fails, please ensure you have strictly followed the steps in this tutorial and troubleshoot yourself.

---

### II. Android Client Configuration & Compilation

If you need to use the Android App, you need to compile the APK yourself.

#### 1. Environment Preparation
*   [Android Studio](https://developer.android.com/studio)
*   JDK 17

#### 2. Modify Server Address
The App needs to know which server to connect to.
1.  Open file: `android_app/app/src/main/java/com/harbin/gamesign/data/api/ApiClient.kt`
2.  Find the `BASE_URL` variable:
    ```kotlin
    // Please modify this to the IP address of the computer where you deployed the Web Server
    // Example: http://192.168.1.100:5000/
    const val BASE_URL = "http://YOUR_SERVER_IP:5000/"
    ```

#### 3. Compile APK
1.  Open the `android_app` directory with Android Studio.
2.  Wait for Gradle sync to complete.
3.  Click menu bar **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**, or use the shortcut Ctrl+F9.
4.  After compilation is complete, the APK will be output to \android_app\app\build\outputs\apk\debug\app-debug.apk. Send it to your mobile device to install.

## Features

### Web Admin Dashboard
- **Dashboard**: Real-time view of sign-in counts, group distribution, promotion status, and other key data.
- Capable of supporting large-scale tournaments, system stable and not prone to crashing.
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

### Player Client (Web & Android)
- **Multi-platform Support**: Provides responsive Web pages and a native Android application.
- **Convenient Sign-in**:
  - Android client integrates **QR Code Scanning** for instant sign-in.
  - Web client supports link jump sign-in.
- **Status Inquiry**: Real-time view of own match number, group, and current status (whether on machine, whether promoted).

## Open Source License

This project is open-sourced under the [MIT License](LICENSE).
