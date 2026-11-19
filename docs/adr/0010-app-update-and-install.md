# 应用检查更新与安装包下载

**Status**: accepted

**Date**: 2026-11-19

## Context

v1.0.0 只把安装包放在官网下载区，三端客户端都没有更新入口。用户必须自己访问官网、比对版本、下载对应文件；版本、大小与 SHA-256 分散在网页和 `SHA256SUMS.txt` 中，容易漏看。

本决定需要同时满足以下约束：

1. **更新源必须与配对服务器解耦**。配对服务器可能是用户自建、可能离线、可能从未初始化；把更新检查挂在配对服务器上，会让未配对设备无法更新，并可能把设备令牌带到非官方主机。
2. **三端能力不同**。Android 可以在用户授权未知来源后安装 APK；Desktop 可以打开 DMG / MSI / DEB 交给系统安装；iOS 不能绕过 App Store / TestFlight 安装，只能检查并展示说明。
3. **学习版仍是 HTTP + IP 直连、安装包未签名**（ADR-0003、ADR-0005）。更新通道没有 TLS，SHA-256 只能证明文件与清单一致，不能证明来源真实性；这一点必须对用户明示。
4. **不扩大到后台能力**。AGENTS 禁止后台推送 / 通知、前台服务与轮询；Android 唯一可接受的例外是系统 DownloadManager 自己发布的下载通知，且不申请 `POST_NOTIFICATIONS`。
5. **用户控制优先**。不做强制更新、最低版本、灰度或自动安装；自动检查只静默提示，安装必须由用户点击触发。

用户于 2026-11-19 确认：三端都显示更新能力；iOS 只检查不安装；批准 Android 新增 `REQUEST_INSTALL_PACKAGES`；更新源为官方发行服务器；版本线为 v1.1.0；发布说明使用三语文件；自动检查默认开启且每次冷启动一次；红点在打开弹窗时清除；Android 使用 DownloadManager；Desktop 在应用运行期间继续下载。

## Decision

### 1. 公开更新描述接口

- 新增 `GET /api/v1/app/latest`，无鉴权，只由官方发行服务器提供。
- 响应由下载清单生成：`version`、`publishedAt`（取清单 `generatedAt`）、`notes`（zh-CN / zh-TW / en）、固定五项 `platforms`；`downloadUrl` 由服务端用 `PUBLIC_BASE_URL + "/downloads/" + file` 拼接，清单本身不存 URL。
- 清单缺失、解析失败、`schemaVersion != 2` 或字段非法统一返回 `503 SERVICE_UNAVAILABLE`；不新增错误码，不泄露文件路径。
- 响应 `Cache-Control: no-store`，支持 `HEAD`；新增按 IP 的 60 次/分钟限流。
- 日志只记录错误码，不记录说明正文、下载 URL 或文件路径。

### 2. 下载清单升级到 schemaVersion 2

- `downloads.json` 增加 `notes` 三语字段；发布说明源文件位于 `release-notes/v<version>/{zh-CN,zh-TW,en}.md`。
- `zh-CN.md` 必需；`zh-TW.md` / `en.md` 缺失时回退 `zh-CN`；单文件上限 64 KiB。
- `scripts/generate-downloads-manifest.sh` 增加 `--notes-dir`，并覆盖引号、反斜杠、制表符与换行的 JSON 转义。
- 上传顺序保持 ADR-0009：四个安装包 → `SHA256SUMS.txt` → `downloads.json` 最后原子切换；保留 `.prev` 回滚。
- 官网解析器先兼容 schema 1 与 2，再切换线上清单；客户端在 schema 2 上线后才启用更新接口。

### 3. 客户端共享更新层

- 新增 `shared/commonMain/.../update/`：DTO、纯数字版本比较、纯 Kotlin SHA-256、无鉴权 `UpdateApi`、进程级 `UpdateController` 与平台 `UpdateInstaller`。
- `UpdateController` 由 `AppContainer` 创建，使用 `services.appScope`，不进入 `AppSession`；配对、改址、解绑只替换会话，不重建更新控制器。
- 更新 API 的 base URL 只取 `services.defaultServerUrl`，绝不读取 `settings.serverUrl`、`initialServerUrl()` 或会话令牌；更新请求不带 `Authorization`。
- 自动检查在每次冷启动 `AppContainer.start()` 成功后执行一次；失败静默，不阻塞首帧。手动检查才展示失败原因。
- 设置项 `autoUpdateCheck` 默认开启；关闭后只跳过自动检查，手动检查仍可用。
- 红点 `hasUnseenUpdate` 只存内存；打开更新弹窗即清除；不做「忽略此版本」，不落盘。

### 4. 平台安装边界

- **Android**：Manifest 仅新增 `REQUEST_INSTALL_PACKAGES`；使用 DownloadManager 下载到应用外部文件目录，FileProvider 暴露只读 URI，`canRequestPackageInstalls()` 为 false 时引导系统未知来源设置，用户点击「安装」后才 `ACTION_VIEW`。不申请 `POST_NOTIFICATIONS`、不建前台服务、不静默安装。
- **Desktop**：Ktor 流式下载到应用数据目录的 `updates/<file>.part`，完成后原子重命名；关闭弹窗不取消，退出应用才取消并删除 `.part`；校验通过后用 `Desktop.open` 打开 DMG / MSI / DEB。
- **iOS**：只检查并展示版本、日期与说明；`available=false` 或无平台文件时显示「iOS 暂未提供安装包」，不下载、不打开安装器、不修改 ATS。

### 5. 安全与失败处理

- 下载前若 `downloadUrl` 为 `http://` 或安装包未签名，先展示风险确认；用户确认后才开始。
- 下载完成后必须校验清单中的 SHA-256；不匹配立即删除文件并进入失败态；校验只防损坏与错文件，不构成来源认证。
- 校验通过后只保留当前版本文件；新检查开始前清理旧文件与失败残留。
- 下载中断：Android 由系统下载器继续；Desktop 在应用运行期间继续，退出应用时取消并清理。

### 6. 版本线与发布顺序

- v1.1.0：Android `versionCode 2`；Desktop `packageVersion 1.1.0`；iOS `MARKETING_VERSION 1.1.0` / `CURRENT_PROJECT_VERSION 2`；官网 `VERSION` 与 `check-release-version.sh` 同步。
- 发布顺序：官网解析器兼容 v1/v2 → 服务端部署 → 1.1.0 版本与三语说明 → tag → GitHub Release → CI 生成并上线 schema 2 清单 → 在线验证 → 公告。

## Considered Options

- **复用配对服务器作为更新源**：不需要新增公网接口，但未配对设备无法检查更新，自建服务器可能没有产物，且会把设备令牌与更新请求绑定到非官方主机；拒绝。
- **客户端直接读 GitHub Releases API**：省去服务端接口，但引入第二个公开来源、额外限流与网络可达性问题，且与官网下载区已有产物重复；拒绝。
- **打开官网下载页让用户手动下载**：实现最轻，但没有应用内进度、没有 SHA 校验、无法覆盖安装，退回 v1.0.0 的体验；拒绝。
- **静默下载并自动安装**：体验最短，但越过用户授权，风险与平台政策都不可接受；拒绝。
- **Android 应用内 HTTP 下载 + FileProvider**：可控性更强，但没有系统级后台与下载通知，进程被杀后无法继续；选择 DownloadManager，并接受通知权限的系统差异。
- **新增 `POST_NOTIFICATIONS` 或前台服务**：可保证通知展示，但超出最小权限原则；拒绝，改用应用内进度与完成态兜底。
- **iOS 提供安装包下载**：需要 App Store / TestFlight 或企业签名，超出学习版范围；拒绝，只做检查。
- **强制更新 / 最低版本 / 灰度**：学习版没有服务端强制能力，且违背用户控制；拒绝。
- **为发布说明引入 Markdown 渲染**：需要新增依赖与安全边界；拒绝，按纯文本保留换行展示。

## Consequences

- `AGENTS.md` §3 增加唯一权限例外 `REQUEST_INSTALL_PACKAGES`、DownloadManager 通知例外，以及「官方发行服务器 / 无令牌 / SHA-256 / 不静默安装」四条硬不变量。
- `docs/api.md` 新增 §13；`docs/功能需求文档.md` 新增 REQ-SET-012/013 与 AC-21…AC-26；两份技术架构文档追加更新子节；`docs/GLOSSARY.md` 增加发行服务器、配对服务器、更新检查、更新描述、发布说明、安装包校验。
- 服务端在清单 v2 上线前对 `app/latest` 返回 503；客户端必须能容忍 503 并保持当前版本，不视为崩溃或强制下线。
- Android 新增一个权限与 FileProvider；iOS 权限不变；Desktop 不新增系统权限，仅写入应用数据目录。
- HTTP + 未签名仍是已知局限：SHA-256 不防同源篡改；将来引入 TLS 或代码签名需要新的 ADR，不在 v1.1.0 范围内。
- 更新检查每次冷启动最多产生一个发往官方服务器的请求，不携带设备标识、正文或令牌；失败不重试、不轮询。
- 回滚以服务端清单为主：保留 `downloads.json.prev`，客户端遇到 503 或旧版本时继续正常使用。
