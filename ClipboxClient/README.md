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

## 打包与交付

```bash
cd ClipboxClient

# Android debug APK（versionName 1.0.0）
./gradlew :composeApp:assembleDebug
# 产物：composeApp/build/outputs/apk/debug/composeApp-debug.apk

# macOS DMG（应用名 Theca，版本 1.0.0）
./gradlew :composeApp:packageDmg
# 产物：composeApp/build/compose/binaries/main/dmg/Theca-1.0.0.dmg
```

- `compose.desktop { nativeDistributions }` 锁定 `packageName = "Theca"`、`packageVersion = "1.0.0"`、DMG targetFormat 与 macOS bundleID；默认 jlink 运行时额外包含 `jdk.unsupported`（DataStore 的 protobuf 依赖 `sun.misc.Unsafe`，缺失时打包产物启动即失败）。
- 学习版默认只交付 Android debug APK 与 macOS DMG：不混淆、不缩小，DMG 未签名；不产出 Windows / Linux 包与 iOS 真机签名包。
- Android 签名材料放在本地 `signing/` 目录（整目录已加入 `.gitignore`，不提交）：`keystore.properties` 提供 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`；debug 与 release 共用同一签名，文件缺失时 debug 回落默认调试签名、release 保持未签名。keystore 与密码必须离线备份，丢失后无法以同一签名更新已安装应用。
- 产物副本与 SHA-256 放在仓库根 `dist/`（已加入 `.gitignore`，不提交）：`composeApp-debug.apk`、`Theca-1.0.0.dmg`、`SHA256SUMS.txt`；哈希与验收证据见 `docs/tasks/release-artifacts.md`。
- iOS 交付为模拟器构建记录：`linkDebugFrameworkIosSimulatorArm64` 与 `xcodebuild ... -sdk iphonesimulator` 均通过，完整输出在 `dist/ios-build.log`。

## 版本基线

全部版本锁定在 `gradle/libs.versions.toml`：AGP 8.13.2、Kotlin 2.2.21、Compose Multiplatform 1.9.3、Gradle 8.14.3。
AGP 9 起 KMP 插件与 `com.android.application` 不再兼容，升级前必须先调整模块结构。
