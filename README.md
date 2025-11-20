# Water Reminder 💧

![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg?style=flat&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?style=flat&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg?style=flat&logo=jetpackcompose)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Water Reminder** 是一款现代、极简且智能的 Android 饮水追踪应用。它采用最新的 Jetpack Compose 构建，拥有沉浸式的 UI 设计、流畅的交互动画以及高度可定制的提醒功能，旨在帮助用户养成健康的饮水习惯。

（本项目完全由Gemini 3构建，所以别提Issues，你看我注释都扔在代码里头没删）

## ✨ 主要功能 (Features)

### 核心体验
* **交互式水杯**: 摒弃传统按钮，采用独特的**拖拽水面**交互。手指在杯子上向下滑动即可直观地添加饮水量。
* **沉浸式设计**: 全面适配 Edge-to-Edge（全面屏）体验，界面延伸至状态栏与导航栏下方。
* **动态主题**: 支持 **Material You (Dynamic Color)** ，完美适配 **深色模式 (Dark Mode)**。
* **庆祝特效**: 当每日目标达成时，主界面会绽放全屏**烟花特效** 🎉。

### 智能提醒
* **精准推送**: 使用 `AlarmManager` 精确调度，确保提醒准时送达（支持 Android 12+ 精确闹钟权限处理）。
* **非打扰式交互**:
    * **快捷推迟**: 通知栏包含 "5分钟后" 快捷按钮。
    * **自定义推迟**: 支持在通知栏直接输入分钟数进行推迟。
    * **静默更新**: 所有的状态变更（如推迟成功）均通过静音通道发送，避免打扰。
    * **智能静音**: 如果当前饮水量已达标，后续的常规提醒将自动静默。
* **晚间达标检查**:
    * 可自定义每日检查时间（默认 20:00）。
    * 可自定义达标阈值（默认 50%）。
    * 若届时饮水量不足设定百分比，将发送额外提醒鼓励补水。

### 个性化设置
* **目标设定**: 自由设定每日饮水目标（mL）和杯子容量。
* **提醒管理**: 列表式管理每日提醒时间点，支持一键添加/删除。
* **外观管理**: 手动切换跟随系统、强制浅色或强制深色模式。
* **数据重置**: 支持手动重置今日数据。

## 🛠️ 技术栈 (Tech Stack)

本项目完全基于现代 Android 开发标准构建：

* **语言**: [Kotlin](https://kotlinlang.org/)
* **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3)
* **架构模式**: MVVM (Model-View-ViewModel)
* **数据持久化**: SharedPreferences
* **后台任务**:
    * `AlarmManager` (`setExactAndAllowWhileIdle` for Doze mode compatibility)
    * `BroadcastReceiver`
* **动画**: Compose Animation API (`Animatable`, `Canvas` 粒子系统)
* **兼容性**: Min SDK 26 (Android 8.0) ~ Target SDK 35

## 📥 安装与构建 (Installation)

### 直接安装
请前往 [Releases](../../releases) 页面下载最新版本的 APK 文件 (`v1.1.1`)。

### 源码构建
1.  克隆仓库：
    ```bash
    git clone [https://github.com/yuichi39/water-reminder.git](https://github.com/yuichi39/water-reminder.git)
    ```
2.  使用 **Android Studio (Ladybug 或更新版本)** 打开项目。
3.  等待 Gradle Sync 完成。
4.  连接 Android 设备或启动模拟器。
5.  点击运行 (Run) 按钮。

**注意**: 如果在 Android 12+ 设备上运行，请确保授予应用“设置闹钟和提醒”的权限，以保证提醒功能的准确性。

## 📝 版本历史 (Changelog)

* **v1.1.1**
    * 更多细节。
    * 全新图标。
* **v1.1.0**
    * 新增：每日目标达成时的烟花庆祝特效。
    * 新增：晚间达标检查功能的百分比阈值可自定义（滑条+输入框）。
    * 优化：通知机制降级为标准通知，不再显示状态栏闹钟图标。
    * 优化：修复深色模式下状态栏字体颜色问题。
    * 优化：设置页 UI 调整，增加版本信息页脚。
* **v1.0.0**
    * 初始版本发布。
    * 基础饮水记录、梯形杯子交互、定时提醒功能。

## 👤 作者 (Author)

**Water Reminder** developed with ❤️ by **yuichi39**.

*Powered by Gemini* （AI真是太好用了家人们）

## 📄 许可证 (License)

本项目采用 [MIT License](LICENSE) 开源许可证。
