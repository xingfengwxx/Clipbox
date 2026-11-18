# Clipbox 客户端

Kotlin Multiplatform 客户端：`composeApp`（Compose Multiplatform UI 与平台入口）+ `shared`（共享层）。
包名 `com.wangxingxing.clipbox`；Android 8.0+（minSdk 26）、iOS 16+、Desktop（Windows / macOS / Linux）。v1.0.0 产出 Android release APK、macOS arm64 DMG、Windows x64 MSI 与 Linux x86_64 DEB；桌面安装包均未签名。

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

# Android release 构建
./gradlew :composeApp:assembleRelease

# 桌面安装包：只能在对应操作系统构建
./gradlew :composeApp:packageDmg   # macOS
./gradlew :composeApp:packageMsi   # Windows，需 WiX 3.14
./gradlew :composeApp:packageDeb   # Linux，需 dpkg / fakeroot

# iOS 模拟器 framework 链接
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

默认服务器地址按构建模式声明：debug 构建与 `./gradlew :composeApp:run` 使用 `http://127.0.0.1:8080`；release 构建与 DMG 产物使用 `http://117.72.121.6:8080`。

Android 本地联调：先 `adb reverse tcp:8080 tcp:8080`，再安装 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`。

## 打包与交付

```bash
cd ClipboxClient

# Android release APK（versionName 1.0.0）
./gradlew :composeApp:assembleRelease
# 产物：composeApp/build/outputs/apk/release/composeApp-release.apk

# macOS DMG（应用名 Theca，版本 1.0.0；仅 macOS）
./gradlew :composeApp:packageDmg
# 产物：composeApp/build/compose/binaries/main/dmg/Theca-1.0.0.dmg

# Windows MSI（仅 Windows，需 WiX 3.14）
./gradlew :composeApp:packageMsi
# 产物：composeApp/build/compose/binaries/main/msi/Theca-1.0.0.msi

# Linux DEB（仅 Linux，需 dpkg / fakeroot）
./gradlew :composeApp:packageDeb
# 产物：composeApp/build/compose/binaries/main/deb/ 下的 .deb，CI 统一重命名
```

- `compose.desktop { nativeDistributions }` 锁定 `packageName = "Theca"`、`packageVersion = "1.0.0"`、`targetFormats(Dmg, Msi, Deb)`、三个平台图标与 macOS bundleID；默认 jlink 运行时额外包含 `jdk.unsupported`（DataStore 的 protobuf 依赖 `sun.misc.Unsafe`，缺失时打包产物启动即失败）。
- 图标位于 `composeApp/packaging/`：`Theca.icns`（macOS）、`Theca.ico`（Windows，16–256 多尺寸）、`Theca.png`（Linux，512×512），均由 `design/brand/exports/` 的 PNG 生成，不依赖额外构建插件。
- 对外正式产物由 tag 触发的 release-only GitHub Actions 构建并上传 GitHub Release；本机 macOS 只回归 DMG，MSI / DEB 由 `windows-2022` 与 `ubuntu-24.04` runner 构建，不在 macOS 上伪造。
- 桌面安装包均未签名：macOS 首次打开需右键 → 打开（无公证）；Windows SmartScreen 可能提示「未知发布者」，选择「更多信息 → 仍要运行」（无 Authenticode）；Linux DEB 无 GPG 签名，安装用 `sudo dpkg -i <file>.deb`，没有 apt 仓库，下载后应核对 SHA-256。
- Android 签名材料位于 `ClipboxClient/signing/`（`keystore.properties` + `clipbox-release.jks`），当前仍被 Git 跟踪；仓库为 public，按 `docs/tasks/security-review.md` F4 由用户接受风险，仅用于学习版公开分发，不得用于应用商店。keystore 与密码必须离线备份；未来轮换密钥库后，已安装用户可能需卸载重装。
- 首轮调试产物与 SHA-256 记录在 `dist/`（已加入 `.gitignore`，不提交）：`composeApp-debug.apk`、`Theca-1.0.0.dmg`、`SHA256SUMS.txt`；正式产物的哈希与验收证据见 `docs/tasks/release-artifacts.md`。
- iOS 交付为模拟器构建记录：`linkDebugFrameworkIosSimulatorArm64` 与 `xcodebuild ... -sdk iphonesimulator` 均通过，完整输出在 `dist/ios-build.log`；真机发布前置见 `docs/tasks/ios-release-prerequisites.md`。

## 版本基线

全部版本锁定在 `gradle/libs.versions.toml`：AGP 8.13.2、Kotlin 2.2.21、Compose Multiplatform 1.9.3、Gradle 8.14.3。
AGP 9 起 KMP 插件与 `com.android.application` 不再兼容，升级前必须先调整模块结构。
