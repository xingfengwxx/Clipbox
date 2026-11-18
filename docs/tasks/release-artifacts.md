# T-REL-05 打包与交付验收记录

- 日期：2026-11-18
- 范围：Android debug APK、macOS Desktop DMG（Theca 1.0.0）与 iOS 模拟器构建记录
- 截图与临时输出：`~/Documents/ClipboxREL05/`（仓库外，不提交二进制）
- 产物副本：仓库根 `dist/`（`.gitignore` 忽略，不提交）

## 1. 环境

| 项 | 环境 |
| --- | --- |
| 主机 | macOS arm64，JDK 21.0.13（Microsoft OpenJDK） |
| Android | `emulator-5554`，Medium_Phone_API_37（sdk_gphone64_arm64），debug APK |
| 服务端 | `ClipboxServer`，`:8080`，数据目录 `ClipboxServer/data/`；`PUBLIC_BASE_URL=http://localhost:8080`，`ADMIN_TOKEN` 为一次性本地测试值 |
| Desktop | DMG 挂载 `/Volumes/Theca`，Theca.app 直接启动；数据目录 `~/Library/Application Support/Theca` |
| iOS | Xcode 27.2，iPhone 16 模拟器（iOS 27.2），仅模拟器构建 |

## 2. 构建命令与结果

| 命令 | 结果 |
| --- | --- |
| `cd ClipboxClient && ./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL in 11s；`composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| `cd ClipboxClient && ./gradlew :composeApp:packageDmg` | BUILD SUCCESSFUL in 2m 20s；`composeApp/build/compose/binaries/main/dmg/Theca-1.0.0.dmg` |
| `shasum -a 256 composeApp/build/outputs/apk/debug/composeApp-debug.apk` | `bd4896298275f01ba795570b527075af44d0c68551732c42ca6c9e979c948d0b` |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL in 17s |
| `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build` | ** BUILD SUCCEEDED **；完整日志 `dist/ios-build.log` |

DMG 元数据（`plutil` 读取 `Theca.app/Contents/Info.plist`）：`CFBundleShortVersionString=1.0.0`、`CFBundleVersion=1.0.0`、`CFBundleIdentifier=com.wangxingxing.clipbox`、`CFBundleName=Theca`。

## 3. 产物与 SHA-256

| 产物 | 大小 | SHA-256 |
| --- | --- | --- |
| `dist/composeApp-debug.apk` | 19 MB | `bd4896298275f01ba795570b527075af44d0c68551732c42ca6c9e979c948d0b` |
| `dist/Theca-1.0.0.dmg` | 79 MB | `75b91ae0d07c9fab4e26a0cec338cbc36879cbb19233aaa6c0f3943bff90fd47` |

`dist/SHA256SUMS.txt` 记录以上两行；`dist/` 与 `dist/ios-build.log` 均不提交。

## 4. Android 验收（AVD）

| 步骤 | 命令 / 操作与证据 | 结果 |
| --- | --- | --- |
| 安装 | `adb install -r composeApp-debug.apk` | `Success` |
| 版本 | `adb shell dumpsys package com.wangxingxing.clipbox \| grep versionName` | `versionName=1.0.0`（versionCode=1） |
| 配对 | 清空应用数据后启动 → 配对页默认 `http://127.0.0.1:8080`，输入管理接口生成的 6 位码 → 服务端 `POST /api/v1/pair 201`，随后 `GET /api/v1/clips 200` 拉回 43 条历史 | 通过；截图 `rel05-android-paired.png` |
| 发送 | 输入 `rel05-android-avd-1794973286` → `POST /api/v1/clips 201`；服务端库 `server_seq=56`；历史计数 43 → 44 | 通过；截图 `rel05-android-send-input.png`、`rel05-android-sent.png` |
| 真机 | 当前无 Android 真机连接 | 未执行，见 §6-1 |

## 5. Desktop 验收（DMG）

| 步骤 | 命令 / 操作与证据 | 结果 |
| --- | --- | --- |
| 挂载 | `hdiutil attach -nobrowse -readonly Theca-1.0.0.dmg` | 挂载到 `/Volumes/Theca`，含 `Theca.app` |
| 启动 | 直接运行 `Theca.app/Contents/MacOS/Theca` | 进程存活，窗口标题 `Theca`、尺寸 1000×700（CGWindowList）；截图 `rel05-desktop-window.png` |
| 同步 | 复用已有配对（`~/Library/Application Support/Theca`），服务端记录 `GET /api/v1/clips 200`（deviceId `01M7MBC8XAXC8930GVAQ9E81H2`） | 通过（界面显示「已配对 / 已同步」） |
| 剪贴板 | Android 再发送 `rel05-desktop-clip-1794973711`（`POST /api/v1/clips 201`，`server_seq=57`），DMG 应用收到后写入系统剪贴板 | `pbpaste` 输出与正文完全一致；通过 |
| 分享页 | 记录右键菜单 → 生成分享（24 小时）→ 复制链接：`http://localhost:8080/s/{token}`；服务端 `POST /api/v1/shares 201`，`GET /s/{token}` 返回 200 | 通过；截图 `rel05-desktop-menu.png`、`rel05-desktop-share-dialog.png`、`rel05-desktop-share-dialog3.png`、`rel05-share-browser.png` |
| 分享页安全头 | 响应含 `Content-Security-Policy: default-src 'none'; ...`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Cache-Control: no-store` | 通过 |
| 卸载镜像 | 退出应用后 `hdiutil detach /Volumes/Theca` | 正常弹出 |

## 6. 偏差与已知限制

1. **真机安装未执行**（用户确认）：本机只连接了 AVD `Medium_Phone_API_37`，无 Android 真机；APK 安装、配对与发送均在 AVD 完成。真机安装留待后续补充或列为 v1.0.0 已知限制。
2. **跨路径改动 `composeApp/build.gradle.kts`**（用户批准）：为产出符合品牌规范的 macOS 包，新增 `nativeDistributions`：`packageName=Theca`、`packageVersion=1.0.0`、`targetFormats(Dmg)`、`macOS.bundleID=com.wangxingxing.clipbox`。Compose 1.9 未显式声明 targetFormats 时不会注册 `packageDmg`。
3. **jlink 运行时补模块**：首次 DMG 启动失败（`NoClassDefFoundError: sun/misc/Unsafe`，DataStore protobuf 触发），补充 `modules("jdk.unsupported")` 后重建并启动成功；默认运行时模块为 java.base / java.desktop / java.logging / jdk.crypto.ec。
4. **DMG 未签名**：任务卡禁止引入签名证书，产物为本地未签名构建；直接从挂载卷启动，未经过 Gatekeeper 分发路径。
5. **Desktop 数据目录复用**：DMG 与 `./gradlew :composeApp:run` 共用 `~/Library/Application Support/Theca`；验收沿用已有配对与历史，未清空用户数据。
6. **iOS 仅模拟器**：按交付范围未导出真机签名包；`dist/ios-build.log` 保留两条构建命令完整输出。

## 7. 结论

- Android debug APK 与 macOS DMG 均可用、版本号 1.0.0，哈希已记录；DMG 窗口、剪贴板、分享页三项验证通过。
- iOS 模拟器构建命令可复现并通过。
- 本卡 DoD 满足（真机安装按用户决定登记为已知限制）。

---

## 2026-11-18 追加：配对步骤变更（ADR-0006）

上文“管理接口生成的 6 位码”已废弃。当前流程为：空库首台设备初始化 → 已配对设备设置页生成配对新设备码 → 新设备配对。T-REL-15 重建 Android / macOS 产物并更新台账；Windows / Linux 由 release CI 重建。
