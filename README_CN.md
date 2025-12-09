# maimaiDX-Match-Sign (比赛签到系统)

[English](README.md) | [中文](README_CN.md)

> ⚠️ **声明**：本项目完全由 AI 生成，核心逻辑与代码构建由 **Gemini 3.0 Pro** 与 **GPT-5.1 Thinking** 协作完成。

这是一个专为 maimai DX 比赛设计的开源签到与赛事管理系统，包含 Web 管理端与 Android 客户端。旨在提供高效、现代化的赛事组织体验。

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

## 🚀 快速开始

本项目分为两个主要部分，请分别参考对应的文档进行配置：

*   **服务端部署**: [Web Server README](web_server/README.md)
*   **安卓客户端**: [Android App README](android_app/README.md)

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源。
