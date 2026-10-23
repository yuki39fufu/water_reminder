# Water Reminder 💧

![Android](https://img.shields.io/badge/Android-13.0%2B-green.svg?style=flat&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?style=flat&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg?style=flat&logo=jetpackcompose)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Water Reminder** 是一款现代、极简且智能的 Android 饮水追踪应用。采用最新的 Jetpack Compose 构建，拥有沉浸式的 UI 设计、流畅的交互动画以及高度可定制的提醒功能，旨在帮助用户养成健康的饮水习惯。

（本项目完全由Deepseek 与Gemini构建，所以别提Issue，你看我注释都扔在代码里头没删）

## ✨ 主要功能

### 核心体验
* **交互式水杯**: 摒弃传统按钮，采用独特的**拖拽水面**交互。手指在杯子上向下滑动即可直观地添加饮水量。
* **沉浸式设计**: 全面适配全面屏体验，界面延伸至状态栏与导航栏下方。
* **动态主题**: 支持 **Material You (Dynamic Color)**。
* **庆祝特效**: 当每日目标达成时，主界面会绽放全屏**烟花特效** 🎉

### 智能提醒
* **精准推送**: 使用 `AlarmManager` 的 `setAlarmClock` 调度，锁屏 / Doze / 厂商省电策略下仍能准时送达。
* **非打扰式交互**:
    * **快捷推迟**: 通知栏包含 "5分钟后" 快捷按钮。
    * **自定义推迟**: 支持在通知栏直接输入分钟数进行推迟。
    * **静默更新**: 所有的状态变更均通过静音通道发送，避免打扰。
    * **智能静音**: 如果当前饮水量已达标，后续的常规提醒将自动静音。
* **晚间达标检查**:
    * 可自定义每日检查时间。
    * 可自定义达标阈值。
    * 若届时饮水量不足设定百分比，将发送额外提醒鼓励补水。

### 个性化设置
* **目标设定**: 自由设定每日饮水目标（mL），或根据体重自动计算（`体重(kg) × 35 − 1000 mL`）。
* **提醒管理**: 列表式管理每日提醒时间点，支持一键添加/删除。
* **外观管理**: 手动切换跟随系统、浅色、深色或纯黑（OLED）模式。
* **数据重置**: 支持手动重置今日数据。

## 🛠️ 技术栈

本项目完全基于现代 Android 开发标准构建：

* **语言**: [Kotlin](https://kotlinlang.org/)
* **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3)
* **架构模式**: MVVM (Model-View-ViewModel)
* **数据持久化**: SharedPreferences
* **字体**: Google Sans Code（随 APK 打包，不依赖系统字体）
* **后台任务**:
    * `AlarmManager` (`setAlarmClock`，锁屏 / Doze 下仍能准时唤醒)
    * `BroadcastReceiver`
* **动画**: Compose Animation API (`Animatable`, `Canvas` 粒子系统)
* **兼容性**: Min SDK 33 (Android 13.0) ~ Target SDK 36

## 📥 安装与构建

### 直接安装
请前往 [Releases](../../releases) 页面下载最新版本的 APK 文件（`v1.2.1`）。

### 源码构建
1.  克隆仓库：
    ```bash
    git clone https://github.com/yuki39fufu/water_reminder.git
    ```
2.  环境要求：**JDK 17**、**Android SDK Platform 36** + **Build-Tools 35/36**，Gradle 由 wrapper 自动下载（8.13）。
3.  使用 **Android Studio (Ladybug 或更新版本)** 打开项目，等待 Gradle Sync 完成。
4.  连接 Android 设备或启动模拟器，点击运行 (Run)。

纯命令行构建：
```bash
# 先在项目根目录写 local.properties
# sdk.dir=D:\\Dev\\android-sdk
./gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # 未配置签名时产出 app-release-unsigned.apk
```

**签名**：在项目根目录创建 `keystore.properties`（已在 `.gitignore` 中排除）：
```properties
storeFile=D:/keys/water-release.jks
storePassword=******
keyAlias=water
keyPassword=******
```
存在该文件时 release 会自动使用它签名，否则输出 unsigned APK。

### 权限说明
* **通知权限**（Android 13+）：首次启动会弹窗申请；拒绝后可在「设置 → 权限 → 通知权限」里重新打开。
* **电池优化**：首次启动会引导把应用加入「不受电池优化限制」，否则系统可能延迟或吞掉后台闹钟；可在「设置 → 权限 → 电池优化」里复查。
* **精确闹钟**（Android 12+）：Android 14 起默认不再预授权，可在「设置 → 权限 → 精确闹钟」里打开；未开启时提醒仍会触发，但可能延迟几分钟。
* 重启手机、解锁前、系统时间 / 时区变化或应用更新后，闹钟会由 `BootReceiver` 自动重排。

### 遇到问题？
构建、签名或安装卡住了？**问大肥鱼**。

## 👤 作者

**Water Reminder** developed with ❤️ by **yuki39fufu**.

*Powered by Deepseek & Gemini* （AI真是太好用了家人们）

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源许可证。
