# Clipbox / Theca

离线优先的跨设备文本中转工具，附带本地开发者工具箱。跨平台，使用 Kotlin Multiplatform 与 Compose Multiplatform 构建。v1.0.0 交付 Android、iOS 模拟器、macOS、Windows 与 Linux；服务端为单实例 Ktor + SQLite。

- 客户端：`ClipboxClient/`（Kotlin Multiplatform + Compose Multiplatform）
- 服务端：`ClipboxServer/`（Kotlin + Ktor + Exposed + SQLite WAL）
- 官网：`ClipboxWeb/`（Vite + Vue 3）
- 产品与架构文档：`docs/`；执行协议：`AGENTS.md`

## 本地构建

```bash
# 服务端测试与运行
cd ClipboxServer
./gradlew test
./gradlew run

# 客户端测试与 Android debug 构建
cd ClipboxClient
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run

# 官网
cd ClipboxWeb
npm ci
npm run dev
npm run build
```

服务端默认监听 `http://localhost:8080`；Android 模拟器/真机先执行 `adb reverse tcp:8080 tcp:8080`。

## 发布产物

v1.0.0 由 release-only GitHub Actions 构建，产物上传 GitHub Release：

| 平台 | 产物 |
| --- | --- |
| Android | `Theca-v1.0.0-android-release.apk`（release 签名） |
| macOS arm64 | `Theca-v1.0.0-macos-arm64-release.dmg`（未签名、无公证） |
| Windows amd64 | `Theca-v1.0.0-windows-amd64-release.msi`（未签名） |
| Linux amd64 | `Theca-v1.0.0-linux-amd64-release.deb`（未签名） |
| iOS | 仅模拟器构建，不提供 IPA |

- 命名规范：`Theca-v<version>-<platform>[-<arch>]-<debug|release>.<ext>`；Android 为 universal 单包，不加架构段；正式产物为 `-release`，本地 debug 交付示例 `Theca-v1.0.0-android-debug.apk`。

- 触发方式：推送 `v*` tag，或在 Actions 页面手动执行 `Release`；手动执行只做 dry run，不创建 Release。
- 版本一致性：`scripts/check-release-version.sh` 校验 Android `versionName`、Desktop `packageVersion`、iOS `MARKETING_VERSION` 与官网 `VERSION`。
- 安装提示：macOS 首次打开需右键 → 打开；Windows SmartScreen 选择「更多信息 → 仍要运行」；Linux 用 `sudo dpkg -i`，不提供 apt 仓库。所有产物应核对 `SHA256SUMS.txt`。
- Windows / Linux 未做安装与运行验收，属于 v1.0.0 已知限制，详见 `docs/tasks/release-artifacts.md`。
- 正式产物哈希以 tag 触发的 CI 为准；`downloads/` 由人工同步到服务器，服务器凭据不进入 CI。

## 安全边界

v1.0.0 为 HTTP + IP 直连的学习版，不含 TLS / 域名 / 反代；不集成第三方日志、崩溃或分析 SDK。Android 签名材料位于 `ClipboxClient/signing/` 且仍被 Git 跟踪，仓库为 public，按 `docs/tasks/security-review.md` F4 接受风险，仅用于学习版公开分发，不得用于应用商店。

## 文档入口

- 任务拆解与状态：`docs/tasks/README.md`
- 接口契约：`docs/api.md`
- 产品行为与验收：`docs/功能需求文档.md`
- 官网需求：`docs/web-功能需求.md`
- 发布决策：[ADR-0005](docs/adr/0005-v1-multiplatform-release.md)
- iOS 真机前置：`docs/tasks/ios-release-prerequisites.md`
