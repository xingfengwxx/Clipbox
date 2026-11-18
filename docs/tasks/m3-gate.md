# M3 Gate：Android + Desktop 日常可用

- 日期：2026-11-17
- 范围：T-CORE-11 → T-UI-19 十四张卡 + T-UI-20 本地端到端验收
- 截图目录：`~/Documents/ClipboxM3/`（仓库外，99 个文件；不提交二进制）
- 场景明细：[e2e-local.md](e2e-local.md)

## 1. 环境

| 端 | 环境 |
| --- | --- |
| 服务端 | `ClipboxServer`，`:8080`，SQLite WAL，数据目录 `ClipboxServer/data/`；`PUBLIC_BASE_URL=http://localhost:8080` |
| 隔离服务端 | `PORT=18080 DATA_DIR=/tmp/clipbox-ac11`，仅 AC-11 使用 |
| Android | `emulator-5554`（Medium_Phone_API_37，1080×2400，420dpi），debug APK，`adb reverse tcp:8080 tcp:8080` |
| Desktop | macOS arm64，`:composeApp:run`，`~/Library/Application Support/Theca` |
| 第三设备 | Node 24 WebSocket + 外部设备令牌（`/tmp/v19-device.json`，不提交） |
| iOS | 本 Gate 不包含；留待 M4 |

验收期间主服务端带访问日志重启一次（保留数据），用于 AC-09；Android 解除配对并重新配对一次，用于 AC-14。

## 2. 验证矩阵

| 编号 | 场景 | 命令 / 操作与证据 | 结果 |
| --- | --- | --- | --- |
| G1 | 服务端回归 | `cd ClipboxServer && ./gradlew test --rerun`；21 个测试类 123 个用例 | 通过：0 失败、0 错误、0 跳过 |
| G2 | 客户端共享测试 | `cd ClipboxClient && ./gradlew :shared:allTests`；115 个测试类 739 个用例（Android/JVM/iOS 模拟器目标） | 通过：0 失败、0 错误、0 跳过 |
| G3 | Android 构建 | `./gradlew :composeApp:assembleDebug`；产物 `composeApp/build/outputs/apk/debug/composeApp-debug.apk` | 通过 |
| G4 | Android 安装与配对 | `adb install -r`、`adb reverse`、`am start`；配对页含服务器地址输入与「未加密连接，仅用于学习」；配对后进入同步完成态 | 通过 |
| G5 | Desktop 启动与配对 | `:composeApp:run`；空态 → 配对页 → 配对码 → 拉取全量历史；设置页可解绑/重配 | 通过（窗口自动化限制见 D14） |
| G6 | 双向实时收发 | AC-01：Android → Node WS +3ms、Desktop `lastSeq=38`、`pbpaste` 命中；AC-02：Desktop `pbcopy` → 服务端 seq48 → Android 前台可见、点击复制、粘贴入输入框一致（`e2e-ac01-android-sent.png`、`e2e-ac02-*.png`） | 通过 |
| G7 | 断网重放 | AC-03：断网发 3 条 → 待同步；恢复后各重放一次，无重复，`outbox=0`（`e2e-ac03-*.png`） | 通过 |
| G8 | 冷启动补拉 | AC-04：移除 `adb reverse` + force-stop 2 分钟，期间写入 seq32–36；冷启动 REST 补拉，顺序与游标正确、无重复（`e2e-ac04-*.png`） | 通过 |
| G9 | 超限拒绝 | AC-05：服务端 70,000 字节 → `400 VALIDATION_ERROR`；客户端 65,536 边界由 `TextSizeTest` 覆盖；120 KiB 手工注入按用户指示跳过（D12） | 部分通过 |
| G10 | 分享安全 | AC-06：CSP、`nosniff`、`no-referrer`、`no-store`，HTML 全转义，无外部资源/JS；AC-07：一次性第二次 410、过期 410，错误页不回显正文（`/tmp/e2e-share-*`） | 通过 |
| G11 | 工具箱离线 | AC-08：飞行模式下 JSON、Base64、URL、时间戳、正则 5 个工具均可计算/复制，不写历史、不联网（`e2e-ac08-*.png`） | 通过 |
| G12 | 重启持久化 | AC-09：服务端重启后 `clips=43`、`server_seq=48`；Android 冷启动恢复已配对/已同步与新记录（`e2e-ac09-server-restart.png`） | 通过 |
| G13 | 剪贴板回环 | AC-10：自动粘贴 + 自动发送同时开启，Android 发送后 Desktop 只写一次、6s 内无回传、`outbox=0`；2s 窗口外同内容可再次上传（seq45/46）。修复自动发送未唤醒 Outbox 的缺陷后，自动发送记录即时重放（seq42/43） | 通过（缺陷已修，见 D11） |
| G14 | 保留与墓碑 | AC-11：501 → 软删 1（seq505）、清理旧墓碑后 `sync_floor=504`；`since=1`/`999999` 重同步；**`since=0` 首页后 `since=201` 被误判重同步，全量分页无法完成**（D8） | 部分通过（D8 已于 2026-11-18 修复关闭，见 §4-8） |
| G15 | 安全基线 | AC-13：设备令牌 SHA-256 前缀 `d3faeda08d84` 与数据库一致；分享 token 哈希一致；日志敏感模式 0 命中；配对第 6 次 429；分享路径记录为模板（`/tmp/pair-6.json`） | 通过 |
| G16 | 解除配对 | AC-14：解绑后本机 clips/outbox/meta 清空、回配对页；服务端 41 条/12 设备保留；Desktop 在线继续收新记录；重配对拉回历史（`e2e-ac14-*.png`） | 通过 |
| G17 | 多设备广播 | AC-15：三端在线广播；置顶 → 服务端 seq49、Desktop 同步；删除 → 墓碑 seq50、双端移除（`e2e-ac15-*.png`） | 通过 |
| G18 | 诊断日志 | AC-18：debug 默认开、release 默认关；`LoggingPolicyTest`/`LoggingRedactorTest` 覆盖关闭静默与诊断脱敏；实际日志 0 敏感命中；诊断模式不显示明文令牌（按决策，D4） | 部分通过 |
| G19 | 主题与多语言 | AC-19/20：Android English + 深色立即生效、冷启动保持；恢复简体与跟随系统后浅色跟随；三语/三主题截图见 `visual-checklist.md`；iOS 留待 M4 | Android 通过；三端完整验收留待 M4 |
| G20 | 24 小时稳定 | AC-12：未执行（D13） | 未执行 |
| G21 | iOS 场景 | AC-16/17 与 iOS 主题/语言/iPad 留待 T-IOS-08/09 | 范围外 |

## 3. 本批任务卡提交

`1f82b4e` T-CORE-11 · `75c6725` T-CORE-12 · `ec26242` T-CORE-13 · `fe04070` T-CORE-15 · `dc9e1e2` T-UI-08 · `a034b98` T-UI-09 · `d2a3828` T-UI-10 · `640c1f1` T-UI-11 · `2d00e95` T-UI-12 · `752515a` T-UI-13 · `9e07377` T-UI-14 · `e4ec98d` T-UI-15 · `2db30a4` T-UI-16 · `e8f4c02` T-UI-19 · 本提交 T-UI-20。

## 4. 偏差与已知限制

1. **同步状态保留 `Resyncing`**（D1）：任务卡列 `Idle/Syncing/Offline/Pending/Failed/Unauthorized`，现有实现多一个 `Resyncing`；按单值优先级 `Unauthorized > Resyncing > Syncing > Offline > Failed > Pending > Idle` 展示，不拼接多状态。
2. **宽屏断点 600dp**（D2）：T-IOS-08 原文 ≥840dp，按用户决策使用 600dp；iOS 验收在 M4 复核。
3. **iPad 机型**（D3）：任务卡写 iPad 10th generation，本机不可用，M4 使用 iPad (A16)。
4. **诊断令牌脱敏**（D4）：REQ-SET-011 允许诊断明文令牌，按决策 #3 一律脱敏；因此 AC-18 原文「debug 可见令牌」不成立，单测与日志检索证明无明文泄漏。
5. **回环口径**（D5）：任务卡描述 `lastApplied`/`lastSent` 均可不处理；按 REQ-RECV-005 只以 `lastAppliedHash` + 2s 窗口跳过，`lastSentHash` 仅记录。
6. **服务器地址动态化**（D6）：`SyncRuntime` 原固定构建期地址；T-CORE-15 改为读设置并在配对成功/解绑时重建 session，补齐配对页地址输入。
7. **新增 Koin 依赖**（D7）：仅新增 `koin-compose-viewmodel` 4.1.1（与现有 Koin 同版本），已获批准；未升级其他依赖。
8. **AC-11 全量分页与 `sync_floor` 冲突**（D8，已关闭）：`sync_floor=504` 时 `since=0` 首页返回 seq2–201、`hasMore=true`，续页 `since=201` 因 `<504` 返回 `resyncRequired=true`，与 `api.md`「`since=0` 按页拉取直到 `hasMore=false`」不一致；客户端 `SyncEngine.resyncLocked()` 将全量分页中出现重同步判为服务端违约。根因是冻结文档未定义「全量分页续页」与「增量游标」的区分，服务端无状态无法自行判断。**2026-11-18 经用户批准采用方案 A 修复**：`GET /api/v1/clips` 新增可选参数 `full`，`full=true` 跳过 `since < sync_floor` 判定、保留 `since > serverMaxSeq` 保护；客户端首次配对与强制重同步整轮分页携带 `full=true`。契约、架构、需求与术语表同步修订；`SyncFlowIntegrationTest.full sync completes after retention purge with non zero sync floor` 复现「501 条 + 软删 + 墓碑清理 + 多页全量拉取」并通过，服务端与客户端全量测试绿。未重新执行手工复跑，D8 依据自动化集成测试关闭。详见 [BUG-001](bug-001-ac11-full-sync-floor.md)。
9. **iOS 日志 sink**（D9）：若 Kotlin/Native os_log 绑定不可用则用 NSLog，M4 记录 API 偏差。
10. **模拟器限制**（D10）：iOS 粘贴权限弹窗、系统分享面板、后台真机行为可能无法完整复现，M4 用 XCUITest/手工截图取证并登记。
11. **自动发送未唤醒 Outbox**（D11，已修）：`ClipboardController` 自动发送成功后没有 UI 调用方唤醒 `OutboxWorker`，记录滞留到下一次用户操作；T-UI-20 注入 `wakeOutbox` 并在成功后调用，实测 seq42/43 即时重放。
12. **AC-05 客户端手工注入跳过**（D12）：按用户指示未注入约 120 KiB 文本；服务端超限与客户端字节边界单测通过，UI 超限提示未手工截图。
13. **AC-12 未执行**（D13）：24 小时稳定性不属于本轮本地矩阵，留待发布前验证。
14. **Desktop UI 自动化限制**（D14）：验收期间窗口无法稳定获得前台焦点，未做窗口截图操作；发送/剪贴板路径以 `pbcopy`/`pbpaste`、SQLite 与服务端日志取证，均为真实进程。
15. **M2 Desktop 失效令牌偏差关闭**：T-UI-08 设置页提供解除配对/重新配对，AC-14 已验证清空与重配。

## 5. 出口结论

- G1–G8、G10–G13、G15–G17 全部通过；G9、G14、G18 为部分通过并有明确理由（G14 的 D8 已于 2026-11-18 修复关闭）；G20 未执行；iOS 场景按计划留待 M4。
- Android 与 Desktop 已具备日常自用能力：配对/解绑、双向实时、离线重放、补拉、搜索/置顶/删除、5 个工具、设置、主题、三语、诊断日志、Desktop 自动收发均实测可用。
- **M3 Gate 通过**：AC-11 的「全量分页 + 墓碑清理」冲突（D8）已于 2026-11-18 以 BUG-001 方案 A 修复，并由自动化集成测试验证关闭；T-REL-06 前无需再决策。
- 下一步：T-IOS-08（iPad 宽屏、主题/三语、扩展三语）。
