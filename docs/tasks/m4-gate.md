# M4 Gate：iOS 模拟器验收

- 日期：2026-11-17
- 范围：T-IOS-08（iPad 宽屏、主题/三语、扩展三语）+ T-IOS-09（iOS 模拟器端到端验收）
- 截图目录：`docs/evidence/ClipboxM4/`（仓库内，37 个文件；已在 `.gitignore` 忽略，不提交二进制）
- 场景明细：[e2e-ios.md](e2e-ios.md)

## 1. 环境

| 端 | 环境 |
| --- | --- |
| 服务端 | `ClipboxServer`，`:8080`，SQLite WAL，数据目录 `ClipboxServer/data/`；`PUBLIC_BASE_URL=http://localhost:8080` |
| iPhone | iPhone 16，iOS 27.2（UDID `0278FBBD-82F0-4092-AF60-B6B44C8228A9`） |
| iPad | iPad (A16)，iOS 27.2（UDID `301101B4-D695-45D1-ABCB-782FF20DDCCB`） |
| 构建 | `linkDebugFrameworkIosSimulatorArm64` + `xcodebuild` 模拟器构建 |
| 测试 | XCUITest `iosAppUITests`，5 个用例；第二设备由管理接口配对 |

App Group：`group.com.wangxingxing.clipbox`；分享草稿 `share-draft.json`（`id` / `text` / `createdAt`）。

## 2. 验证矩阵

| 编号 | 场景 | 命令 / 操作与证据 | 结果 |
| --- | --- | --- | --- |
| G1 | iOS framework 链接 | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | 通过：BUILD SUCCESSFUL |
| G2 | iPhone 模拟器构建 | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build` | 通过：BUILD SUCCEEDED |
| G3 | iPad 模拟器构建与安装 | 同一 target 由 `xcodebuild test` 在 iPad (A16) 构建、安装并执行 | 通过 |
| G4 | 配对 | 未配对空态 → 配对页（默认 `http://127.0.0.1:8080`）→ 6 位码 → `POST /api/v1/pair 201` → 历史页；服务端日志见 e2e-ios §4 | 通过 |
| G5 | 发送与键盘避让 | 输入 `ios-sent-1794909661`；发送按钮可点且底边不低于键盘顶边；服务端 `POST /api/v1/clips 201`；第二设备读取命中（`tios09-ios-keyboard-avoidance.png`、`tios09-ios-sent.png`） | 通过 |
| G6 | 实时接收与复制/粘贴 | 第二设备写入后记录实时出现（`tios09-ios-received-ws.png`）；点击复制提示「已复制到剪贴板」；显式「粘贴」回填输入框（`tios09-ios-copied-pasted.png`） | 通过 |
| G7 | 前后台同步 | 后台 4 秒内无请求、无通知（SpringBoard 无正文）；回前台先 `GET /api/v1/clips 200` 补拉再重连 WS；`pbpaste` 命中后台期间正文；服务端 WS 关闭/重连日志见 e2e-ios §4（`tios09-ios-foreground-catchup.png`） | 通过 |
| G8 | 分享草稿 | 已配对冷启动预填（`tios09-ios-share-draft-prefill.png`）；解除配对后草稿保留（`tios09-ios-unpaired.png`）；未配对 → 配对后预填且 3 秒内不自动发送（`tios09-ios-unpaired-draft-paired-prefill.png`） | 通过 |
| G9 | 分享扩展契约 | `pluginkit` 注册 `com.wangxingxing.clipbox.share(1.0.0)`；激活规则 `public.plain-text`；扩展内嵌主 App；无网络、无令牌存储（T-IOS-06 代码约束） | 通过（系统面板未自动化，D16） |
| G10 | iPad / iPhone 布局与三语主题 | iPad 竖屏 NavigationRail、iPhone 底部导航；主题三段、简中/繁中/英文切换不截断；旋转保持状态（`tios09-ipad-*`、`tios09-ipad-landscape-*.upright.png`） | 通过 |
| G11 | 权限与后台声明 | `UIBackgroundModes` 不存在；无通知/剪贴板权限；无推送注册、无 `beginBackgroundTask`；ATS `NSAllowsArbitraryLoads=true` 仅学习版；URL Scheme `clipbox` | 通过 |
| G12 | AC 覆盖 | AC-16（分享扩展）、AC-17（前台同步/粘贴权限）由 G5–G9 取证；AC-19/20 的 iOS 部分由 G10 取证；AC-01…AC-15 已在 M3 通过 | 通过（限制见 §4） |

## 3. 本批任务卡提交

`dccabe9` T-IOS-08 · 本提交 T-IOS-09。

T-IOS-08 关闭了 M2 的「扩展仅中文」偏差；T-IOS-09 关闭了 M2 的「未配对分享草稿未验证」偏差（SpringBoard 深链确认框限制不再阻塞）。

## 4. 偏差与已知限制

1. **宽屏断点 600dp**（D2，沿用）：T-IOS-08 原文 ≥840dp，按用户决策使用 600dp；iPad 与 iPhone 均按 600dp 切换底部导航 / NavigationRail。
2. **iPad 机型**（D3，沿用）：任务卡写 iPad 10th generation，本机不可用，改用 iPad (A16)；构建与截图记录实际机型。
3. **iOS 日志 API**（D9，关闭）：Kotlin/Native `os_log` 绑定不可用，`IosLogSink` 使用 `NSLog` 输出到系统日志；不落盘、不上传，日志脱敏口径与 M3 D4 一致。
4. **模拟器限制**（D10，沿用）：粘贴权限弹窗、系统分享面板、真机后台行为无法在模拟器完整复现；用 App Group 注入、服务端日志与 XCUITest 取证，不伪装通过。
5. **系统分享面板未自动化**（D16）：分享扩展本身已注册并用激活规则/内嵌位置验证；主 App 消费路径通过直接写入 App Group 草稿验证。扩展 UI 在 T-IOS-08 人工验证。
6. **跨端接收代理**（D17）：iOS → 其他端由第二设备 REST 分页读取证明落库，未在同一轮启动 Desktop / Android UI 同屏观察；Desktop / Android 双向已在 M3 Gate 实测。
7. **iPad 未配对**（D18）：iPad 布局截图在未配对空态取得；配对后双栏内容态未单独截图，不影响断点与导航验证。
8. **旋转截图**（D19）：无头模拟器横屏帧缓冲不旋转，导出后用 `sips -r -90` 生成 `.upright.png`；仅取证处理，App 内旋转状态已由断言验证。
9. **AC-11 全量分页冲突**（D8，M3 遗留，已关闭）：属冻结文档层面的协议空洞，与 iOS 无关。2026-11-18 经用户批准采用 BUG-001 方案 A（`full` 参数）修复，自动化集成测试覆盖「501 条 + 软删 + 墓碑清理 + 多页全量拉取」并通过；详见 [BUG-001](bug-001-ac11-full-sync-floor.md)。
10. **AC-12 24 小时稳定**（D13，M3 遗留）：未执行，留待发布前稳定性验证。

## 5. 出口结论

- G1–G12 全部通过；D16–D19 为模拟器取证限制，均有无需伪造的替代证据。
- iOS 模拟器已具备完整能力：配对、发送、实时接收、复制/粘贴、前后台补拉、分享草稿（已配对与未配对）、解除配对、主题/三语、iPhone/iPad 布局。
- **M4 Gate 通过**（带 §4 已知限制）；M3 遗留的 D8（AC-11 全量分页）已于 2026-11-18 修复关闭。
- 下一步：T-REL-01（服务器准备，用户动作）。
