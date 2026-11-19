# AGENTS.md

本文件是所有实现 Agent 的执行协议。任务卡在 `docs/tasks/`；开始任何工作前必须先读本文件，再读对应任务卡。

## 1. 项目与当前状态

- 产品：Clipbox，离线优先的跨设备文本中转 + 本地开发者工具箱。
- 三端：Android（minSdk 26）、iOS 16+、Desktop（Windows / macOS / Linux）；v1.0.0 桌面产物为 macOS arm64 DMG、Windows x64 MSI、Linux x86_64 DEB，均未签名（`docs/adr/0005-v1-multiplatform-release.md`）。
- 服务端：Kotlin + Ktor + Exposed + SQLite WAL，单实例。
- 当前状态：v1.0.0 已发布；v1.1.0 开发中，补齐 Windows / Linux 发布产物、官网下载矩阵与三端检查更新。
- 包名：客户端 `com.wangxingxing.clipbox`；服务端 `com.wangxingxing.clipbox.server`。

## 2. 权威文档

按以下顺序阅读；冲突时优先级为：接口 > 行为 > 架构 > 任务卡。

1. `docs/tasks/README.md`：里程碑、依赖、派单顺序。
2. 当前任务卡。
3. `docs/api.md`：客户端与服务端之间唯一接口契约。
4. `docs/功能需求文档.md`：产品行为与 Given/When/Then 验收。
5. `docs/客户端技术架构.md` / `docs/服务端技术架构.md`：实现细节。
6. `docs/GLOSSARY.md`：术语。
7. `docs/adr/0001…0010`：已接受决策。
8. `design/README.md`、`design/brand-spec.md`、`design/Clipbox 三端设计.html`：视觉与交互基准。

发现文档之间不一致：**停下汇报**，不要自行选择或修改冻结文档。

## 3. 硬不变量

违反以下任一条即视为实现错误，即使功能“看起来能用”。

### 同步

1. 客户端本地 Room 数据库是唯一数据源；UI 只观察本地数据，不直接展示网络响应。
2. `serverSeq` 是同步唯一权威顺序；`createdAt` 只用于界面排序。
3. `lastSeq` 只能由 REST 拉取的 `nextSince` 或**连续**的 WebSocket 事件（`seq == lastSeq + 1`）推进。
4. 任何写接口响应（POST / PATCH / DELETE）都**不得**推进 `lastSeq`。
5. 全量重同步不得清空 Outbox、未同步记录或失败记录。
6. Outbox 重放、REST 拉取、WS 事件应用共享同一互斥锁，不得并发改本地库。
7. 客户端本地裁剪只影响展示：最近 200 条非置顶 + 全部置顶；不向服务端传播删除。

### 限额与安全

8. 单条正文 UTF-8 ≤65536 字节；请求体 ≤512 KiB。字节数不是字符数。
9. 设备令牌与分享令牌为 32 字节随机数的 base64url 无填充；服务端只存 SHA-256 哈希；令牌不进入 URL、日志、崩溃信息。
10. 默认日志不记录正文、令牌、配对码、分享 token；诊断日志仅在用户开启时输出到本机系统日志，不上传、不落盘。
11. 分享页必须严格 HTML 转义，带 CSP、`nosniff`、`no-referrer`、`no-store`，不加载外部资源、不执行 JS。
12. 不集成第三方日志、崩溃、行为分析 SDK；不新增应用权限。唯一例外：Android `REQUEST_INSTALL_PACKAGES`，仅用于用户主动触发的安装包安装（[ADR-0010](docs/adr/0010-app-update-and-install.md)）。

### 部署基线

13. v1.0 学习版为 HTTP / WS + IP 直连：`http://<SERVER_IP>:8080`。**不加入 Caddy、TLS、HSTS、域名或强制 HTTPS。**
14. 客户端允许 `http://`，但配对页与设置页必须展示「未加密连接，仅用于学习」。
15. 部署仅 server 容器，发布 `8080:8080`；Android 用 `networkSecurityConfig` 允许明文，iOS 用 ATS 例外；两者都只为本学习版服务。

### 产品范围

16. v1.1.0 不包含：后台推送 / 通知、移动端后台剪贴板、片段收藏夹、托盘 / 全局快捷键、端到端加密、透明复制页。例外：Android DownloadManager 的系统下载通知由系统下载器发布，应用不申请通知权限；应用自建通知、前台服务、后台轮询仍禁止。
17. 不新增文档未定义的功能、设置项、接口字段或页面。
18. 三端共用固定色板，不做动态取色；UI 文案必须进入 Compose Resources，支持简中（默认）/ 繁中 / English。

### 工程

19. 不升级已锁定依赖；确需新增依赖时先停下说明理由并等待确认。
20. 不做 PR / 常驻 CI，不写全量 UI 自动化；仅允许由版本 tag 或手动触发的 release-only GitHub Actions（`docs/adr/0005-v1-multiplatform-release.md`）；验收仍靠任务卡命令 + 手工截图。
21. 一卡一提交；不 amend 已推送提交；不 push、不打 tag，除非用户明确要求。

### 更新与安装

22. 更新检查只访问官方发行服务器（`PlatformServices.defaultServerUrl`）的公开接口，不读取配对服务器地址，不带设备令牌，不复用带鉴权的 `ApiHttpClient`。
23. 安装包必须来自官方下载区；下载完成后校验 SHA-256，不匹配立即删除并提示失败。
24. 不静默安装、不自动重启；只有用户明确点击「安装」后才拉起系统安装器，未知来源授权由用户完成。
25. 更新说明、下载 URL 与文件路径不写入日志；自动检查失败静默，不阻塞启动。

## 4. 工程路径与命令

| 工程 | 路径 |
| --- | --- |
| 客户端 | `ClipboxClient/` |
| 服务端 | `ClipboxServer/` |
| 官网 | `ClipboxWeb/` |
| 文档 | `docs/` |
| 设计 | `design/` |

命令（工程初始化后生效；若实际任务名不同，以 T-S-01 / T-CORE-01 的记录为准）：

```bash
# 服务端
cd ClipboxServer
./gradlew test
./gradlew run

# 客户端
cd ClipboxClient
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
./gradlew :composeApp:assembleRelease
./gradlew :composeApp:packageDmg
./gradlew :composeApp:packageMsi   # 仅 Windows
./gradlew :composeApp:packageDeb   # 仅 Linux
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# 官网
cd ClipboxWeb
npm ci
npm run dev
npm run build
npm run preview

# Android 本地联调
adb reverse tcp:8080 tcp:8080
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.wangxingxing.clipbox/.MainActivity
```

本地服务端地址：`http://localhost:8080`（Desktop）；Android 经 `adb reverse` 后同样使用 `http://localhost:8080`；iOS 模拟器使用 `http://localhost:8080`。

## 5. 执行协议

1. 只执行当前指定的任务卡；不跨卡、不提前做下一卡。
2. 先读本文件 + 任务卡 + 必读章节；梳理代码流程优先 codegraph（见 §9）；输出 3–5 条实施计划。
3. 只改任务卡「涉及路径」；跨路径改动必须说明原因。
4. 按「实现要点」实现；不做额外功能、不重构无关代码；调试与测试优先使用 §9 规定的工具。
5. 逐条执行「验收」命令，保留真实输出；失败先定位根因，不跳过、不伪造。
6. 按统一 DoD 自检；更新任务卡要求产出的记录文件（如 `e2e-local.md`）。
7. 提交：`T-XX: <一句话>`；只 add 本卡文件；不 push。
8. 汇报：改动文件、验收结果、提交哈希、风险 / 偏差。

验收失败最多修复两轮；两轮仍失败，停下汇报，不继续猜测。

## 6. 汇报模板

```text
任务：T-XX <标题>
状态：完成 / 阻塞

改动：
- <文件>: <一句话>

验收：
- `<命令>` → <结果>
- <截图 / 测试报告路径>

提交：<hash> <message>
偏差 / 风险：<无则写无>
下一卡前置是否满足：是 / 否（原因）
```

## 7. 禁止事项

- 禁止修改已冻结的需求、接口、架构文档来让实现「通过」。
- 禁止跳过验收、伪造输出、删除失败测试、放宽校验。
- 禁止提交 `.env`、数据库、备份、令牌、私钥、APK / DMG / MSI / DEB 等产物。
- 禁止在代码中硬编码服务器 IP、令牌、示例域名。
- 禁止在 UI 中硬编码用户可见文案。
- 禁止在 Android / iOS 上实现后台剪贴板或轮询。
- 禁止引入 Caddy / TLS / 域名配置。
- 禁止执行不可逆的远程操作（删除数据、重建服务器、修改防火墙）未经用户确认。

## 8. 语言与代码风格

- 与用户沟通、任务汇报、文档使用中文；代码标识符用英文。
- 用户可见文案全部进入 `composeResources`；错误码到文案的映射集中管理。
- 日期、时区、字节数、哈希等工具集中在 `shared/util` 或对应共享模块，不在 UI 重复实现。

### 文件头

`ClipboxClient/` 与 `ClipboxServer/` 的新建 Kotlin 源文件统一使用以下文件头：

```kotlin
/**
 * author : 王星星
 * date : yyyy-MM-dd HH:mm
 * email : 1099420259@qq.com
 * description : 文件职责、边界与需要特别注意的约束
 */
```

- `date` 写入文件创建时的本地日期与 24 小时制时间，编辑或重命名不更新；新建文件使用当时时间。
- `description` 使用中文，说明该文件的职责与边界；只有存在非显而易见的约束时才补充「注意：…」，不得只重复类名或包名。
- 本次变更触碰到的 Kotlin 文件若缺少文件头，应在同一次变更中补齐；未触碰的文件不追溯补头。

### 注释

- 注释统一使用中文，只写「为什么」和非显而易见的约束，不复述代码。
- 必须注释：业务规则与状态流转前提、协程取消与重试等易错约束、跨层隐式约定、SQL 口径、迁移与安全限制。
- 公共 API 的契约（参数含义、返回值、异常）需要说明时使用 KDoc；其余使用 `//` 放在被说明代码的上方。
- 禁止逐行注释、禁止只复述代码行为的注释、禁止新写英文行内注释、不写任务引用与多段注释；注释随代码同步修改，过期注释按缺陷处理。

## 9. 工具优先级

以下偏好只改变执行方式，不改变 §3 硬不变量与 §7 禁止事项；任务卡「验收」列出的命令仍须逐条执行并保留输出。

1. **Android 调试与测试**：模拟器 / 真机 / 安装 / 启动 / 截图 / 日志 / 界面操作优先使用 `android` CLI 及官方 Android Skills（`android-cli`、`android-profiler` 等），而不是手写等效 adb 命令；本机未安装时按 `android-cli` Skill 的说明安装。任务卡要求的 `adb reverse` 等命令照常执行。
2. **代码流程梳理**：理解调用链、数据流、模块依赖时优先用 codegraph（MCP `codegraph_explore` 或 `codegraph` CLI）；工程无索引时先在对应工程根目录执行 `codegraph init`，再检索；索引不可用时才回退到文本搜索。
3. **测试建设**：设计测试策略、搭建测试工程、补齐测试用例时先调用 `testing-setup` Skill。其建议若涉及新增测试依赖，按 §3 工程第 19 条先停下确认；不得因此引入 PR / 常驻 CI 或全量 UI 自动化（第 20 条），release-only CI 例外见第 20 条。
