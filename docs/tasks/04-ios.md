# iOS 任务卡（T-IOS）

工程：`ClipboxClient/iosApp/`（SwiftUI 宿主 + Share Extension）与 `composeApp` 的 iOS targets。
Bundle ID：`com.wangxingxing.clipbox`；最低 iOS 16.0；只交付模拟器构建与验收。
当前环境**未安装 Xcode**，T-IOS-01 是用户动作卡：Agent 给出命令与验证标准，用户执行，Agent 复核。

---

## T-IOS-01 安装 Xcode 与打通 KMP 框架链接

| 字段 | 值 |
| --- | --- |
| 里程碑 | M0 |
| 前置 | T-CORE-01（iOS targets 已配置） |
| 需求 | REQ-PLAT-008 |
| 预估 | 0.5 天（含下载安装，用户执行） |
| 执行者 | **用户**执行安装；Agent 提供命令、验证结果 |

**目标**：本机具备完整 Xcode 工具链，Gradle 能链接 iOS 模拟器 framework。

**必读**：`docs/客户端技术架构.md` §10 平台差异矩阵；`docs/功能需求文档.md` REQ-PLAT-008。

**涉及路径**：系统环境；`ClipboxClient/composeApp/build.gradle.kts` 的 iOS targets。

**用户执行**

```bash
# 1. 从 App Store 安装 Xcode（或开发者下载），完成后：
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -runFirstLaunch
xcodebuild -version
xcodebuild -license accept

# 2. 安装至少一个 iOS 16+ 模拟器运行时（Xcode Settings → Platforms）
xcrun simctl list runtimes
xcrun simctl list devices available
```

**Agent 复核与后续**

```bash
cd ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
ls -la composeApp/build/bin/iosSimulatorArm64/debugFramework/
```

预期：`xcodebuild -version` 输出版本号；`simctl` 至少有一个可用 iPhone 模拟器；framework 链接成功并生成 `.framework`。

**DoD**

- [ ] `xcode-select -p` 指向完整 Xcode，不是 Command Line Tools。
- [ ] 至少一个 iOS 16+ 运行时可用。
- [ ] framework 可被 Xcode 工程引用。

**禁止**

- 不要用 Command Line Tools 冒充完整 Xcode。
- 不要在未安装 Xcode 时继续 iOS UI 卡。

---

## T-IOS-02 iOS 宿主工程与 ATS 配置

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-IOS-01 |
| 需求 | REQ-PLAT-008、REQ-NFR-001 |
| 预估 | 1 天 |

**目标**：创建 `iosApp` Xcode 工程，SwiftUI 壳承载 Compose 视图；允许学习版 HTTP 访问。

**必读**：`docs/客户端技术架构.md` §4、§6.7.4、§10；`docs/adr/0003-http-ip-learning-deployment.md`。

**涉及路径**：`ClipboxClient/iosApp/iosApp.xcodeproj`、`iosApp/iOSApp.swift`、`iosApp/Info.plist`、`iosApp/ContentView.swift`。

**实现要点**

- Xcode 工程：target `iosApp`，Bundle ID `com.wangxingxing.clipbox`，最低部署 16.0，仅 iPhone / iPad。
- SwiftUI `@main App` → `UIViewControllerRepresentable` 承载 `MainViewControllerKt.MainViewController()`。
- Info.plist：
  - `NSAppTransportSecurity` → `NSAllowsArbitraryLoads=true`（仅学习版，注释说明原因）。
  - 如需访问局域网 IP，增加 `NSLocalNetworkUsageDescription`。
  - 不申请通知、后台模式、剪贴板相关权限。
- 构建脚本调用 Gradle 生成 framework（embedAndSignAppleFrameworkForXcode 或模板方式），模拟器可链接。
- 不配置真机签名要求；模拟器构建即可。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：构建成功；模拟器启动后显示 Compose 占位界面；无 ATS 拦截错误。

**DoD**

- [ ] Bundle ID 与最低版本正确。
- [ ] 无后台模式与通知权限。
- [ ] ATS 配置有注释说明仅学习版使用。

**禁止**

- 不要申请真机证书或描述文件。
- 不要引入 CocoaPods / SPM 依赖。

---

## T-IOS-03 iOS 共享 UI 接入

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-IOS-02、T-UI-01 |
| 需求 | REQ-PLAT-008、REQ-SET-005/009 |
| 预估 | 1 天 |

**目标**：iOS 上运行与 Android / Desktop 相同的历史、配对、导航骨架，主题与语言可切换。

**必读**：`docs/客户端技术架构.md` §7.1、§7.6、§7.7、§10；`design/README.md` 已知假设。

**涉及路径**：`composeApp/src/iosMain/kotlin/.../MainViewController.kt`、`.../IosDeviceInfo.kt`、`.../IosAppLifecycle.kt`、`iosApp/`。

**实现要点**

- `MainViewController` 返回 `ComposeUIViewController { App() }`；宿主不重复实现导航。
- 设备名默认 `iOS · <机型>`；提供 iOS `DeviceInfo` actual。
- 安全区与状态栏：Compose 内容避让 Home Indicator 与刘海；窗口背景使用 tokens。
- 主题跟随系统；语言切换复用共享 UI。
- 模拟器上使用 `http://localhost:8080` 配对本地服务端（无需 `adb reverse`）。
- 返回手势、键盘避让可用。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：模拟器完成配对、发送、列表展示；主题与语言切换生效。

**DoD**

- [ ] iOS 无独立页面实现（除宿主壳）。
- [ ] 安全区无遮挡。
- [ ] 未配对空态可进入配对页。

**禁止**

- 不要为 iOS 复制一套 ViewModel。
- 不要在 SwiftUI 层写业务逻辑。

---

## T-IOS-04 前后台同步生命周期

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-IOS-03、T-CORE-09、T-CORE-15 |
| 需求 | REQ-RECV-004/008、REQ-PLAT-009 |
| 预估 | 0.5–1 天 |

**目标**：iOS 前台建立 WS，后台断开；回前台先 REST 补拉再重连；无后台同步。

**必读**：`docs/客户端技术架构.md` §6.4、§10；`docs/功能需求文档.md` REQ-PLAT-009。

**涉及路径**：`composeApp/src/iosMain/kotlin/.../IosAppLifecycle.kt`、宿主 `iOSApp.swift` 的 `scenePhase`。

**实现要点**

- `scenePhase == .active`：先 `syncNow()` 补拉，再连接 WS。
- `.background` / `.inactive`：断开 WS，停止重连，不执行后台任务。
- 通知权限、Background Modes 一律不配置。
- 生命周期回调可重复触发，需幂等。
- 回到前台后新记录出现在列表，不弹通知。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：模拟器切后台再回前台，列表补齐缺失记录；后台期间无网络连接。

**DoD**

- [ ] 无 `beginBackgroundTask`、推送或后台刷新。
- [ ] 回前台不重复建立多条 WS。
- [ ] 补拉失败不阻塞 UI。

**禁止**

- 不要在后台写剪贴板或同步。
- 不要注册远程推送。

---

## T-IOS-05 剪贴板显式操作与权限提示

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-IOS-03、T-CORE-12 |
| 需求 | REQ-PLAT-009、REQ-RECV-005 |
| 预估 | 0.5–1 天 |

**目标**：iOS 只在用户点击时写入剪贴板；读取只由用户显式发起；拒绝权限不崩溃。

**必读**：`docs/客户端技术架构.md` §6.7；`docs/功能需求文档.md` REQ-PLAT-009。

**涉及路径**：`composeApp/src/iosMain/kotlin/.../IosClipboardService.kt`。

**实现要点**

- `writeText`：用户点击「复制」时写 `UIPasteboard.general.string`。
- `readText`：仅在用户点击「从剪贴板粘贴」时读取；不轮询、不在启动 / 前台读取。
- 捕获粘贴权限拒绝或读取失败，返回可重试错误；UI 提示「请重试或长按输入框手动粘贴」。
- 收到远端记录不自动写剪贴板。
- 不申请任何剪贴板相关权限声明（系统自动提示）。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

预期：模拟器点击复制后可粘贴到其他应用；拒绝权限路径有提示、无崩溃。

**DoD**

- [ ] 无自动读取路径。
- [ ] 无后台剪贴板代码。
- [ ] 错误提示进入多语言资源。

**禁止**

- 不要用 `UIPasteboard.detectPatterns` 等后台探测能力。
- 不要为了「体验」在启动时预读剪贴板。

---

## T-IOS-06 分享扩展与 App Group 草稿

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-IOS-02、T-CORE-01 |
| 需求 | REQ-SEND-005、REQ-PLAT-008 |
| 预估 | 1 天 |

**目标**：系统分享面板可从其他应用把 text/plain 交给 Clipbox 分享扩展。

**必读**：`docs/客户端技术架构.md` §6.7.4、§8.7；`docs/功能需求文档.md` SEND 域。

**涉及路径**：`iosApp/ClipboxShareExtension/ShareViewController.swift`、`Info.plist`、`ClipboxShareExtension.entitlements`、主 App entitlements。

**实现要点**

- 新增 Share Extension target，Bundle ID `com.wangxingxing.clipbox.share`；只接受 `public.plain-text`。
- 主 App 与扩展共享 App Group（建议 `group.com.wangxingxing.clipbox`，以 Xcode 实际配置为准）。
- 扩展读取 `NSExtensionItem` 文本，写入 App Group 容器 `share-draft.json`（正文 + 时间戳），不访问网络、数据库、设备令牌。
- 写入成功后唤起主 App `clipbox://share`；若扩展环境不允许打开 URL，则提示用户返回 Clipbox。
- 扩展 UI 极简：显示「已发送到 Clipbox」或失败原因；无品牌墙。
- 配置 URL Scheme `clipbox` 到主 App Info.plist。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：Safari / 备忘录分享文本时出现 Clipbox；分享后主 App 打开并预填 / 写入草稿。

**DoD**

- [ ] 扩展不包含网络代码。
- [ ] App Group 读写路径正确。
- [ ] 扩展失败有明确提示。

**禁止**

- 不要在扩展中读取剪贴板或后台运行。
- 不要在扩展中保存令牌。

---

## T-IOS-07 草稿消费、未配对暂存与深链

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-IOS-06、T-UI-03 |
| 需求 | REQ-SEND-005、REQ-PAIR-008 |
| 预估 | 0.5–1 天 |

**目标**：主 App 启动 / 回到前台时消费分享草稿；未配对时保留，配对后预填或发送。

**必读**：`docs/客户端技术架构.md` §6.7.4；`docs/功能需求文档.md` 未配对分享场景。

**涉及路径**：`composeApp/src/iosMain/kotlin/.../ShareDraftStore.kt`、`.../IosAppLifecycle.kt`、历史页 ViewModel。

**实现要点**

- 打开 URL / 回到前台时检查 App Group 草稿；读取后删除文件（一次性消费）。
- 已配对：预填发送器并聚焦，不自动发送（避免误发）；用户确认后发送。
- 未配对：草稿保留在本地（内存 / App Group），进入配对页；配对成功后回到历史页预填。
- 草稿只保留最近一条；新草稿覆盖旧草稿并提示。
- 处理重复深链：同一草稿不重复预填。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：已配对时分享后文本出现在输入框；未配对时配对后仍能看到文本。

**DoD**

- [ ] 草稿消费后不残留。
- [ ] 不自动发送。
- [ ] 重复深链不重复写入。

**禁止**

- 不要用剪贴板作为扩展与主 App 的传输通道。
- 不要在未配对时丢弃草稿。

---

## T-IOS-08 iPad 宽屏、主题与多语言适配

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-IOS-07、T-UI-09、T-UI-10 |
| 需求 | REQ-PLAT-008、REQ-SET-005/009、REQ-NFR-014/016 |
| 预估 | 0.5–1 天 |

**目标**：iPad 宽屏使用侧边导航，内容不拉伸；主题、三语、可访问性全部可用。

**必读**：`design/README.md` 已知假设；`docs/客户端技术架构.md` §7.1、§7.6、§7.7。

**涉及路径**：共享导航布局、`iosApp` 设备配置、资源文件。

**实现要点**

- 宽度 ≥840dp 使用 NavigationRail；内容最大宽度受控，列表不横向拉伸。
- iPhone 窄屏保持底部导航；旋转不丢状态。
- 主题跟随系统；三语切换不截断（重点检查英文长按钮与繁体字）。
- 触控目标 ≥48dp；TalkBack 对应 VoiceOver 标签可读。
- 模拟器分别验收 iPhone 与 iPad。

**验收**

```bash
cd ClipboxClient
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPad (10th generation)' build
```

预期：iPad 显示侧边导航；iPhone 显示底部导航；旋转后状态保持。

**DoD**

- [ ] 两种设备形态均截图留档。
- [ ] 无截断与重叠。
- [ ] 深色模式无对比度问题。

**禁止**

- 不要为 iPad 单独复制页面。
- 不要锁定横竖屏。

---

## T-IOS-09 模拟器构建与 iOS 验收

| 字段 | 值 |
| --- | --- |
| 里程碑 | M4 |
| 前置 | T-IOS-08、T-S-11 |
| 需求 | iOS 全部 REQ、AC 中 iOS 相关项 |
| 预估 | 1 天 |

**目标**：iOS 模拟器完整验收：配对、发送、接收、复制、分享扩展、前后台、主题、语言。

**必读**：`docs/tasks/README.md` M4 Gate；`docs/功能需求文档.md` §5。

**涉及路径**：不新增业务；产出 `docs/tasks/e2e-ios.md` 记录。

**实现要点**

- 启动本地服务端；模拟器配对 `http://localhost:8080`。
- 验证：App 输入发送 → Desktop / Android 收到；其他端发送 → iOS 回前台看到并可复制；分享扩展 → 预填；未配对分享 → 配对后预填；后台无同步无通知；主题 / 三语 / iPad。
- 构建命令与截图留档；记录已知限制（模拟器无真实粘贴权限弹窗等）。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
```

预期：构建成功；`e2e-ios.md` 全部场景通过或有明确限制说明。

**DoD**

- [ ] 模拟器构建可复现。
- [ ] 分享扩展与深链可用。
- [ ] 无后台权限与推送声明。

**禁止**

- 不要把模拟器限制当成功能缺失而不记录。
- 不要为通过验收修改服务端契约。
