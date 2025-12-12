# maimaiDX-Match-Sign (Tournament Sign-in System)

[English](README_EN.md) | [中文](README.md) | [Configuration](web_server/CONFIGURATION.md)

> ⚠️ **Disclaimer**: This project was partially constructed using **Gemini 3.0 Pro** and **GPT-5.1 Thinking**.

This is an open-source sign-in and tournament management system designed specifically for maimai DX tournaments. It includes a Web Admin Panel and an Android Client App, aiming to provide an efficient and modern tournament organization experience.

## 📥 Installation

First, clone this repository to your local machine:

```bash
git clone https://github.com/KawaiiMira114/maimaiDX-Match-Sign.git
cd maimaiDX-Match-Sign
```

## ✨ Features

### 🖥️ Web Admin Panel
- **Modern UI Design**: Fully adopts **Material Design 3**, offering a beautiful and smooth experience.
- **Dashboard**: Real-time overview of sign-in counts, group distribution, promotion status, and other key data.
- **Player Management**:
  - Batch import/export of player lists.
  - Fuzzy search for players.
  - Create, Read, Update, Delete (CRUD) operations for player information.
- **Tournament Flow Control**:
  - **Auto Grouping & Draw**: Randomly generate player numbers and groups.
  - **Schedule Management**: Supports Qualifiers, Top 16, Semi-finals, Finals, and more.
  - **Promotion/Elimination**: One-click management of player promotion or elimination status.
- **Exclusive Sign-in Features**:
  - Generate unique QR codes and sign-in links for players.
  - Support for quick QR code scanning sign-in.

### 📱 Player Client (Web & Android)
- **Multi-platform Support**: Responsive Web interface and native Android application.
- **Convenient Sign-in**:
  - Android app integrates **QR Code Scanning** for instant sign-in.
  - Web client supports sign-in via link redirection.
- **Status Inquiry**: Real-time checking of match number, grouping, and current status (e.g., On Machine, Promoted).

## 🛠️ Tech Stack

*   **Backend**: Python (Flask), SQLAlchemy (SQLite)
*   **Frontend**: HTML5, CSS3 (Material 3), JavaScript
*   **Mobile**: Android (Kotlin, Jetpack Compose)

## 📖 Usage Guide

**Before starting, please ensure you have a server running Linux. Using [BT Panel (Baota)](https://www.bt.cn/) is recommended for easier environment management and deployment.**

### I. Web Server Deployment

#### 1. Prerequisites
Ensure your server has the following installed:
*   [Python 3.8 or higher](https://www.python.org/downloads/)
*   `pip` (Python Package Manager)

#### 2. Install Dependencies
Open a terminal, navigate to the `web_server` directory, and install the required libraries:
```bash
cd web_server
pip install -r requirements.txt
```

#### 3. Start the Service
Run the following command in the `web_server` directory:
```bash
python app.py
```
*   The `data.db` database file will be created automatically on the first run.
*   Default service address: `http://localhost:5000` (Local) or `http://YOUR_SERVER_IP:5000` (LAN/Public).

#### 4. Access Admin Panel
Open a browser and visit `http://localhost:5000/admin`.
*   **Default Admin Username**: `admin`
*   **Default Admin Password**: `admin888`
*   **Sensitive Operation Password** (e.g., Clear Data): `1145141919810ax`

> 💡 **Tip**: It is recommended to search for and change these default passwords in `app.py` for security.

#### 🛡️ BT Panel (Baota) Deployment Guide

If you are using **BT Panel**, follow these steps:

**1. Install Python Environment and Create Project**
1.  Upload the `web_server` folder to your server directory (e.g., `/www/wwwroot/game_sign`).
2.  Open BT Panel, go to **Websites** -> **Python Projects**.
3.  If no Python version is installed, click **Python Version Manager** to install the latest Python 3.x.
4.  Click **Add Python Project**:
    *   **Project Path**: Select the uploaded `web_server` folder.
    *   **Startup File**: Select `app.py`.
    *   **Port**: Enter `5000`.
    *   **Python Version**: Select the installed Python version.
    *   **Framework**: Select `Flask`.
    *   **Startup Method**: `uWSGI` or `Gunicorn` is recommended.
    *   Click **Submit** and wait for dependency installation and virtual environment creation.

**2. Configure Domain and Mapping**
1.  Find your newly created project in the list, click **Settings** -> **Domain Management**, and add the domain pointing to your server IP.
2.  Click **External Mapping** and ensure it is enabled (if required).
3.  **Open Port**: Go back to the BT Panel homepage, click **Security** -> **Add Port Rule**, and allow port `5000` (Protocol: TCP).

**3. Check Connectivity**
Enter your domain (or `http://SERVER_IP:5000`) in a browser to check if the page loads correctly. If it fails, check your security group rules and project logs.

---

### II. Android Client Configuration & Build

If you need to use the Android App, you must build the APK yourself.

#### 1. Prerequisites
*   [Android Studio](https://developer.android.com/studio) (Latest version recommended)
*   JDK 17 (Usually included with Android Studio)

#### 2. Modify Server Address (Critical!)
The App needs to know which server to connect to.
1.  Open file: `android_app/app/src/main/java/com/harbin/gamesign/data/api/ApiClient.kt`
2.  Find the `BASE_URL` variable:
    ```kotlin
    // Change this to the IP address of the computer hosting the Web Server
    // Example: http://192.168.1.100:5000/
    const val BASE_URL = "http://YOUR_SERVER_IP:5000/"
    ```
    *   ⚠️ Note: **Do NOT** use `localhost`, as `localhost` on the phone refers to the phone itself.
    *   Ensure the phone and computer are on the **same LAN** (connected to the same WiFi), or use a public IP/Domain.

#### 3. Build APK
1.  Open the `android_app` directory with Android Studio.
2.  Wait for Gradle sync to complete.
3.  Click menu **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
4.  Once built, transfer the generated APK to your phone and install it.

## 📄 License

This project is licensed under the [MIT License](LICENSE).
