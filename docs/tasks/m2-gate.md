# M2 Gate：断网重放、补拉与分享全部验证

- 日期：2026-11-17
- 范围：T-S-10 → T-IOS-07 十三张卡完成后的里程碑验收
- 截图目录：`docs/evidence/ClipboxM2/`（仓库内，41 个文件；已在 `.gitignore` 忽略，不提交二进制产物）

## 1. 环境

| 端 | 环境 |
| --- | --- |
| 服务端 | `ClipboxServer`，`ADMIN_TOKEN=<已配置> PUBLIC_BASE_URL=http://localhost:8080 ./gradlew run`，SQLite WAL，实例 `:8080` |
| Android | `emulator-5554`（Medium_Phone_API_37，1080×2400），`adb reverse tcp:8080 tcp:8080` |
| Desktop | macOS arm64，`./gradlew :composeApp:run`，数据目录 `~/Library/Application Support/Theca` |
| iOS | iPhone 16 模拟器 / iOS 27.2，XCUITest 完成配对 |

验证期间服务端带访问日志重启一次（`/tmp/gate-server.log`），用于 G17/G19 取证；SQLite 数据目录沿用，未重建。

## 2. 验证矩阵

| 编号 | 场景 | 命令 / 操作与证据 | 结果 |
| --- | --- | --- | --- |
| G1 | 服务端回归 | `cd ClipboxServer && ./gradlew test --rerun`；21 个测试类 123 个用例 | 通过：0 失败、0 跳过 |
| G2 | 客户端共享测试 | `cd ClipboxClient && ./gradlew :shared:allTests --rerun`；67 个测试套 439 个用例 | 通过：0 失败、0 跳过 |
| G3 | Android 构建 | `./gradlew :composeApp:assembleDebug`；产物 `composeApp/build/outputs/apk/debug/composeApp-debug.apk` | 通过 |
| G4 | Desktop 启动与配对 | `:composeApp:run` 启动；空态「去配对」→ 配对页（含「未加密连接，仅用于学习」）→ 输入 6 位配对码 → 已配对/已同步，拉取到服务端全部记录（`gate-window-empty.png`、`gate-window-pairing.png`、`gate-window-paired.png`） | 通过 |
| G5 | iOS 构建 | `:composeApp:linkDebugFrameworkIosSimulatorArm64` + `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build`（T-IOS-07 验收时执行，Gate 复用同一次构建） | 通过 |
| G6 | 配对与双向实时 | iOS UI 测试消息（置顶）与设备 B 记录出现在 Android（`gate-android-1.png`、`gate-ios-1.png`）；Android 置顶记录出现在 iOS；Desktop 在配对后立即拉全记录，并由 Desktop 发送 `gate-desktop-send-check`（服务端 14:12:18，`POST /api/v1/clips 201`），Android 即时显示「Desktop · Mac OS X 刚刚」（`gate-android-desktop-send.png`、`gate-window-sent.png`）；服务端 WS `101` 连接日志存在 | 通过 |
| G7 | 断网重放 | 停服务端；Android 发送 2 条并置顶 → 状态「待同步」；重启服务端 → 自动重放（seq 4/5/6），状态转「已同步」；无重复记录，置顶保留（`gate-android-offline-sent.png`、`gate-android-pin-offline.png`、`gate-android-replayed.png`） | 通过 |
| G8 | REST 补拉 | 杀掉 Android 前台，另一端写入 seq7；重开 App 先展示本地数据，随后补拉并推进游标（`gate-android-pull.png`）；Room 提取 `meta.lastSeq=11`（`/tmp/gate-android.db`） | 通过 |
| G9 | WS 缺口补拉 | 运行期缺少网络整形工具，未能强制制造「非连续 seq」；由 `SyncGapTest`（databaseTest）覆盖「缺口只触发一次 REST 补拉、事件不重复应用」；服务端日志记录 WS `101` 建连/断连与重连行为 | 限制：见 §4-5 |
| G10 | 全量重同步 | 无运行时手动入口（按计划不伪造 UI 操作）；由 `SyncEngineResyncTest` 覆盖 `resyncRequired` 重建与 Outbox/失败记录保留 | 限制：见 §4-6 |
| G11 | 分享生成 | Android 操作面板 → 生成分享：四档「1 小时 / 24 小时 / 7 天 / 阅后即焚」，默认 24 小时；结果页展示完整 URL、过期时间、一次性标识与复制链接；有效期内可重复访问；未同步记录入口置灰并显示「同步后可分享」（`gate-android-sheet.png`、`gate-android-share-result.png`） | 通过 |
| G12 | 分享页安全 | `curl -i` 校验：CSP `default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Cache-Control: no-store`；`<script>`/`<img onerror>` 被转义、无外部资源、无 JS、`robots noindex,nofollow`；非法 token 404「链接无效或已被删除」 | 通过 |
| G13 | 一次性消费 | 一次性链接第一次 200，第二次 410 `SHARE_CONSUMED`「链接已被使用」；并发访问仅一次 200（`SharePageTest` 覆盖） | 通过 |
| G14 | 过期链接 | TTL=60 创建，过期后访问 410 `SHARE_EXPIRED`「链接已过期」 | 通过 |
| G15 | 新记录提示 | 工具箱页收到新记录 → 3 秒 Snackbar「收到新记录」，点击回历史页顶部；历史页滚动后收到 → 提示；历史页顶部收到 → 不提示、直接插入（`gate-android-hint.png`、`gate-android-hint-clicked.png`、`gate-android-hint2.png`） | 通过 |
| G16 | 搜索 / 置顶 / 删除 | 搜索 300ms 防抖，命中 2 条、清空恢复；置顶即点即改（置顶 2）；删除取消无任何变更；确认删除后记录移除且不可恢复（`gate-android-search.png`、`gate-android-pinned.png`、`gate-android-cancel.png`、`gate-android-confirm.png`、`gate-android-deleted.png`） | 通过 |
| G17 | iOS 前后台 | 14:12:53 打开「设置」使字匣进入后台；14:12:57 WebSocket `101` 连接关闭（`latencyMs=244671`）；14:12:56 设备 B 写入 seq13；14:12:59 回前台触发 `GET /api/v1/clips 200`；iOS 本地 `meta.lastSeq=13`，`gate-bg-probe-*` 与 `gate-desktop-send-check` 均落库；全程无通知、无后台轮询（`gate-ios-bg-before.png`、`gate-ios-bg-after.png`） | 通过 |
| G18 | iOS 分享扩展 | 扩展 target 注册成功（`pluginkit`）；App Group 草稿 33 B 冷启动预填并聚焦（`gate-ios-draft.png`）；重复深链按 `createdAt:id` 去重不重复预填；预填后不自动发送（回前台截图仍保留草稿，`gate-ios-bg-after.png`）；已配对状态由 XCUITest 配对验证 | 通过（未配对保留见 §4-7） |
| G19 | 安全复查 | Android 清单仅 `INTERNET`；无第三方日志/崩溃/分析 SDK；扩展无网络与令牌代码；默认日志检索：正文标记、设备令牌、分享 token、管理接口配对码命中数均为 0；分享访问记录为 `pathTemplate=/s/{token}`；无 Caddy / TLS / HSTS / 域名配置；`https://` 仅出现在配置校验与单测字符串中 | 通过 |

## 3. 本批任务卡提交

`4873a47` T-S-10 · `3f093d1` T-CORE-14 · `e949219` T-CORE-09 · `7c685c6` T-CORE-10 · `9d64e40` T-UI-04 · `6634bd4` T-UI-05 · `6a5d35a` T-UI-06 · `99a290e` T-UI-07 · `e57aed5` T-S-11 · `c9e8132` T-IOS-04 · `aa44a15` T-IOS-05 · `a012567` T-IOS-06 · `6ec524b` T-IOS-07。

## 4. 偏差与已知限制

1. **iOS `.inactive` 不断开 WS**：按用户确认的决策，只有 `.background` 触发断连；`.inactive`（来电、控制中心）保持连接。
2. **T-IOS-05 无 UI 入口**：仅提供 `IosClipboardService` 与三语文案；模拟器无法产生真实粘贴权限弹窗，权限拒绝路径未运行期复现。
3. **分享扩展文案**：扩展为极简中文 Swift 文案（非 Compose 页面），不进入 `composeResources`；范围仅限扩展界面。
4. **分享结果仅内存**：历史页内再次打开可复用结果，应用重启后丢失；符合「不写本地分享记录」的约束。
5. **G9 运行期限制**：本机无网络整形工具，无法稳定制造 WS 缺口；以 `SyncGapTest` 单测与真实 WS 重连日志为证据。
6. **G10 运行期限制**：全量重同步没有手动入口；以 `SyncEngineResyncTest`（含 Outbox 与失败记录保留断言）为证据。
7. **G18 未配对保留**：`simctl openurl` 会弹出 SpringBoard「在字匣中打开？」确认框并阻塞场景激活，未配对 → 配对后保留未做运行期复现；草稿文件在发送成功前不清除的设计与代码评审可证。已配对预填、去重与「不自动发送」已实测。
8. **Desktop 失效令牌**：早期联调遗留的 Desktop 令牌被服务端判 401 时，顶栏显示「已配对」而状态条显示「未配对」；M2 无桌面解绑入口，Gate 中备份并移空本地测试数据（`/tmp/gate-desktop-db-backup/`、`/tmp/gate-desktop-settings-backup.pb`，未删除）后重新配对。建议 M3 T-UI-08 设置页提供解绑/重新配对。
9. **Content-Type 大小写**：分享页返回 `text/html; charset=UTF-8`（大写），`docs/api.md` 写 `utf-8`；HTTP 语义大小写不敏感，不视为缺陷。
10. **截图转录误差**：曾用 UI 截图转录的分享 URL 得到 404（43 位 token 转录不完整）；G12–G14 的安全语义均以 API 创建的真实链接复核，非实现缺陷。
11. **XCUITest 索引修正**：搜索框加入后发送器由 `textViews[0]` 变为 `textViews[1]`，本提交修正测试索引。
12. **启动配对码日志**：服务端启动时打印一次自动生成的配对码，冻结文档（功能需求文档 §自助配对、服务端技术架构 §日志）明确允许；请求日志不记录配对码。与 AGENTS 第 10 条按文档例外口径执行。

## 5. 出口结论

- G1–G19 均有真实输出或明确限制说明。
- 十三张卡 + Gate 提交后 M2 完成；下一张为 M3 的 T-CORE-11（同步状态与手动同步）。
