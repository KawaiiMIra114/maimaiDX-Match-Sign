# Game Sign Android App

A native Android application built with Jetpack Compose.

## Prerequisites

*   Android Studio Hedgehog or later.
*   JDK 17.

## Configuration

1.  Open `app/src/main/java/com/harbin/gamesign/data/api/ApiClient.kt`.
2.  Update `BASE_URL` to point to your web server address.
    *   If using Android Emulator, use `http://10.0.2.2:5000/`.
    *   If using a real device, ensure the phone and server are on the same network and use the server's local IP (e.g., `http://192.168.1.x:5000/`).

## Building

1.  Open the project in Android Studio.
2.  Sync Gradle project.
3.  Build > Build Bundle(s) / APK(s) > Build APK(s).

## Features

*   **Sign In**: Enter name or scan QR code.
*   **Status Check**: View current match status and results.
*   **QR Scanner**: Integrated ML Kit scanner.
