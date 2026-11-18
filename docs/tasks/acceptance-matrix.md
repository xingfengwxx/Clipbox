# AC-01…AC-20 验收矩阵

| 字段 | 值 |
| --- | --- |
| 任务卡 | T-REL-06 |
| 日期 | 2026-11-18 |
| 执行 | 王星星 |
| 基线提交 | `2d22580`（T-REL-05 收尾） |
| 依据 | `docs/功能需求文档.md` §5（AC 编号、场景、步骤、判定标准逐字抄录，未改写） |
| 证据根目录 | `/Users/wangxingxing/Documents/ClipboxREL06/`（仓库外，截图/日志/脚本不提交） |
| 历史证据 | `docs/tasks/m2-gate.md`、`m3-gate.md`、`m4-gate.md`、`e2e-local.md`、`e2e-ios.md` |

说明：本轮为 v1.0.0 发布前总验收，端点全部直连本机或远程学习版实例（HTTP / WS + IP，无 TLS，符合 ADR-0003）。每条 AC 均给出可复查的证据路径；「已知限制」条目均注明用户确认口径与替代证据。

## 1. 验证环境

| 代号 | 说明 |
| --- | --- |
| L | 本地主验收实例：`/tmp/clipbox-rel06-main`（从 `ClipboxServer/data/clipbox.db` 用 `sqlite3 .backup` 复制，`PORT=8080`，`PUBLIC_BASE_URL=http://localhost:8080`，独立 `ADMIN_TOKEN` 仅存本地） |
| I | AC-11 隔离实例：`/tmp/clipbox-rel06-ac11`（`PORT=18080`，空库，可执行 jar，不接触主数据） |
| R | 远程部署：`<SERVER_IP>:8080`，容器 `clipbox-clipbox-1`（限额 512 MiB），本卡只做只读检查与采样 |
| Android | `emulator-5554`，Medium_Phone_API_37（sdk_gphone64_arm64），Clipbox 1.0.0 debug；APK SHA-256 `bd4896298275f01ba795570b527075af44d0c68551732c42ca6c9e979c948d0b`（T-REL-05） |
| iOS 手机 | iPhone 16 模拟器，UDID `0278FBBD-82F0-4092-AF60-B6B44C8228A9`，iOS 27.2 |
| iOS 平板 | iPad (A16) 模拟器，UDID `301101B4-D695-45D1-ABCB-782FF20DDCCB`，iOS 27.2 |
| Desktop | macOS 本机 Theca 1.0.0：主环境为 `:composeApp:run` 调试实例；release 产物（DMG 解包 `Theca-release.app`）仅用于 AC-18 日志取证；数据目录 `~/Library/Application Support/Theca` |
| 浏览器 | 分享页用桌面 Chrome（macOS）与 AVD Chrome（Android）各一次（AC-06） |

## 2. 验收矩阵

| 编号 | 场景 | 步骤（原文） | 判定标准（原文） | 环境 | 本轮操作与结果 | 证据 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AC-01 | 配对与手机到桌面闭环 | 生成配对码 → 设备配对 → 手机分享文本 | 桌面 ≤5s 出现在系统剪贴板，可直接粘贴 | L + Android + Desktop | Android 重新配对（见 AC-14）后发送 `ac01-android-1794979168`；服务端落库 seq=60（createdAt 13:19:29.735）；Desktop 剪贴板截图 13:19:30 已含该内容（距落库 ≤1s），`pbpaste` 直接命中 | `AC-01/01-android-typed.png`、`02-android-sent.png`、`03-desktop-clipboard.png`、`desktop-current.png`、`sent-body.txt` | 通过 |
| AC-02 | 桌面到手机 | 桌面发送文本 → 手机打开 App | ≤3s 可见，一键复制成功 | L + Desktop + Android | Desktop `pbcopy ac02-desktop-to-phone-1794979405`；T0+1.047s 截图、T0+2.562s 截图均见新记录与正文，UI dump（T0+2.749s）含完整正文，判定 2.6s ≤3s；点击条目复制 toast 成功，粘贴入输入框内容一致（32/65,536 B）；服务端 seq=63 仅 1 行 | `AC-02/result.txt`、`shot-1s.png`、`shot-2.5s.png`、`01-copy-tap.png`、`02-paste-verify.png`、`05-row-tap.png`、`06-paste-body.png` | 通过 |
| AC-03 | 离线补发 | 断网发送 3 条 → 恢复网络 | 自动补发，服务端无重复，本地状态变为已同步 | L + Android | 飞行模式 + 无 adb reverse + force-stop 冷启动后离线发送 `ac03d-offline-a/b/c`：本地 outbox=3 全部 PENDING_CREATE、服务端 0 行；恢复网络后 20s 内自动补发完毕，服务端 seq 73/74/75 各 1 条无重复，outbox=0、三条 SYNCED | `AC-03/result.txt`、`00-offline.png`、`05-app-offline-start.png`、`09-offline-queue-3.png`、`10-synced.png` | 通过 |
| AC-04 | WebSocket 断线重连 | 断网 2 分钟并产生 5 条变更 → 恢复 | 自动重连并补拉，5 条完整，顺序正确，无重复 | L + Android | 断网 2 分 10 秒（含服务端重启切断 WS），期间用临时设备写入 5 条（seq 76–80）；恢复后 15s 内自动补拉，客户端 5 行 serverSeq 76–80 顺序正确、重复 0、全部 SYNCED | `AC-04/result.txt`、`00-app-offline.png`、`01-synced.png`、`create-results.json` | 通过 |
| AC-05 | 超限拒绝 | 发送 70 KiB 文本 | 客户端提示超限；直接请求服务端返回 400 | L + Android + 宿主注入 | Chrome 经 `adb reverse` 复制 71,680 B 后粘贴：计数器 71,680/65,536 B 未截断，提示「超出 64 KiB 上限」，可改为复制；发送被禁用（点击后服务端计数 75\|87 不变、输入框不清空、本地 0 条 71680 B 记录）；`curl` 71,680 B 请求 → HTTP 400 `VALIDATION_ERROR`「content 超过 65536 个 UTF-8 字节」 | `AC-05/result.txt`、`02-copied.png`、`03-over-limit.png`、`04-copy-instead.png`、`server-70kib.txt` | 通过 |
| AC-06 | 分享页 XSS | 分享 `<script>alert(1)</script>` | 页面原样展示文本，无脚本执行 | L + 桌面 Chrome + AVD Chrome | 载荷 `<script>alert(1)</script><img src=x onerror=alert(2)> & "quotes" 'single'`（clip seq=88）创建分享页；HTML 全转义（`&lt;script&gt;…`）、script-tags=0、外部资源 0、无脚本执行；响应头四件套齐全（CSP `default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`、`nosniff`、`no-referrer`、`no-store`）；桌面与移动浏览器各渲染一次 | `AC-06/result.txt`、`share-page-with-headers.txt`、`desktop-share-page.png`、`android-share-page.png` | 通过 |
| AC-07 | 分享一次性与过期 | 创建阅后即焚链接并打开两次；创建 1 小时链接并模拟过期 | 第二次 410「已被使用」；过期 410「已过期」 | L + curl | 阅后即焚：第一次 200 回显正文 1 处，第二次 410「已被使用」正文 0 处；TTL 3600s 链接（13:18:04 创建）在 14:18:14 打开 → 410「已过期」正文 0 处；两类错误页均带四安全头 | `AC-07/result.txt`、`headers-1.txt`、`headers-2.txt`、`page-1.html`、`page-2.html`、`expired-headers.txt`、`expired-page.html` | 通过 |
| AC-08 | 工具箱离线 | 飞行模式下使用 5 个工具 | 全部可用，不产生历史记录，不发起网络请求 | L + Android | 飞行模式 + 移除 adb reverse 冷启动（状态「离线」）；JSON/Base64/URL/时间戳/正则 5 个工具全部输出正确（含截图）；离线期间服务端无业务请求、DB count=76/max_seq=88 不变，本地 clips=72 不变、outbox=0（工具结果不写历史）；恢复网络回到「已同步」 | `AC-08/result.txt`、`00-offline-history.png`、`01-json.png`、`02-base64.png`、`03-url.png`、`04-timestamp.png`、`05-regex.png` | 通过 |
| AC-09 | 重启持久化 | 重启服务端与三端客户端 | 服务端数据完整，客户端历史完整，序号连续 | L + 三端 | 服务端 14:00:29 停、14:00:32 起（healthz 200）；三端 14:00:27 全停后冷启动；重启前后 clips 76/76、活跃 72/墓碑 4 一致、devices 21、seq 1–88 保留、distinct 76 重复 0；三端本地库重启前后一致（Android 72/88/0 等），历史完整、序号连续 | `AC-09/result.txt`、`baseline.txt`、`after-restart.txt`、`restart.txt`、`shots/01-android-history.png`、`shots/02-ios-history.png`、`shots/03-ios-history-nokb.png`、`shots/04-desktop-history.png` | 通过 |
| AC-10 | 剪贴板回环 | 同时开启自动粘贴与自动发送，手机发送文本 | 桌面只写入一次剪贴板，不产生新的上传记录 | L + Android + Desktop | 双开关 ON（历史页截图）；Android 发送 `ac10-loop-1794979987`：NSPasteboard changeCount 42→43（只写一次）、pbpaste 命中；服务端 seq 80→81 仅 1 行且 source_device_id 为 Android，Desktop lastSeq=81、outbox=0，无回传 | `AC-10/result.txt`、`change-before.txt`（42）、`change-after.txt`（43）、`body.txt`、`desktop-history.png`、`android-synced.png` | 通过 |
| AC-11 | 序号与墓碑 | 制造 501 条非置顶记录并触发清理；构造游标落后 | 最旧记录被软删；墓碑 30 天后清理；落后客户端收到重同步提示；清理后从 `since=0` 起的全量分页（`full=true`）可拉完所有活跃记录 | I（隔离实例） | 501 条全部 201（seq 1–501）；重启清理 `softDeleted=1`（最旧 seq=1 软删并分配 seq=502）；墓碑 `deleted_at` 置 31 天前后重启 `tombstonePurged=1 syncFloor=502`（物理删除 + 同事务推进 sync_floor）；`since=1`（<502）返回 `resyncRequired=true`、`minAvailableSeq=503`；`since=0&full=true` 分页拉完全部活跃记录、无重复（BUG-001 回归用例） | `AC-11/result.txt`、`seed-console.txt`、`phase2-soft-delete.txt`、`phase3-tombstone-purge.txt`、`pagination-console.txt` | 通过 |
| AC-12 | 资源占用 | 稳定运行 24 小时 | 服务端内存 <400MB，健康检查正常 | R | 容器 11:23:03 启动，13:17:39–14:17:57 每 5 分钟采样 13 次：13/13 `/healthz` HTTP 200 `{"status":"ok","version":"1.0.0"}`、docker health=healthy、0 失败；内存 112.9–115.4 MiB（≈29% 需求上限）、CPU 0.09–0.12%；采样结束时连续运行 2h55m（期间经历全量读写）。「24 小时」登记为已知限制 LIM-01 | `AC-12/result.txt`、`remote-stats.log` | 已知限制（替代证据通过） |
| AC-13 | 安全基线 | 检查服务端数据库、日志、限流 | 服务端无明文令牌；日志无内容与 token；配对超限返回 429 | R + L + I | L/R `devices.token_hash`、`shares.token_hash` 全部 64 位小写 hex、异常 0，schema 无明文列；L 已知令牌 SHA-256 与库中 1 行比对命中（只存哈希）；L 日志 97 行 / R 日志 487 行 grep：bearer/token/authorization 0、正文标记 0、分享 token 0（配对码仅启动打印一次，为文档允许的例外）；L 干净窗口第 6 次配对请求 → 429 `RATE_LIMITED` + `Retry-After: 55` | `AC-13/result.txt`、`local-check.txt`、`remote-check.txt`、`rate-limit-threshold.txt` | 通过 |
| AC-14 | 解除配对 | 设备 A 解除配对 | A 本地清空并回到配对页；B 与其他设备不受影响；服务端数据保留 | L + Android + Desktop + iOS | Android 解除配对 → 本地清空并回到配对页（03）；Desktop/iOS 连接与会话不受影响；服务端 devices/clips 数据保留；Android 重新配对后发送 `ac14-desktop-still-linked-1794979124` 成功广播，Desktop 端可见 | `AC-14/01-current-android.png`–`06-repaired-synced.png`、`ac14-sent-body.txt` | 通过 |
| AC-15 | 多设备广播 | 三台设备在线，任一台发送 / 置顶 / 删除 | 其余设备 5s 内反映最终状态 | L + Android + Desktop + iOS | 三端在线（lastSeq=81）；发送 seq=82：Desktop/iOS 0.213s 可见；置顶 seq=83：iOS 0.352s、Android 1.504s；删除 seq=86：Desktop/iOS 0.232s、Android 1.087s；收尾三端 lastSeq=86 | `AC-15/result.txt`、`00-ios-launch.png`、Desktop/Android 组图（01–13）、`ui-android-before-delete.xml`、`ui-del2*.xml` | 通过 |
| AC-16 | iOS 分享扩展闭环 | 在 iOS 系统分享 text/plain → 选择 Clipbox 分享扩展 → 发送页确认发送 | 发送页预填正确，确认后同步成功；取消不产生记录，草稿已清理 | iOS iPhone 16 | 扩展注册核查：`pluginkit -m -p com.apple.share-services` 列出 `com.wangxingxing.clipbox.share(1.0.0)`；激活规则 `public.plain-text` 通过；App Group 草稿闭环：已配对预填（testShareDraftPrefillWhenPaired 通过）、未配对不预填（testUnpairForDraftScenario 通过）、配对后同一草稿预填（testPairAfterUnpairedShareDraft 通过）；草稿仅在确认发送时清理。系统分享面板内 UI 点击不可自动化 → 已知限制 LIM-03 | `AC-16/result.txt`、`draft-ios-share-draft-prefill.png`、`unpair2-ios-unpaired.png`、`repair2-ios-unpaired-draft-paired-prefill.png`、`/tmp/rel06-tios-draft.xcresult`、`/tmp/rel06-tios-unpair2.xcresult`、`/tmp/rel06-tios-repair2.xcresult` | 部分通过（已知限制 LIM-03） |
| AC-17 | iOS 前台同步与粘贴权限 | 其他设备发送文本 → iOS App 在后台 → 回到前台；拒绝粘贴权限后重试 | 后台不弹通知、不写剪贴板；回前台 3s 内可见；权限拒绝后可重试 | iOS iPhone 16 | 后台：SpringBoard 树无含正文的通知元素，剪贴板保持上一笔；回前台：服务端日志显示 Activate 后 0.31s 完成补拉 GET，前台恢复完成→测试首次确认可见 2.95s ≤3s（Activate 指令→首次确认 3.12s，含 XCTest 键入与轮询开销；用户确认按 2.95s 口径判通过）；粘贴权限弹窗不可自动化 → 已知限制 LIM-04 | `AC-17/result.txt`、`timeline.txt`、`pairing2-ios-foreground-catchup.png`、`pairing2-ios-copied-pasted.png`、`pairing2-ios-sent.png`、`pairing2-ios-received-ws.png`、`pairing2-ios-keyboard-avoidance.png`、`/tmp/rel06-tios-pairing2.xcresult` | 通过（已知限制 LIM-04） |
| AC-18 | 日志分级 | release 默认包与 debug 包各使用一天；debug 再关闭诊断日志 | release 零应用日志；debug 默认可见请求 / 响应体与令牌；关闭后无敏感字段日志 | L + Android debug + Desktop release | debug 诊断 ON：`NET POST /clips status=201 … authorization=<redacted> request={…content…} response={…serverSeq:89…}`（请求/响应体可见；令牌实现为始终脱敏，比原文更严格，见 LIM-06）；关闭诊断：15 行全为系统日志、应用诊断 0 条、正文/token 0（期间写入 seq 95）；Desktop release：301 行系统日志、应用业务日志 0 条、正文/token 0；「各使用一天」登记为 LIM-02 | `AC-18/result.txt`、`android-diagnostic-on.log`、`android-diagnostic-off.log`、`desktop-release.log`、`02-settings-diag-current.png`、`03-diag-toggled-off.png` | 通过（部分替代，已知限制 LIM-02） |
| AC-19 | 多语言切换 | 首次启动 → 依次切换简体中文 / 繁體中文 / English → 重启 | 默认简体中文；三端文案完整，无硬编码、截断或乱码；重启后保持所选语言 | Android + Desktop + iOS iPad | Android 繁中（设置/历史/重启保持）→ English（含重启）→ 简中；Desktop 简中/繁中/English 面板与重启保持、恢复简中；iOS iPad XCUITest 逐步断言「设置→外观与语言→English→繁體中文」即时生效，terminate+launch 后标题「字匣」；三端文案完整无截断乱码；默认简中（首次启动文案 + M2 记录） | `AC-19/result.txt`、Android 8 张、Desktop 5 张、`ipad-ipad-appearance-zh-hans.png`、`ipad-ipad-appearance-en.png`、`ipad-ipad-appearance-zh-hant-dark.png`、`ipad-ipad-portrait-home.png`、`/tmp/rel06-tios-ipad.xcresult` | 通过 |
| AC-20 | 主题切换 | 首次启动 → 依次切换浅色 / 深色 / 跟随系统 → 改变系统外观 → 重启 | 默认跟随系统；三端即时生效；跟随系统随系统变化；重启保持上次选择；无主题闪烁或不可读文本 | Android + Desktop + iOS iPad | Android：深/浅即时生效且重启保持，跟随系统随系统切换即时跟随、重启保持；iOS：深/浅切换、跟随系统选择、重启后保持，旋转布局正常；Desktop：深/浅即时生效，但「跟随系统」在运行中切换系统外观时不跟随（系统已浅色而应用保持深色），重启后按启动时系统外观显示 → 已知限制 LIM-05（用户确认）；三端无不可读文本 | `AC-20/result.txt`、Android 10 张、Desktop 5 张（含 `14-desktop-follow-system-light-NOT-followed.png`）、`ipad-ipad-appearance-en-dark.png`、`ipad-ipad-appearance-zh-hant-dark.png`、`ipad-ipad-landscape-appearance.png`、`ipad-ipad-landscape-home.png`、`/tmp/rel06-tios-ipad.xcresult` | 通过（已知限制 LIM-05） |

## 3. 自动化测试映射（回归入口）

| 层 | 测试类 / 用例 | 覆盖 AC |
| --- | --- | --- |
| 服务端 | `PairingTest`、`DeviceAuthTest` | AC-01、AC-13、AC-14 |
| 服务端 | `ClipWriteTest`、`LimitsTest`、`ContractTest` | AC-03、AC-05 |
| 服务端 | `ShareCreateTest`、`SharePageTest` | AC-06、AC-07、AC-13 |
| 服务端 | `RetentionTest`、`SeqAllocatorTest`、`ClipQueryTest`、`MigrationTest` | AC-11 |
| 服务端 | `SyncFlowIntegrationTest`、`WebSocketTest`、`ConcurrencyTest` | AC-03、AC-04、AC-09、AC-15 |
| 服务端 | `LoggingTest`、`ErrorMappingTest`、`HealthRoutesTest`、`DatabaseStartupTest`、`DatabaseHealthTest`、`RepositoryTest`、`AppConfigTest` | AC-12、AC-13、AC-18 |
| 客户端 shared | `TextSizeTest` | AC-05 |
| 客户端 shared | `OutboxWorkerTest`、`OutboxEnqueueTest`、`OutboxDaoTest`、`BackoffTest` | AC-03、AC-04 |
| 客户端 shared | `SyncGapTest`、`SyncEngineResyncTest`、`SyncEnginePullTest`、`SyncSocketTest`、`SyncRepositoryTest`、`SyncStatusTest` | AC-03、AC-04、AC-09、AC-11 |
| 客户端 shared | `ClipboardGuardTest` | AC-10 |
| 客户端 shared | `LoggingPolicyTest`、`LoggingRedactorTest`、`ApiLoggingTest`、`SocketLoggingTest` | AC-18 |
| 客户端 shared | `Base64ToolTest`、`JsonToolTest`、`UrlToolTest`、`TimestampToolTest`、`RegexToolTest`、`TrimTest` | AC-08 |
| 客户端 shared | `ClipDaoTest`、`ClipDaoPersistenceTest`、`ClipMergerTest`、`ClipRepositoryTest`、`SettingsRepositoryTest`、`ShareRepositoryTest`、`ApiDtoTest`、`ApiServiceTest`、`ServerUrlTest`、`ClipTimeTest`、`UlidTest`、`ErrorMappingTest` | AC-01–AC-04、AC-09、AC-14、AC-15 |
| XCUITest | `testPairingSendReceiveCopyPasteBackground` | AC-01、AC-02、AC-17（iOS 侧） |
| XCUITest | `testShareDraftPrefillWhenPaired`、`testUnpairForDraftScenario`、`testPairAfterUnpairedShareDraft` | AC-16 |
| XCUITest | `testAdaptiveLayoutAppearanceAndRotation` | AC-19、AC-20（iOS 侧） |

## 4. 已知限制与偏差（均由用户确认口径）

| 编号 | 内容 | 替代证据 | 确认 |
| --- | --- | --- | --- |
| LIM-01 | AC-12「稳定运行 24 小时」不在本轮窗口内 | 远程容器连续运行 2h55m + 13 次 5 分钟间隔健康/内存采样（内存 ≈29% 上限、健康全绿）；发布后由运维持续观察 | 用户确认（2026-11-18） |
| LIM-02 | AC-18「release/debug 各使用一天」不在本轮窗口内 | debug 诊断 ON/OFF 双窗口 + Desktop release 运行窗口取证 + `LoggingPolicyTest`/`LoggingRedactorTest` 单元测试 | 用户确认（2026-11-18） |
| LIM-03 | AC-16 系统分享面板内 UI 点击（选择扩展、确认发送、取消）不可自动化：本机 Xcode 无 Simulator.app GUI、无 idb/cliclick，XCUITest 不能跨进程操作分享面板 | 扩展注册与激活规则核查、App Group 草稿闭环三用例、App 内同一发送链路用例，及 M4 `e2e-ios.md` IOS-06/07/08/10 历史记录 | 用户确认（2026-11-18） |
| LIM-04 | AC-17 系统粘贴权限弹窗（允许/拒绝）不可由模拟器脚本注入点击 | 粘贴按钮点击路径用例（pairing2）与 M4 `e2e-ios.md` IOS-11 记录；每次点击独立触发、用户拒绝后可再次点击重试 | 用户确认（2026-11-18） |
| LIM-05 | AC-20 Desktop 端「跟随系统」仅在启动时解析系统外观，运行中改变系统外观不跟随（Android/iOS 正常）；固定浅色/深色即时生效且重启保持 | 缺陷截图 `14-desktop-follow-system-light-NOT-followed.png`；登记为 v1.0.0 已知限制，不阻塞发布 | 用户确认（2026-11-18） |
| LIM-06 | AC-18「debug 默认可见令牌」：实现中 `authorization=<redacted>` 始终脱敏，比原文更严格（M3 D4 脱敏口径，`LoggingRedactorTest` 覆盖） | `android-diagnostic-on.log` 诊断行 | 既定设计，非缺陷 |

AC-17「回前台 3s 内可见」测量口径说明：以「前台恢复完成（App idle）→ 测试首次确认可见」计为 2.95s ≤3s；以 Activate 指令起算为 3.12s，其中约 2s 为 XCTest 查找/点击搜索框与键入 13 字符、1s 为轮询间隔，无帧级视频证据可精确到 UI 呈现时刻；用户裁定判通过（2026-11-18），完整时间线见 `AC-17/timeline.txt`。

## 5. 失败项与闭环

本轮无失败项，无新增缺陷卡。唯一产品缺陷（AC-20 Desktop 运行中不跟随系统外观）经用户确认登记为 v1.0.0 已知限制 LIM-05，证据随矩阵归档。

## 6. 回归命令与真实输出

```bash
cd ClipboxServer && ./gradlew test --rerun-tasks
cd ClipboxClient && ./gradlew :shared:allTests --rerun-tasks
```

- 服务端：`BUILD SUCCESSFUL in 18s`，`tests=126 failures=0 errors=0 skipped=0`（`build/test-results/test/*.xml` 汇总）。
- 客户端：`BUILD SUCCESSFUL in 39s`，`tests=745 failures=0 errors=0 skipped=0`（`shared/build/test-results/` 四个目标汇总：desktopTest / iosSimulatorArm64Test / testDebugUnitTest / testReleaseUnitTest）。
- 原始输出：`/Users/wangxingxing/Documents/ClipboxREL06/logs/server-test.log`、`client-alltests.log`（含 `SERVER_EXIT=0`、`CLIENT_EXIT=0`）。

## 7. 结论

- 20/20 条 AC 均有环境、步骤、证据与结论，全部有明确判定。
- 完全通过 15 条：AC-01…AC-11、AC-13、AC-14、AC-15、AC-19。
- 通过且含用户确认的已知限制项 2 条：AC-17（LIM-04）、AC-20（LIM-05）。
- 部分通过（已知限制）1 条：AC-16（LIM-03）。
- 替代证据 + 已知限制 2 条：AC-12（LIM-01）、AC-18（LIM-02）。
- 三端覆盖：Android、Desktop（macOS）、iOS 模拟器（iPhone 16 + iPad (A16)）；分享页桌面与移动浏览器各验证一次；无失败项，v1.0.0 验收通过。

---

## 2026-11-18 追加：AC-01 / AC-13 口径更新（ADR-0006）

配对码改由已配对设备生成，服务端只存哈希且不再打印。AC-01 的“生成配对码”步骤改指设置页取码；AC-13 追加断言 `device_pairing_codes` 仅含 64 位小写 hex、日志中配对码出现 0 次。历史证据保留；T-REL-15 提供新证据。

### T-REL-15 新证据（2026-11-18）

- AC-01：全新数据目录下 `POST /api/v1/bootstrap` 初始化首台设备 → 设备 A 取码 → 设备 B `POST /api/v1/pair` 201 → A/B 双向 REST 同步命中；Android 设置页取码与配对页初始化在 T-UI-21 实测。
- AC-13：`device_pairing_codes` 仅 1 行 64 位小写 hex，`devices.token_hash` 3 行 64 位小写 hex；db/wal/shm 明文配对码 0 次；服务端日志明文码 0 次；取码第 10 次 429 且 `Retry-After: 60`。
- 证据：`/tmp/rel15-acceptance.json`、`/tmp/clipbox-rel15-server.log`、[release-artifacts.md](release-artifacts.md) 的 T-REL-15 小节。
