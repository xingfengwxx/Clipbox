# Clipbox 客户端

Kotlin Multiplatform 客户端：`composeApp`（Compose Multiplatform UI 与平台入口）+ `shared`（共享层）。
包名 `com.wangxingxing.clipbox`；Android 8.0+（minSdk 26）、iOS 16+、Desktop（本版只产出 macOS 包）。

## 前置

- JDK 21（`JAVA_HOME` 已配置）
- Android SDK：platform 36、build-tools 36
- iOS：完整 Xcode（`xcode-select -p` 必须指向 `/Applications/Xcode.app/Contents/Developer`，仅 Command Line Tools 无法链接 framework）

## 构建与运行

```bash
cd ClipboxClient

# Android debug 构建
./gradlew :composeApp:assembleDebug

# Desktop 启动
./gradlew :composeApp:run

# 共享层测试
./gradlew :shared:allTests

# iOS 模拟器 framework 链接
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

默认服务器地址按构建模式声明：debug 构建与 `./gradlew :composeApp:run` 使用 `http://127.0.0.1:8080`；release 构建与 DMG 产物使用 `http://117.72.121.6:8080`。

Android 本地联调：先 `adb reverse tcp:8080 tcp:8080`，再安装 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`。

## 版本基线

全部版本锁定在 `gradle/libs.versions.toml`：AGP 8.13.2、Kotlin 2.2.21、Compose Multiplatform 1.9.3、Gradle 8.14.3。
AGP 9 起 KMP 插件与 `com.android.application` 不再兼容，升级前必须先调整模块结构。
