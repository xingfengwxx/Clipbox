# M4 iOS 模拟器验收记录（T-IOS-09）

- 日期：2026-11-17
- 范围：iOS 16+ 模拟器上的配对、发送、接收、复制/粘贴、前后台同步、分享草稿、主题/三语、iPhone/iPad 布局；真实本地服务端
- 截图目录：`~/Documents/ClipboxM4/`（仓库外，T-IOS-09 新增 17 个文件；不提交二进制）
- 结果目录：`/tmp/tios09-iphone-main.xcresult`、`/tmp/tios09-draft-prefill.xcresult`、`/tmp/tios09-unpair.xcresult`、`/tmp/tios09-pair-after-draft.xcresult`、`/tmp/tios09-ipad-adaptive.xcresult`

## 1. 环境

| 项 | 环境 |
| --- | --- |
| 服务端 | `ClipboxServer`，`:8080`，SQLite WAL，数据目录 `ClipboxServer/data/`；`ADMIN_TOKEN` 为 32 位测试值，`PUBLIC_BASE_URL=http://localhost:8080`；访问日志 `/tmp/clipbox-m3-server-restart.log` |
| iPhone | iPhone 16，iOS 27.2，UDID `0278FBBD-82F0-4092-AF60-B6B44C8228A9` |
| iPad | iPad (A16)，iOS 27.2，UDID `301101B4-D695-45D1-ABCB-782FF20DDCCB`（任务卡写 iPad 10th generation，本机不可用，见 M4 D3） |
| 构建 | Xcode 完整工具链；`composeApp` iOS targets（`iosSimulatorArm64`） |
| 测试 | XCUITest target `iosAppUITests`，5 个用例；截图经 `xcresulttool export attachments` 导出 |
| App Group | `group.com.wangxingxing.clipbox`；草稿文件 `share-draft.json` |
| 第二设备 | XCUITest 内通过管理接口配对的 REST 设备，替代 Desktop / Android 观察写入 |

调试默认服务器地址由构建配置注入为 `http://127.0.0.1:8080`；模拟器与本机服务端同一网络栈，无需 `adb reverse`。

## 2. 构建

```bash
cd ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
# BUILD SUCCESSFUL in 611ms

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
# ** BUILD SUCCEEDED **
```

iPad 用例由 `xcodebuild test` 自动构建并安装同一 target；日志 `/tmp/tios09-ipad-adaptive.log`。

## 3. 场景结果

| 编号 | 场景 | 操作与证据 | 结果 |
| --- | --- | --- | --- |
| IOS-01 | 未配对进入配对页 | 全新安装启动，空态按钮「去配对」→ 配对页；服务器地址输入框默认 `http://127.0.0.1:8080`（构建配置注入，非硬编码）；「6 位配对码」「设备名称」可见。截图 `tios09-ios-*`（配对流程内） | 通过 |
| IOS-02 | 配对 | 管理接口生成一次性配对码（不记录到文档），输入后「配对」可点；服务端 `POST /api/v1/pair 201`（18:00:52），随后 `GET /api/v1/clips 200` 完成首次拉取 | 通过 |
| IOS-03 | App 输入发送 | 点击输入框弹出键盘；输入 `ios-sent-1794909661` 后断言「发送」可点、可命中，且 `button.maxY ≤ keyboard.minY + 1`，键盘不遮挡；点击发送 → 服务端 `POST /api/v1/clips 201`（18:00:57）；第二设备分页读取命中该正文，证明已落库并可被其他端读取。截图 `tios09-ios-keyboard-avoidance.png`、`tios09-ios-sent.png` | 通过 |
| IOS-04 | 其他端发送 → iOS 前台实时接收 | 第二设备 `POST /api/v1/clips 201`（18:01:01，`ios-received-1794909661`）；搜索过滤后记录行出现（WS 实时），截图 `tios09-ios-received-ws.png`；点击记录出现「已复制到剪贴板」，再点显式「粘贴」按钮，输入框包含该正文，截图 `tios09-ios-copied-pasted.png` | 通过 |
| IOS-05 | 后台断开与回前台补拉 | 按 Home 进入后台 4 秒：期间第二设备写入 `ios-background-1794909673`（18:01:13），iOS 无任何请求；SpringBoard 无障碍树中不存在该正文，无通知。回前台后 `GET /api/v1/clips 200` 两次（18:01:15）补拉，记录出现（25s 内），截图 `tios09-ios-foreground-catchup.png`；点击记录后 `xcrun simctl pbpaste` 输出 `ios-background-1794909673` | 通过 |
| IOS-06 | 分享草稿预填（已配对） | 向 App Group 写入 `share-draft.json`（`text=ios-share-draft-e2e-1120`，`id/createdAt` 唯一）；冷启动触发 `.active` 检查，输入框自动预填，截图 `tios09-ios-share-draft-prefill.png`；不自动发送。用例 4.476s 通过 | 通过 |
| IOS-07 | 未配对分享草稿保留 | 设置页「解除配对」→ 确认 → 回到「6 位配对码」页；App Group 草稿文件保留（未被消费），截图 `tios09-ios-unpaired.png`。用例 12.684s 通过 | 通过 |
| IOS-08 | 未配对分享 → 配对后预填 | 未配对启动进入配对页；输入新配对码完成配对后进入历史页，输入框预填 `ios-share-draft-e2e-1120`；等待 3 秒再次断言仍在输入框，未自动发送，截图 `tios09-ios-unpaired-draft-paired-prefill.png`。用例 11.590s 通过 | 通过 |
| IOS-09 | iPad 宽屏与 iPhone 窄屏 | iPad (A16) 竖屏使用左侧 NavigationRail（`设置` tab `minX < 240`），iPhone 16 使用底部导航（`minY > 300`）；外观页三段主题与三语切换不截断；旋转后页面状态保持。截图 `tios09-ipad-portrait-home.png`、`tios09-ipad-appearance-*.png`、`tios09-ipad-landscape-home.upright.png`、`tios09-ipad-landscape-appearance.upright.png`。用例 34.553s 通过 | 通过 |
| IOS-10 | 分享扩展注册与契约 | `pluginkit -m -p com.apple.share-services` 列出 `com.wangxingxing.clipbox.share(1.0.0)`；appex `NSExtensionActivationRule` 使用 `public.plain-text` 的 SUBQUERY；`NSExtensionPrincipalClass = ClipboxShareExtension.ShareViewController`；appex 内嵌于主 App `PlugIns/ClipboxShareExtension.appex` | 通过 |
| IOS-11 | 权限与传输声明 | 主 App Info.plist：`NSAppTransportSecurity.NSAllowsArbitraryLoads=true`（仅学习版）；`UIBackgroundModes` 不存在；`CFBundleURLSchemes = clipbox`；无通知/剪贴板权限声明；代码无 `beginBackgroundTask`、无后台刷新、无推送注册 | 通过 |

## 4. 服务端日志摘录（最终主流程）

```text
18:00:50.235 POST /api/v1/admin/pairing-codes 201
18:00:52.584 POST /api/v1/pair 201
18:00:52.618 GET  /api/v1/clips 200 deviceId=01M7MF7HK5QSJR78M82S9SQJD6
18:00:57.812 POST /api/v1/clips 201 deviceId=01M7MF7HK5QSJR78M82S9SQJD6   # ios-sent
18:00:58.012 POST /api/v1/pair 201                                        # 第二设备
18:01:01.410 POST /api/v1/clips 201 deviceId=01M7MF7PWTW0HA016MVCM7Y70S   # ios-received
18:01:09.541 GET  /api/v1/sync 101 latencyMs=16906                        # 后台断开 WS
18:01:13.085 POST /api/v1/clips 201 deviceId=01M7MF7PWTW0HA016MVCM7Y70S   # 后台期间第二设备写入
18:01:15.800 GET  /api/v1/clips 200 deviceId=01M7MF7HK5QSJR78M82S9SQJD6   # 回前台补拉
18:01:20.309 GET  /api/v1/sync 101 latencyMs=4501                         # 回前台重连 WS
```

## 5. 已知限制

1. **系统分享面板未自动化**：模拟器分享面板属于系统进程，XCUITest 不稳定。T-IOS-09 通过直接写入 App Group `share-draft.json` 验证主 App 消费路径，并以 `pluginkit` 注册、激活规则与扩展内嵌证明分享扩展存在；扩展 UI 在 T-IOS-08 已人工验证。M2 的 `simctl openurl` SpringBoard 确认框限制因冷启动触发 `.active` 检查而不再影响本场景。
2. **粘贴权限弹窗**：模拟器不出现真实 iOS 粘贴权限弹窗；显式「粘贴」入口、读取失败映射与不崩溃路径由代码与单测覆盖，真机行为未在本轮复现。
3. **后台真机行为**：以后台 4 秒窗口 + 服务端日志证明无后台请求与无通知；未做真机长时间后台测试。
4. **跨端接收代理**：iOS → 其他端由第二设备 REST 分页读取证明落库，未启动 Desktop / Android UI 同屏观察；Desktop / Android 双向已在 M3 实测。
5. **iPad 未配对**：iPad 布局用例在未配对空态执行，侧栏、设置、外观与旋转均已验证；配对后的双栏内容态未单独截图。
6. **旋转帧缓冲**：无头模拟器横屏截图仍为竖屏像素缓冲，导出后用 `sips -r -90` 生成 `.upright.png`，仅影响取证，不影响 App 旋转。
7. **测试脚本修正**：设置页行按钮把标题与副标题合并为一个无障碍标签，初次精确匹配失败；改为 `label BEGINSWITH` 后通过，产品代码未改动。

## 6. 结果汇总

5 个 XCUITest 用例全部通过：

| 用例 | 结果 | 耗时 |
| --- | --- | --- |
| `testPairingSendReceiveCopyPasteBackground` | passed | 36.255s |
| `testShareDraftPrefillWhenPaired` | passed | 4.476s |
| `testUnpairForDraftScenario` | passed | 12.684s |
| `testPairAfterUnpairedShareDraft` | passed | 11.590s |
| `testAdaptiveLayoutAppearanceAndRotation`（iPad A16） | passed | 34.553s |

---

## 2026-11-18 追加：第二设备取码方式变更（ADR-0006）

上文“通过管理接口配对第二设备”已废弃。T-IOS-11 起，UITest 先调用 `/api/v1/bootstrap` 初始化，再用设备 Bearer 调 `/api/v1/devices/me/pairing-code` 取码，不使用 `ADMIN_TOKEN` 或 `X-Admin-Token`。

T-IOS-11 实现说明：

- 测试进程首次 `POST /api/v1/bootstrap`（设备名 `XCUITest 宿主设备`）成为首台设备，返回的令牌缓存在测试 Runner 的 UserDefaults；之后各用例用该 Bearer 取码，令牌与配对码均不打印。
- 若服务端已初始化且 Runner 无缓存令牌，取码用例会失败；需用全新数据目录重启服务端后重跑（或保留同一模拟器的 Runner 数据）。
- 取码返回 429 时按 `Retry-After` 等待后重试；服务端数据被重置返回 401 时清除缓存令牌并重新 bootstrap。
- 主流程断言未变，仍覆盖配对、发送/接收、复制粘贴、前后台补拉、分享草稿、宽屏与三语。
