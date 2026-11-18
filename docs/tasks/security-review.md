# 安全与隐私复查（T-REL-07）

> 学习版基线：HTTP + IP 直连（[ADR-0003](../adr/0003-http-ip-learning-deployment.md)）。本文件只记录证据与结论，不修改任何已冻结的需求、接口与架构文档。
> 复查日期：2026-11-18；同日复核发布范围扩展（公开仓库 + 官网公开分发）。

## 1. 复查基线

| 项 | 值 |
| --- | --- |
| 代码版本 | `b968d52`（T-REL-06 验收矩阵）之上叠加本卡修复；本卡提交为 `T-REL-07`（见 `git log`） |
| 服务端 | 远程学习服务器 `117.72.121.6:8080`（HTTP，单容器，非 root uid 10001，2 核 2GB 基线） |
| 客户端 | Android API 26+ / iOS 16+ / macOS Desktop（Compose Multiplatform） |
| 复查方法 | 服务端、客户端、构建与仓库三层代码走查 + 自动化测试 + 仓库扫描 |
| 判定口径 | **通过** / **修复**（本卡闭环）/ **接受风险**（ADR-0003 或用户决策） |

## 2. 结论摘要

| # | 检查项 | 结论 |
| --- | --- | --- |
| 1 | 令牌与凭据 | 通过 |
| 2 | 日志与隐私 | 修复 F1 后通过；诊断日志令牌按 M3 Gate D4 一律脱敏 |
| 3 | 分享页安全 | 通过 |
| 4 | 限流 | 通过 |
| 5 | 传输与明文警示 | 通过（HTTP 明文为 ADR-0003 已接受风险） |
| 6 | 应用权限与平台能力 | 修复 F3 后通过 |
| 7 | 仓库卫生 | 通过；签名材料入库（F4）为用户 2026-11-18 决策的接受风险 |
| 8 | 依赖 | 通过 |

必须修复项 3 项（F1/F2/F3）已全部闭环；F4 为用户决策的接受风险项，见 §5、§6。

## 3. 逐项证据

### 3.1 令牌与凭据

| 结论 | 证据 |
| --- | --- |
| 设备令牌与分享令牌为 32 字节随机数的 base64url 无填充 | `ClipboxServer/src/main/kotlin/com/wangxingxing/clipbox/server/util/Hashing.kt:16-24` |
| 服务端只存 SHA-256（64 位十六进制），库表无明文令牌列 | `Hashing.kt:27-37`；`ClipboxServer/src/main/resources/migrations/V001__init.sql:9`（devices.token_hash）、`:39`（shares.token_hash）；`db/Tables.kt` |
| 明文令牌只在配对 / 分享创建响应中返回一次 | `service/PairingService.kt:67-77`；`service/ShareService.kt:54-73` |
| 设备令牌校验只按哈希查库 | `plugins/Security.kt:77`；`ws/SyncRoutes.kt:52-54` |
| 分享令牌先过 `^[A-Za-z0-9_-]{43}$` 格式白名单再哈希查库 | `service/ShareService.kt:88-93`、`:144` |
| 管理接口 `X-Admin-Token` 使用恒定时间比较（双方先 SHA-256 固定长度，再 `MessageDigest.isEqual`） | `service/AdminService.kt:31-35` |
| 客户端令牌只存 DataStore（应用沙箱），无第二处持久化 | `ClipboxClient/shared/src/commonMain/kotlin/com/wangxingxing/clipbox/settings/SettingsRepository.kt:19`、`:66-78`、`:81-87` |
| 令牌只走 `Authorization` 头与 WS 首帧，不进入 URL；WS 地址只拼 scheme/host | `ClipboxClient/shared/src/commonMain/kotlin/com/wangxingxing/clipbox/network/ApiHttpClient.kt:48`；`network/SyncSocket.kt:233`、`:361-368` |
| 分享链接只含分享令牌（ADR-0002 快照设计），不含设备令牌 | `routes/ShareRoutes.kt`；`service/ShareService.kt:54-73` |
| 测试锁定「库中存哈希、响应返回明文」 | `ClipboxServer/src/test/kotlin/com/wangxingxing/clipbox/server/PairingTest.kt:105-106`；`ShareCreateTest.kt:77`、`:88-90` |

### 3.2 日志与隐私

**服务端**

- 未安装 Ktor `CallLogging`，请求日志只输出白名单字段（requestId、方法、路径模板、状态码、耗时、deviceId、errorCode）：`plugins/RequestLogging.kt:39-48`。
- 路径模板化：`/s/{token}` 归一为 `/s/{token}`，未匹配记 `/{unmatched}`，原始 token / ULID 不进日志：`plugins/RequestLogging.kt:57-68`。
- 异常日志只带 requestId、路径模板与堆栈，响应不携带异常细节：`plugins/Errors.kt:125-134`。
- 启动日志只有版本、监听地址、数据目录与 schema 版本，管理令牌 / 设备令牌 / 正文不出现：`Application.kt:100-111`；启动自动生成的配对码按 REQ-NFR-003 允许打印一次（`Application.kt:110`，见 §6 接受风险）。
- 测试锁定：分享 token 只记模板、请求日志无正文与设备令牌、配对码不入日志、管理令牌不入日志、启动日志字段白名单——`LoggingTest.kt:47/64/77/97/115`；`LoggingTest.kt:73/93/111/133` 对应断言。

**客户端（修复 F1 后）**

| 结论 | 证据 |
| --- | --- |
| 关闭时零应用日志输出（sink 前早退） | `ClipboxClient/shared/src/commonMain/kotlin/com/wangxingxing/clipbox/logging/AppLogger.kt:52` |
| debug 默认开、release 默认关（Android 原生按构建类型；iOS / Desktop 修复后按 `isDebug`） | `ClipboxClient/composeApp/src/androidMain/kotlin/com/wangxingxing/clipbox/di/PlatformModule.android.kt:57`；`iosMain/.../PlatformModule.ios.kt:56`；`desktopMain/.../PlatformModule.desktop.kt:48`；`shared/.../logging/AppLogger.kt:22-23` |
| release 手动开启需风险确认弹窗 | `ClipboxClient/composeApp/src/commonMain/kotlin/com/wangxingxing/clipbox/ui/settings/SettingsScreen.kt:188-194`、`:256-265`；`composeResources/values/strings.xml:166-167` |
| 诊断开启时网络日志追加请求 / 响应体，令牌一律脱敏（M3 D4） | `shared/.../network/ApiService.kt:164-190`；`shared/.../logging/AppLogger.kt:70-72`；`shared/.../logging/LogRedactor.kt:14-29` |
| 日志只写本机系统日志（Logcat / NSLog / stdout），不落盘、不上传、无导出 | `composeApp/src/androidMain/.../logging/AndroidLogSink.kt:20`；`iosMain/.../logging/IosLogSink.kt:21`；`desktopMain/.../logging/DesktopLogSink.kt:12`；`shared/.../logging/LogSink.kt` |
| 测试锁定：release 静默、开关即时生效、关闭零输出、诊断正文 + 令牌脱敏 | `shared/src/commonTest/.../logging/LoggingPolicyTest.kt:23/36/49/62`；`network/ApiLoggingTest.kt:23/48/67/85`；`network/SocketLoggingTest.kt:33`；`logging/LoggingRedactorTest.kt:17/25/35/45/55` |

### 3.3 分享页安全

| 结论 | 证据 |
| --- | --- |
| 严格 HTML 转义（`& < > " '` 全覆盖），正文 / 错误页 / 标题统一转义 | `service/SharePageRenderer.kt:79-90`；插入点 `:30`、`:42`、`:52` |
| CSP 与需求原文一致 | `service/SharePageRenderer.kt:14-15`；`routes/ShareRoutes.kt:107` |
| `X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Cache-Control: no-store` 全局覆盖（含错误页） | `plugins/Security.kt:57-59` |
| 模板零外部字体 / 图片 / 脚本，仅内联 `<style>` | `service/SharePageRenderer.kt:45-74` |
| 自动化测试：转义 + 安全响应头 + 一次性消费 | `ClipboxServer/src/test/kotlin/com/wangxingxing/clipbox/server/SharePageTest.kt:43`、`:64`、`:80`、`:99` |
| 真实浏览器验证（T-REL-06 AC-06）：`<script>` 与 `onerror` 载荷全转义、脚本标签 0、外部资源 0，桌面 Chrome 与 AVD Chrome 各渲染一次 | [acceptance-matrix.md](acceptance-matrix.md) AC-06 行 |

### 3.4 限流

| 接口 | 额度 | 证据 |
| --- | --- | --- |
| `POST /api/v1/pair` | 5 次/分钟/IP | `plugins/RateLimits.kt:24`、`:122` |
| `POST /api/v1/admin/pairing-codes` | 10 次/分钟/IP | `RateLimits.kt:25`、`:123` |
| 设备接口 | 120 次/分钟/设备（先限流后写 `last_seen_at`） | `RateLimits.kt:26`；`plugins/Security.kt:92-101` |
| `GET /s/{token}` | 60 次/分钟/IP | `RateLimits.kt:27` |
| `GET /healthz` | 120 次/分钟/IP | `RateLimits.kt:28` |
| WebSocket 连接尝试 | 10 次/分钟/设备，超限关闭码 4429 | `RateLimits.kt:29`；`ws/SyncRoutes.kt:59-62` |
| 429 必须带 `Retry-After`（窗口剩余秒数，1..60） | — | `plugins/Errors.kt:56-63`；`RateLimits.kt:62-63`；测试 `LimitsTest.kt:79/91/172/182/297-302` |

- 计数键只取 TCP 源地址，绝不读取 `X-Forwarded-For`，伪造转发头无法绕过：`plugins/RateLimits.kt:102`；`LimitsTest.kt` 含伪造 XFF 用例。
- WebSocket 未认证升级请求不计入设备尝试闸门与连接上限，仅靠 5 秒认证超时回收：`ws/SyncRoutes.kt:50-66`、`ws/SyncSocketConfig.kt`。api.md §8 只要求设备维度限流，列为接受风险（§6）。

### 3.5 传输与明文警示

| 结论 | 证据 |
| --- | --- |
| 配对页对 `http://` 地址展示「未加密连接，仅用于学习」 | `composeApp/src/commonMain/.../ui/pairing/PairingScreen.kt:117-119`、`:223-239`；判定 `shared/.../settings/ServerUrl.kt:61` |
| 设置页服务器行与改址对话框同样警示 | `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt:318-320`、`:728-735` |
| 文案三语齐备（简中默认） | `composeResources/values/strings.xml:22`；`values-zh-rTW/strings.xml:22`；`values-en/strings.xml:23` |
| 「关于」页披露未加密传输，并（F2 修复后）披露服务端明文存储与无端到端加密 | `SettingsScreen.kt:428-441`；`values/strings.xml:171-174`（三语同名条目） |
| 服务端未下发 HSTS、无 CORS、无 TLS / 301 强制跳转 | `plugins/Security.kt:54`；`ErrorMappingTest.kt:95`（断言无 `Strict-Transport-Security`）；`Application.kt` 仅 Netty 明文端口 |
| Android 只放行明文，不放宽证书 / 主机校验 | `composeApp/src/androidMain/res/xml/network_security_config.xml:2-5` |
| iOS ATS 例外仅学习版所需 | `ClipboxClient/iosApp/iosApp/Info.plist:41-43`（`NSAllowsArbitraryLoads=true`） |
| 部署仅 server 容器、发布 `8080:8080`，无 Caddy / 域名 / 证书 | `docker-compose.yml:6-19`；`ClipboxServer/Dockerfile` |

### 3.6 应用权限与平台能力

| 结论 | 证据 |
| --- | --- |
| Android 仅申请 `INTERNET`，无前台服务、通知、存储、后台剪贴板权限 | `composeApp/src/androidMain/AndroidManifest.xml:4`；合并 release 清单同口径 |
| `AndroidManifest` 声明 `android:allowBackup="false"`（F3 修复）：令牌与明文记录不进入系统 Auto Backup | `AndroidManifest.xml:9` |
| Android 分享入口仅 `text/plain` 的 `ACTION_SEND` / `ACTION_PROCESS_TEXT` | `AndroidManifest.xml:24-38` |
| iOS 主 App 与 Share Extension 无 `UIBackgroundModes`、无 `aps-environment`、无通知权限声明 | `iosApp/iosApp/Info.plist`、`iosApp/ClipboxShareExtension/Info.plist`、`iosApp/iosApp.xcodeproj/project.pbxproj`（检索零命中） |
| 分享扩展无网络能力、无令牌读取 | `ClipboxClient/iosApp/ClipboxShareExtension/ShareViewController.swift`（`URLSession` / `http` / `token` 检索为零，仅读语言偏好） |
| iOS Entitlements 仅 App Group | `iosApp/iosApp/iosApp.entitlements`；`ClipboxShareExtension/ClipboxShareExtension.entitlements` |

### 3.7 仓库卫生

| 结论 | 证据 |
| --- | --- |
| `.gitignore` 覆盖 `.env`、`*.jks`、`*.db(-wal/-shm)`、`/data/`、`/ClipboxServer/data/`、`/dist/` | 根 `.gitignore:17-32` |
| 跟踪文件扫描：无 `.env`、数据库、备份、APK / DMG 等产物；历史新增敏感文件只有 `ClipboxClient/signing/clipbox-release.jks` | `git ls-files` 敏感类型扫描 + `git log --all --diff-filter=A -- '*.env' '*.db' '*.apk' '*.dmg' '*.bak' '*.jks'` |
| 本地 `ClipboxServer/data/clipbox.db`、`dist/*.dmg`、`dist/*.apk` 均被忽略 | `git check-ignore -v` → `.gitignore:29`、`.gitignore:32` |
| Docker 构建上下文排除 `.env`、`data/`、数据库与构建缓存 | `ClipboxServer/.dockerignore` |
| 镜像不以 root 运行、根文件系统只读、`/tmp` tmpfs、内存上限 512m | `ClipboxServer/Dockerfile:15-28`；`docker-compose.yml:18-27` |
| **F4（用户决策）**：`ClipboxClient/signing/clipbox-release.jks` 与 `keystore.properties`（含明文口令）仍被 git 跟踪 | `git ls-files ClipboxClient/signing`；提交 `64a3bea`；仓库为 public（`xingfengwxx/Clipbox`），APK 将通过官网公开分发；用户 2026-11-18 复核后维持现状；见 §5、§6 |
| 观察项：设计稿演示数据含一条 SSH 命令（私网地址、root、非标端口、密钥文件名） | `design/Clipbox 三端设计.html:1208`；不可路由私网地址，建议后续泛化为占位符（不在本卡范围） |

### 3.8 依赖

| 结论 | 证据 |
| --- | --- |
| 服务端无第三方日志 / 崩溃 / 分析 SDK，仅 logback（本地 console） | `ClipboxServer/build.gradle.kts:17-34`；`src/main/resources/logback.xml` |
| 客户端依赖全部为官方 / 一方库（Compose、Ktor Client、Room、DataStore、Koin、coroutines 等） | `ClipboxClient/gradle/libs.versions.toml`；`shared/build.gradle.kts`；`composeApp/build.gradle.kts` |
| 全仓检索 `crashlytics|sentry|bugly|firebase|analytics|umeng|appsflyer|datadog|mixpanel|amplitude|bugsnag` 零命中 | 仓库范围检索 |
| 未接入 Ktor `Logging` 插件（客户端），无落盘 / 上传日志实现 | `shared/.../logging/LogSink.kt` 唯一抽象 |

## 4. 需求条目逐条结论

| 条目 | 结论 | 说明 |
| --- | --- | --- |
| REQ-NFR-001 传输安全 | **通过**（明文传输=接受风险） | 客户端配对页 / 设置页警示齐全；服务端应用层安全（哈希、限流、转义、CSP 四头、日志脱敏、上限）全部保留；未误加 HSTS / TLS。明文传输按 ADR-0003 接受（§6-1） |
| REQ-NFR-002 设备令牌安全 | **修复后通过**（F3）+ 接受风险 | 32 字节、仅返回一次、服务端只存 SHA-256、令牌不入 URL / 日志 / 分享链接；客户端只存 DataStore；新增 `allowBackup=false` 关闭系统备份通道。无 Keystore / Keychain 封装为架构文档登记的接受风险（§6-2） |
| REQ-NFR-003 配对安全 | **通过** + 接受风险 | 6 位数字、10 分钟、一次性；5 次/分钟/IP；配对码短时效明文存库并在过期后清理；启动自动码允许打印一次（§6-3） |
| REQ-NFR-004 分享页安全 | **通过** | 转义 + CSP + `nosniff` / `no-referrer` / `no-store` + 零外部资源；自动化测试与 AC-06 真实浏览器证据齐备 |
| REQ-NFR-005 管理接口安全 | **通过** | 仅 `X-Admin-Token`、恒定时间比较、10 次/分钟/IP、不返回设备令牌或记录内容；防火墙来源限制属部署动作（T-REL-03 已记录） |
| REQ-NFR-006 隐私与日志 | **修复后通过**（F1、F2）+ 接受风险 | 服务端日志无正文 / 令牌 / 配对码 / 分享 token；release 默认零应用日志三端一致；「关于」页补齐服务端明文存储与无端到端加密披露。诊断日志令牌脱敏为 M3 D4 的从严偏差（§6-7） |
| REQ-NFR-007 手机到桌面时延 | **通过**（引用 T-REL-06） | 属性能条目，非本轮安全范围；AC-01 实测服务端落库到桌面剪贴板 ≤1s（[acceptance-matrix.md](acceptance-matrix.md)） |
| REQ-SET-011 诊断日志开关 | **修复后通过**（F1） | debug 默认开、release 默认关；开关持久化立即生效；release 开启需风险确认；开启追加请求 / 响应体，令牌一律脱敏；仅本机系统日志，无导出 / 上报 |

## 5. 必须修复项与闭环记录

| 编号 | 问题 | 修复 | 验证 |
| --- | --- | --- | --- |
| F1 | iOS / Desktop 的 `diagnosticLoggingDefault` 硬编码 `true`，release 首启动诊断日志即开启（含正文的请求 / 响应输出到系统日志），风险确认弹窗永不触发 | `PlatformModule.ios.kt:55-56`、`PlatformModule.desktop.kt:47-48` 改为 `diagnosticLoggingDefault = isDebug`；Desktop `run` 任务已注入 `clipbox.debugBuild=true`，开发运行仍默认开启 | 编译通过（§7-4）；Android 侧原本即符合；`LoggingPolicyTest` 覆盖关闭静默与开关即时生效 |
| F2 | 「关于」页只写本机保留策略，未披露服务端明文存储 / 无端到端加密 | 三语 `settings_about_storage_body` 补充「同步内容在服务端以明文存储，v1.0.0 不含端到端加密」：`values/strings.xml:172`、`values-zh-rTW/strings.xml:172`、`values-en/strings.xml:180`；沿用既有 `HintRow` 展示（`SettingsScreen.kt:428-441`），无布局变更 | 编译通过（§7-4）；文案引用点核对 |
| F3 | Android 未声明 `allowBackup`（默认 true），令牌与明文记录进入系统 Auto Backup | `AndroidManifest.xml:9` 增加 `android:allowBackup="false"` | `:composeApp:assembleDebug` 通过（§7-4），合并清单含该属性 |
| F4 | 签名材料（PKCS12 密钥库 + 明文口令）已被提交进 Git | **用户 2026-11-18 决策：仅记录，不改仓库与历史**；本卡不执行删除跟踪 / 忽略 / 轮换 | 见 §6-4 接受风险与后续建议 |

## 6. 已接受风险

| # | 风险 | 依据 / 决策 | 缓解与边界 |
| --- | --- | --- | --- |
| 1 | 设备令牌与剪贴正文在公网以明文 HTTP / WS 传输，链路中间人可读取与篡改 | ADR-0003；REQ-NFR-001 学习版基线 | 仅个人学习与可信网络使用，不对外分发为安全产品；应用层安全全部保留；未来加 TLS 终止并改 `PUBLIC_BASE_URL` 即可，无需改协议 |
| 2 | 客户端数据（令牌、记录、分享草稿）以明文存储于沙箱，无 Keystore / Keychain 封装、无端到端加密 | `docs/客户端技术架构.md` 已登记；REQ-NFR-002 | 依赖系统应用沙箱隔离；「关于」页与 `docs/README.md` 明确披露；v1.0.0 不引入加密封装 |
| 3 | 启动日志打印一次自动生成的配对码，可读容器日志者可换取设备令牌 | REQ-NFR-003 明确允许（自助配对） | 每次启动仅一条、10 分钟有效、一次性消费；容器日志轮转与访问权限由服务器侧控制（T-REL-03） |
| 4 | `ClipboxClient/signing/clipbox-release.jks` 与明文口令 `keystore.properties` 已进入 Git 历史（提交 `64a3bea`），仍被跟踪 | **用户 2026-11-18 决策：仅记录，不改仓库** | 学习版签名用于本版 APK 覆盖安装与官网公开分发；密钥已随公开仓库暴露，任何人均可签同名 APK，SHA-256 只能校验完整性，不能证明发布者；不得用于应用商店。建议后续：轮换密钥库口令或重建密钥库、`git rm --cached` 并忽略 `signing/`、必要时清理历史并重克隆；轮换后老用户可能需卸载重装 |
| 5 | WebSocket 未认证的升级请求不计入设备尝试闸门与连接上限，可能被用于连接耗尽 | api.md §8 只要求「10 次/分钟/设备」；单实例学习版 | 5 秒认证超时回收、单设备仅一个活跃连接、全局连接上限 100；HTTPS / 反代层限流不在学习版范围 |
| 6 | 容器 `/tmp` 使用 `tmpfs: /tmp:exec`，放松了默认 noexec | sqlite-jdbc 需解压原生库并 `System.load`（`docker-compose.yml:23-26` 注释） | 根文件系统只读、非 root、`/tmp` 不持久化、无外部写入面 |
| 7 | 诊断日志中令牌一律脱敏，与 REQ-SET-011「诊断开启可见令牌」原文存在偏差 | M3 Gate D4（用户批准） | 比需求更严格；仍保留请求 / 响应体输出用于排障；日志不落盘、不上传 |
| 8 | iOS 日志 sink 使用 `NSLog` 而非显式 `os_log` | M3 Gate D9 | 仅写系统日志，不落盘、不上传；后续可替换为 os_log 绑定 |
| 9 | release 构建默认服务器地址硬编码学习服务器公网 IP（`composeApp/build.gradle.kts:14`），与 AGENTS.md §7「不硬编码服务器 IP」存在偏差 | T-REL-05 交付说明与验收矩阵已记录；release 产物需开箱即连 | 公网 IP 非机密；地址可在设置页修改；后续可改为构建参数 / 本地属性注入 |
| 10 | `docker-compose.yml` 的 `MAX_CLIP_BYTES` / `MAX_REQUEST_BYTES` 环境变量未被服务端读取，实际阈值来自代码常量 | 非安全问题，属配置与实现的文档偏差 | 实际生效值与 compose 声明一致（65536 / 524288）：`RequestLimits.kt:30`、`ClipService.kt:259`；后续可让 `AppConfig` 读取或从 compose 移除 |
| 11 | 设计稿演示数据含 SSH 命令（`root@10.0.8.24 -p 2202` 与密钥文件名） | `design/Clipbox 三端设计.html:1208`；不可路由私网地址 | 建议后续泛化为占位符；修改设计基准不属于本卡范围 |

## 7. 验收输出

```bash
$ git grep -nE 'ADMIN_TOKEN=[^<]|Bearer [A-Za-z0-9_-]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY' -- . ':!docs/tasks/security-review.md' || true
ClipboxClient/shared/src/commonTest/kotlin/com/wangxingxing/clipbox/logging/LoggingRedactorTest.kt:18:        val redacted = LogRedactor.redact("authorization=Bearer sk-live-abcdef123456")
ClipboxServer/README.md:15:ADMIN_TOKEN=$(printf 'a%.0s' {1..32}) \
"docs/Clipbox \351\241\271\347\233\256\344\272\244\346\216\245\346\226\207\346\241\243.md":304:      - ADMIN_TOKEN=${ADMIN_TOKEN}
docs/tasks/01-server.md:40:ADMIN_TOKEN=$(printf 'a%.0s' {1..32}) PUBLIC_BASE_URL=http://203.0.113.10:8080 ./gradlew run &
docs/tasks/05-release.md:327:git grep -nE 'ADMIN_TOKEN=[^<]|Bearer [A-Za-z0-9_-]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY' -- . ':!docs/tasks/security-review.md' || true
"docs/\346\234\215\345\212\241\347\253\257\346\212\200\346\234\257\346\236\266\346\236\204.md":819:      - ADMIN_TOKEN=${ADMIN_TOKEN}
```

输出逐行定性：`LoggingRedactorTest.kt:18` 是脱敏单测的伪造值 `sk-live-…`；`ClipboxServer/README.md:15` 与 `docs/tasks/01-server.md:40` 是 `printf 'a'` 生成的本地示意令牌；交接文档 / 服务端架构是 `${ADMIN_TOKEN}` 变量引用；`05-release.md:327` 是本任务卡自身的命令文本。**无真实密钥、私钥或生产令牌。**

```bash
$ cd ClipboxServer && ./gradlew cleanTest test --no-build-cache
> Task :test

BUILD SUCCESSFUL in 5s
6 actionable tasks: 2 executed, 4 up-to-date
# build/test-results/test/*.xml 汇总：tests=126 failures=0 errors=0 skipped=0 suites=21
```

```bash
$ cd ClipboxClient && ./gradlew :shared:allTests --rerun-tasks
> Task :shared:testDebugUnitTest
> Task :shared:testReleaseUnitTest
> Task :shared:allTests

BUILD SUCCESSFUL in 35s
51 actionable tasks: 51 executed
# shared/build/test-results 汇总：
#   desktopTest             tests=217 failures=0 errors=0 skipped=0 suites=34
#   iosSimulatorArm64Test   tests=216 failures=0 errors=0 skipped=0 suites=33
#   testDebugUnitTest       tests=156 failures=0 errors=0 skipped=0 suites=24
#   testReleaseUnitTest     tests=156 failures=0 errors=0 skipped=0 suites=24
#   合计 tests=745 failures=0 errors=0 skipped=0；iosX64Test 在 arm64 宿主按 Gradle 配置 SKIPPED
```

```bash
$ cd ClipboxClient && ./gradlew :composeApp:assembleDebug :composeApp:compileKotlinDesktop :composeApp:compileKotlinIosSimulatorArm64
> Task :composeApp:packageDebug
> Task :composeApp:assembleDebug

BUILD SUCCESSFUL in 10s
82 actionable tasks: 21 executed, 61 up-to-date
# compileKotlinDesktop、compileKotlinIosSimulatorArm64 包含在同一构建中并通过
```

## 8. 出口结论

- 必须修复项 F1、F2、F3 已全部闭环；F4 按用户 2026-11-18 决策记录为接受风险，未修改仓库与 git 历史。
- 无未处置的高危项；所有「接受风险」均与 ADR-0003 学习版边界或已批准的 M3 Gate 偏差一致。
- 可以进入 T-REL-08；F4 已由用户于 2026-11-18 复核并维持现状，风险随官网公开分发扩大，已在 [ADR-0005](../adr/0005-v1-multiplatform-release.md) 登记。
