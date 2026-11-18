# M3 本地端到端验收记录（T-UI-20）

- 日期：2026-11-17
- 范围：Android + Desktop + 真实本地服务端的 M1–M3 闭环；iOS 场景留给 M4
- 截图目录：`~/Documents/ClipboxM3/`（仓库外，99 个文件；不提交二进制）
- 临时输出：`/tmp/clipbox-m3-server*.log`、`/tmp/e2e-*`、`~/Library/Application Support/Theca/`

## 1. 环境

| 端 | 环境 |
| --- | --- |
| 服务端 | `ClipboxServer`，`:8080`，SQLite WAL，数据目录 `ClipboxServer/data/`；`ADMIN_TOKEN` 使用测试值，`PUBLIC_BASE_URL=http://localhost:8080` |
| 隔离服务端 | `PORT=18080 DATA_DIR=/tmp/clipbox-ac11`，仅用于 AC-11 保留/墓碑场景，不接触主数据 |
| Android | `emulator-5554`（Medium_Phone_API_37，1080×2400），debug APK，`adb reverse tcp:8080 tcp:8080` |
| Desktop | macOS arm64，`./gradlew :composeApp:run`，数据目录 `~/Library/Application Support/Theca` |
| 第三设备 | Node 24 内置 WebSocket + 外部设备令牌（`/tmp/v19-device.json`，不提交），用于观察广播与写入 |

主服务端在验收中重启一次（保留 `data/`），用于 AC-09；Android 在验收中解除配对并重新配对一次，用于 AC-14。

## 2. 场景结果

| 编号 | 场景 | 操作与证据 | 结果 |
| --- | --- | --- | --- |
| AC-01 | 手机 → 桌面 | Android 发送 `e2e-broadcast-3dev`：Node WS 在 +3ms 收到 `clip.created`；Desktop 实时库 `server_seq=38`、`lastSeq=38`；`pbpaste` 命中正文；端到端 ≤5s。截图 `e2e-ac01-android-sent.png` | 通过 |
| AC-02 | 桌面 → 手机 | Desktop 开启自动发送后 `pbcopy ac02-desktop-to-phone-1729`：服务端 `seq=48`、来源设备为 Desktop；Android 前台数秒内出现该记录；点击记录提示「已复制到剪贴板」，点击「粘贴」后输入框内容一致。截图 `e2e-ac02-desktop-to-phone.png`、`e2e-ac02-copied-pasted.png` | 通过 |
| AC-03 | 离线补发 | 断开 `adb reverse` 后发送 3 条，状态为待同步；恢复后 3 条各补发一次，服务端无重复，`outbox=0`，序号推进到 26。截图 `e2e-ac03-offline-1.png`、`e2e-ac03-offline-3.png`、`e2e-ac03-replayed.png` | 通过 |
| AC-04 | WS 断线补拉 | `adb reverse --remove` + force-stop 2 分钟，期间写入 5 条（seq 32–36）；冷启动后 REST `GET /api/v1/clips` 拉取，按 32→36 顺序落库，`lastSeq=36`，无重复。截图 `e2e-ac04-catchup.png`、`e2e-ac04-coldstart.png` | 通过 |
| AC-05 | 超限拒绝 | 服务端：70,000 字节正文 `POST /api/v1/clips` → `400 VALIDATION_ERROR`；客户端 120 KiB 手工注入按用户指示跳过，边界由 `TextSizeTest`（65,536 字节）覆盖。见 §4-2 | 部分通过（客户端 UI 未手工复现） |
| AC-06 | 分享页 XSS | 分享 `<script>alert(1)</script>`：响应头 `CSP default-src 'none'`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Cache-Control: no-store`；`<`/`>`/`&`/`"`/`'` 全部 HTML 转义，无外部资源、无 JS。证据 `/tmp/e2e-share-headers.txt`、`/tmp/e2e-share-body.html` | 通过 |
| AC-07 | 一次性与过期 | 一次性链接第一次 200，第二次 `410 SHARE_CONSUMED`（链接已被使用）；`ttl=60s` 链接等待 75s 后 `410 SHARE_EXPIRED`（链接已过期）；错误页不回显正文。证据 `/tmp/e2e-*` 与服务端访问日志 | 通过 |
| AC-08 | 工具箱离线 | 飞行模式下依次使用 JSON、Base64、URL、时间戳、正则 5 个工具，全部可计算、可复制，不写历史、不发起网络请求。截图 `e2e-ac08-json-offline.png`、`e2e-ac08-base64-offline.png`、`e2e-ac08-url-offline.png`、`e2e-ac08-timestamp-offline.png`、`e2e-ac08-regex-offline.png` | 通过 |
| AC-09 | 重启持久化 | 停止并重启服务端（保留 `data/`）：`healthz` 恢复，`clips=43`、`server_seq=48`；Android 冷启动显示「已配对/已同步」，历史与置顶保留，新增记录继续使用 seq 49/50。截图 `e2e-ac09-server-restart.png` | 通过 |
| AC-10 | 剪贴板回环 | 同时开启自动粘贴与自动发送：Android 发送后 Desktop 只写入一次剪贴板，6s 内服务端无 Desktop 回传记录、`outbox=0`；2s 窗口内重复监听被跳过，窗口外同内容可再次上传（seq 45/46）。截图与数据库见 `e2e-loopback-*`、`/tmp/e2e-desktop*.log` | 通过 |
| AC-11 | 序号与墓碑 | 隔离实例：501 条活跃非置顶 → 清理软删 1 条（新 seq 505）、活跃 500；清理 2 个旧墓碑后 `sync_floor=504`、`minAvailableSeq=505`；`since=1`/`since=999999` 均返回 `resyncRequired=true`；`since=0` 首页 200 条（seq 2–201）、`hasMore=true`。**下一页 `since=201` 因 `<504` 被判定重新同步，全量分页无法完成**。证据 `/tmp/e2e-ac11-p2.json`、`/tmp/e2e-ac11-p3.json`、`/tmp/e2e-ac11-server*.log`；冲突见 §4-1 | 已修复（保留/墓碑语义实测通过；全量分页冲突由 BUG-001 修复，自动化集成测试验证，见 §4-1） |
| AC-12 | 资源占用 | 未执行 24 小时稳定运行；本机 M3 验收窗口不足，留待发布前稳定性验证。见 §4-3 | 未执行 |
| AC-13 | 安全基线 | 设备令牌：`SHA-256(token)` 前 12 位 `d3faeda08d84` 与 `devices.token_hash` 一致；分享 token：`SHA-256` 等于 `shares.token_hash`（`094010ea25f6…`）；服务端日志敏感模式命中数 0，分享访问只记录 `pathTemplate=/s/{token}`；配对接口同一 IP 第 6 次请求返回 `429 RATE_LIMITED`。证据 `/tmp/pair-6.json`、`/tmp/clipbox-m3-server.log` | 通过 |
| AC-14 | 解除配对 | Android 确认解除配对：回到配对页，本地 `clips=0`、`outbox=0`、`meta` 清空；服务端 `clips=41`、`devices=12` 保持不变；Desktop 仍在线并在解绑后收到新记录（seq 47）；重新配对后拉回服务端历史（含该记录）。截图 `e2e-ac14-unpaired.png`、`e2e-ac14-repaired.png` | 通过 |
| AC-15 | 多设备广播 | 三台在线：Android 发送后 Node WS +3ms、Desktop 实时库同步（seq 38）；Android 置顶 `ac02-desktop-to-phone-1729` → 服务端 `pinned=1, seq=49`，Desktop 库同步；Android 删除 `ac14-desktop-still-linked` → 服务端墓碑 `seq=50`，Desktop 与 Android 均移除。截图 `e2e-ac15-pin.png`、`e2e-ac15-delete.png` | 通过 |
| AC-16 | iOS 分享扩展 | 留待 M4 T-IOS-09 | 未执行（范围外） |
| AC-17 | iOS 前台同步与粘贴权限 | 留待 M4 T-IOS-09 | 未执行（范围外） |
| AC-18 | 日志分级 | Android debug 默认诊断开、release 默认关；`LoggingPolicyTest` 覆盖关闭后 sink 静默；`LoggingRedactorTest` 覆盖诊断模式仍脱敏令牌/分享 token；主服务端与 Desktop 日志检索敏感模式 0 命中。按决策 #3，debug 诊断也不输出明文令牌，与 AC-18 原文有偏差。见 §4-4 | 部分通过（按决策收紧） |
| AC-19 | 多语言切换 | Android 简体 → English 立即生效；强制停止后冷启动仍为 English，随后恢复简体；三语截图见 `visual-checklist.md` 与 `e2e-ac19-20-en-dark.png`、`e2e-ac19-20-restart.png`。iOS 三语留待 M4 | Android 通过；三端完整验收留待 M4 |
| AC-20 | 主题切换 | Android 深色 + English 同时生效，冷启动保持深色；恢复「跟随系统」后跟随系统浅色；浅色/深色/跟随系统与 Desktop 截图见 `visual-checklist.md`。iOS 留待 M4 | Android 通过；三端完整验收留待 M4 |

## 3. 回归命令与结果

| 命令 | 结果 |
| --- | --- |
| `cd ClipboxServer && ./gradlew test --rerun` | BUILD SUCCESSFUL；21 个测试类 / 123 个用例，0 失败、0 错误、0 跳过 |
| `cd ClipboxClient && ./gradlew :shared:allTests` | BUILD SUCCESSFUL；115 个测试类 / 739 个用例，0 失败、0 错误、0 跳过（含 Android/JVM/iOS 模拟器目标） |
| `cd ClipboxClient && ./gradlew :composeApp:assembleDebug` | BUILD SUCCESSFUL；`composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| `cd ClipboxClient && ./gradlew :composeApp:run` | Desktop 启动、配对、收发、自动发送/自动粘贴验证通过（见 AC-01/02/10） |

## 4. 失败、冲突与限制

### 4-1 AC-11：全量分页与 `syncFloor` 冲突（已修复，BUG-001 方案 A）

**现象**：`sync_floor=504` 时，`GET /api/v1/clips?since=0&limit=200` 返回 200 条（seq 2–201）、`hasMore=true`、`nextSince=201`；继续请求 `since=201` 时，因为 `201 < sync_floor` 返回 `resyncRequired=true` 与空 `items`，无法按 `api.md`「`since=0` … 客户端按页拉取直到 `hasMore=false`」完成首次全量同步。

**代码位置**：`ClipboxServer/src/main/kotlin/com/wangxingxing/clipbox/server/service/ClipService.kt` 的 `resyncRequired = (since > 0 && since < syncFloor) || since > serverMaxSeq`；客户端 `SyncEngine.resyncLocked()` 将全量分页中再次出现 `resyncRequired` 判为服务端违约。

**影响**：墓碑清理后，只要 `sync_floor` 高于最早活跃记录的序号，新设备/重同步客户端就无法完成全量拉取；在真实保留策略下，长期无新写入后再清理会出现。

**根因性质**：冻结文档同时要求「`since=0` 全量分页」与「`since < sync_floor` 必须重同步」，但未定义「如何区分全量分页续页与增量游标」。服务端无状态，无法从 `since>0` 判断请求是否属于全量分页。

**未采取的动作**：未修改 `api.md` 或实现来“通过”验收；未引入未文档化的全量会话状态。已在 `m3-gate.md` 登记为 D8，并建立 [BUG-001](bug-001-ac11-full-sync-floor.md)。

**修复（2026-11-18）**：经用户批准采用方案 A，`GET /api/v1/clips` 新增可选参数 `full`：`full=true` 跳过 `since < sync_floor` 判定，仍保留 `since > serverMaxSeq` 保护；客户端首次配对与强制重同步的整轮分页都携带 `full=true`，增量请求省略。契约、服务端/客户端架构、功能需求与术语表已同步修订。自动化证据：`SyncFlowIntegrationTest.full sync completes after retention purge with non zero sync floor` 用隔离 SQLite 复现「501 条 + 软删 + 墓碑清理 + 多页全量拉取」，服务端与客户端全量测试均绿（见 `bug-001-ac11-full-sync-floor.md` 修复记录）。本轮未重新执行隔离实例手工复跑，D8 依据自动化集成测试关闭。

### 4-2 AC-05：客户端 120 KiB 手工注入跳过

按用户指示，未继续向 Android 输入框注入约 120 KiB 文本；服务端 70 KiB 拒绝已实测，客户端字节上限由 `TextSizeTest` 覆盖 65,536 字节边界。UI 超限提示未在本轮手工截图，登记为已知缺口。

### 4-3 AC-12：24 小时稳定性未执行

本轮为开发机短时验收，未运行 24 小时；该场景不属于 T-UI-20 的本地手测矩阵，留待发布前执行，M3 Gate 不据此判定通过。

### 4-4 AC-18：诊断日志口径偏差

按已批准决策 #3，诊断日志对设备令牌、分享 token、配对码一律脱敏，因此 AC-18 原文「debug 默认可见令牌」不成立；关闭诊断后无敏感字段日志由单测覆盖。该偏差已在 Gate 登记。

### 4-5 Desktop UI 自动化限制

Desktop 应用窗口在验收期间无法稳定获得前台焦点，未做窗口截图式操作；发送路径通过 `pbcopy` 触发自动发送、`pbpaste` 校验剪贴板、SQLite/WAL 与服务端日志取证，均为真实进程行为，非 mock。

### 4-6 AC-11 隔离实例不触碰主数据

隔离实例使用 `/tmp/clipbox-ac11` 与 `:18080`，主服务端 `data/` 未被用于制造 501 条记录。

## 5. 结论

- Android + Desktop + 真实服务端的核心闭环（配对、双向收发、离线补发、WS 补拉、置顶/删除、分享安全、回环、解绑/重配、主题/语言重启保持）均已实测通过。
- AC-11 的保留/墓碑语义通过；「全量分页 + 墓碑清理」冲突已由 BUG-001 方案 A 修复，并以自动化集成测试验证，D8 关闭。
- AC-05 客户端 UI 手工项与 AC-12 24 小时稳定性未执行，已在 Gate 中登记。
