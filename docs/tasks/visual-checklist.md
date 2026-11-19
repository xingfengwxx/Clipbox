# T-UI-19 视觉与可访问性核对

日期：2026-11-17
构建：`:composeApp:assembleDebug`（debug，版本 1.0.0）
视口：Android 412×915（`wm size 412x915` + `wm density 160`，核对后已 reset 回 1080×2400 @420dpi）；Desktop 窗口 1000×700 与 640×480。
截图目录：`docs/evidence/ClipboxM3/`（不提交二进制）。设计基准：`design/README.md` 屏幕地图与 tokens。

## 1. 静态规则核对

### 1.1 对比度（WCAG 2.1，正文阈值 4.5:1）

浅色 tokens（`ClipboxTokens.Light`）：

| 组合 | 比值 | 组合 | 比值 |
| --- | --- | --- | --- |
| ink / bg | 15.73 | ink3 / bg | 5.15 |
| ink / panel | 17.01 | ink3 / panel | 5.57 |
| ink2 / panel | 7.12 | ink3 / panel2 | 4.70（最低） |
| accent / panel2 | 4.84 | accent / bg | 5.31 |
| ok / bg | 5.57 | warn / bg | 6.31 |
| danger / bg | 6.27 | danger / panel | 6.78 |
| 按钮字 onAccent / accent | 5.74 | | |

深色 tokens（`ClipboxTokens.Dark`）：

| 组合 | 比值 | 组合 | 比值 |
| --- | --- | --- | --- |
| ink / bg | 15.03 | ink3 / bg | 5.98 |
| ink / panel | 14.15 | ink3 / panel | 5.63 |
| ink2 / panel | 7.96 | ink3 / panel2 | 5.20（最低） |
| accent / bg | 6.69 | accent / panel2 | 5.81 |
| ok / bg | 10.47 | warn / bg | 9.15 |
| danger / bg | 7.47 | danger / panel | 7.03 |
| 按钮字 onAccent / accent | 6.69 | | |

结论：浅色最低 4.70、深色最低 5.20，全部正文/标签组合 ≥4.5:1，无红灯。浅色 ink3/panel2 的 4.70 仅用于工具页次级标签，仍达标。

### 1.2 字号

- 正文：`bodyLarge` 16sp、`bodyMedium` 15sp、`bodySmall` 14sp（最小 14sp）。
- 标签与元数据：`labelLarge` 14sp、`labelMedium`/`labelSmall` 12sp、`mono` 12sp（最小 12sp）。
- 结论：满足「正文 ≥14、标签 ≥12」。

### 1.3 触控目标

本次修复后全部可点击目标 ≥48dp：

| 位置 | 原状 | 修复 |
| --- | --- | --- |
| 历史发送器「粘贴」「复制而非发送」「发送」 | 44dp 高 | 48dp |
| 分享结果页「返回」 | 44dp 高 | 48dp |
| 同步状态条整行（重试/状态点击） | 行高约 40dp | `heightIn(min = 48.dp)` |
| 设置「立即同步」 | 文本按钮约 24dp 高 | 48dp 高 Box + 边框 |
| 历史搜索「清空」 | 纯文本点击区 | 48dp 高 Box |
| 记录行整行复制 | 无动作播报 | `clickable(onClickLabel = 复制)` |
| 记录行置顶/更多图标按钮 | 已 48dp | 保持，含 `contentDescription` |
| 工具箱卡、工具操作按钮、配对按钮、外观返回 | 已 ≥48dp | 保持 |

### 1.4 TalkBack 语义

- 记录行整行点击播报「复制」动作（`onClickLabel`），行内「置顶 / 取消置顶 / 更多」均有 `contentDescription`。
- 记录行文本顺序为设备 → 相对时间 → 正文 → 字节数，待同步/失败角标为文本节点，TalkBack 可读完一条记录的关键信息。
- 图标类按钮均有中文/英文/繁中三语文案，无未命名按钮。

## 2. Android 24 屏核对

| 编号 | 结论 | 截图 | 说明 / 偏差 |
| --- | --- | --- | --- |
| S01 历史默认 | 通过 | `v19-s01-history.png`、`v19-s01-history-scroll.png` | 置顶区 + 全部区、发送器字节数、同步状态、行内操作齐全；搜索与列表层级与设计一致 |
| S02 未配对空态 | 通过 | `v19-00-home-412.png` | 线稿插画、还没有记录、去配对；工具箱/设置入口可用 |
| S03 搜索结果 | 通过 | `v19-s03-search.png` | 300ms 防抖后过滤，结果计数「找到 3 条」，清空可见 |
| S03B 搜索无结果 | 通过 | `v19-s03b-noresult.png` | 空态文案「没有匹配的记录 / 换个关键词试试」+ 清空 |
| S04 离线与失败 | 部分通过 | `v19-s04-offline.png` | 停服后发送：顶部「离线 + 重试」、记录「待同步」角标、本地记录照常可读；重试补发后恢复「已同步」。失败角标未单独构造，见偏差 D3 |
| S05 超限 70 KiB | 未取证 | 无 | 按用户指示跳过超限长文本注入，避免循环注入百 KiB 文本；见偏差 D1 |
| S06 记录操作面板 | 通过 | `v19-s06-actions.png` | 复制 / 置顶 / 生成分享 / 删除四操作；已同步记录分享可用 |
| S07 分享选择有效期 | 通过 | `v19-s07-share-ttl.png` | 1 小时 / 24 小时（默认）/ 7 天 / 阅后即焚 |
| S08 分享已生成 | 通过 | `v19-s08-share-created.png` | 完整 URL、过期时间、复制链接、换有效期 |
| S09 删除确认 | 通过 | `v19-s09-delete-confirm.png` | 「删除后无法恢复，所有设备都会移除」，取消 / 危险色删除 |
| S10 配对 | 通过 | `v19-s10-pairing.png` | 服务器地址、6 位码、设备名、未加密提示；空码时提交禁用 |
| S11A 配对码无效/过期 | 通过（文案差异） | `v19-s11a-pair-expired.png` | 提交已使用码 → 401 →「配对码已被使用，请重新获取」；设计稿写「已过期」，见偏差 D2 |
| S11B 配对冷却 429 | 通过 | `v19-s11b-pair-cooldown.png` | 连续提交触发 429，按钮禁用并显示「28 秒后可重试」倒计时 |
| S12 设置 | 通过 | `v19-s12-settings-top.png`、`v19-s12-settings-bottom.png` | 服务器（地址/未加密/状态/最近同步/立即同步）、设备、主题、语言、诊断（含脱敏说明）、关于、危险区齐全；Android 不显示桌面剪贴板区 |
| S13 外观与语言 | 通过 | `v19-s13-appearance.png` | 主题三段（跟随系统/浅色/深色）、实时预览、语言三段（简/繁/EN），立即生效 |
| S14 解除配对确认 | 通过 | `v19-s14-unpair-confirm.png` | 「本机令牌、历史、待发队列与游标将被清空；服务端与其他设备的数据保留」 |
| S15 工具箱入口 | 通过 | `v19-s15-toolbox.png` | 5 张工具卡 + 本地标签；未配对可用 |
| S16 JSON 合法 | 通过 | `v19-s16-json-valid.png` | 2 空格格式化、右上「JSON 合法」、复制/清空 |
| S16B JSON 行列错误 | 通过 | `v19-s16b-json-error.png` | 「失败 / 第 1 行第 17 列：缺少逗号或右括号」 |
| S17 Base64 | 通过 | `v19-s17-base64.png` | 标准字母表带填充输出、URL-safe 开关、编码/解码 |
| S18 URL 编解码 | 通过 | `v19-s18-url.png` | 组件/整串、`+ = 空格` 开关；`a b&c=d` → `a%20b%26c%3Dd` |
| S19 时间戳 | 通过 | `v19-s19-timestamp.png` | 填入当前时间、自动识别秒、本地/UTC/ISO 8601 三行输出 |
| S20 正则测试 | 通过 | `v19-s20-regex.png` | 匹配高亮、2 处匹配、编号分组 `1=12 2=34`、三个标志开关 |
| S21 新记录提示 | 通过 | `v19-s21-new-clip.png` | 工具箱页前台收到外部设备记录 → 底部 Snackbar「收到新记录 / 查看」 |

## 3. Desktop 核对

| 编号 | 结论 | 截图 | 说明 |
| --- | --- | --- | --- |
| D01 历史 1000×700（浅色） | 通过 | `v19-d01-1000.png` | NavigationRail、发送器、置顶/全部、状态条、行内操作；布局与设计稿一致 |
| D01 历史 1000×700（深色） | 通过 | `v19-d01-1000-dark.png` | 跟随 macOS 系统深色，层级与对比度正常 |
| D01 历史 640×480 | 通过 | `v19-d01-640.png` | 最小窗口无横向截断，Rail 与内容自适应 |
| D02 设置 1000×700 | 通过 | `v19-d02-1000.png` | 桌面剪贴板区（自动复制开、自动发送关）+「开启自动发送？」隐私确认弹窗（取消 / 我已了解，开启） |
| D02 设置 640×480 | 通过 | `v19-d02-640.png` | 最小窗口下设置分组可滚动，无截断 |

## 4. 三语与深浅色抽查

| 项 | 截图 | 结论 |
| --- | --- | --- |
| 历史 · 深色 | `v19-s01-dark.png` | 层级、对比度保持；置顶/状态色不丢失 |
| 设置 · 深色 | `v19-s12-dark.png` | 分组标题、危险色、按钮可读 |
| 外观 · 深色 | `v19-s13-appearance-dark.png` | 选中段与预览卡正常 |
| 历史 · English | `v19-s01-en.png` | 长按钮/标签不截断 |
| 设置 · English | `v19-s12-settings-en.png` | 长英文说明换行正常 |
| 外观 · English | `v19-s13-appearance-en.png` | 三段控件文案完整 |
| 工具箱 · English | `v19-s15-toolbox-en.png` | 卡片副标题不截断 |
| 历史 · 繁體中文 | `v19-s01-zhtw.png` | 繁體字不截断 |
| 外观 · 繁體中文 | `v19-s13-appearance-zhtw.png` | 三段控件文案完整 |

## 5. 本次修复

| 文件 | 改动 |
| --- | --- |
| `ui/history/Composer.kt` | 「粘贴」「复制而非发送」「发送」高度 44→48dp |
| `ui/share/ShareSheet.kt` | 分享结果页「返回」44→48dp |
| `ui/components/SyncStatusBar.kt` | 状态条整行 `heightIn(min = 48.dp)` |
| `ui/settings/SettingsScreen.kt` | 「立即同步」改为 48dp 高可点击 Box（保持原视觉） |
| `ui/history/HistoryScreen.kt` | 搜索「清空」改为 48dp 高可点击 Box |
| `ui/components/ClipRow.kt` | 行点击增加 `onClickLabel`（复用「复制」文案） |
| `ui/navigation/ClipboxNavHost.kt` | 工具详情参数读取改用 KMP `androidx.savedstate.read`，修复 Desktop 目标编译失败（Android 专用的 `getString` 扩展不可用）；由本卡 Desktop 截图暴露 |

未改动真实数据逻辑，未引入截图自动化。

## 6. 偏差与理由

- **D1 S05 未取证**：用户明确要求停止约 100 KiB 文本循环注入并跳过超限手工测试。90% 预警与超限禁用逻辑由 `TextSizeTest` 与发送禁用路径覆盖；M3 Gate 登记该验证缺口。
- **D2 S11A 文案**：设计稿标题为「码已过期」，实际复现用已使用码，401 映射到「配对码已被使用，请重新获取」（`pair_error_code_used`）。过期码需等待 10 分钟或改服务端时钟，成本高；两者同属 401 分支、同一 UI 形态。客户端不能生成配对码，故无「重新生成」按钮，提示到服务端管理端获取（`pair_expired_hint` 已备）。
- **D3 S04 失败角标未构造**：服务端无吊销设备/强制失败的公开接口，网络失败只会产生「待同步」。失败态的展示与优先级由 `SyncStatusTest`（Failed 优先于 Pending）与 `ClipRow` 失败角标分支覆盖；离线+待同步+重试已截图取证。
- **D4 Desktop 主题**：主核对图使用手动「浅色」；深色图来自 macOS 系统深色（跟随系统）。两种均核对，无层级丢失。
- **D5 S02 截图来源**：使用首次启动（配对前）的空态；解绑后空态复用同一布局，S14 已验证解绑回到配对页。
- **D6 字体渲染**：Android 使用 Roboto/Noto，设计稿以 macOS 字体栈绘制，字形宽度略有差异；属平台渲染差异，不计为偏差。
- **D7 窗口边框**：Desktop 截图包含 macOS 标题栏（Theca），不属于页面 UI。

## 7. 验收命令

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug        # BUILD SUCCESSFUL
adb exec-out screencap -p > /tmp/clipbox-history.png   # 已生成
```

模拟器视口已恢复：`wm size reset` + `wm density reset`（1080×2400 @420dpi）。

## 8. T-IOS-08 iOS 宽屏、主题与三语核对

日期：2026-11-17
环境：Xcode 27.2 / iOS 27.2 模拟器；iPhone 16（393×852 pt）、iPad (A16)（820×1180 pt）。两台模拟器均先卸载 App 再安装，保证首启为简体中文、跟随系统主题。
截图目录：`docs/evidence/ClipboxM4/`（`tios08-*.png`，不提交二进制）。

### 8.1 布局与旋转

| 设备 / 形态 | 结论 | 截图 | 说明 |
| --- | --- | --- | --- |
| iPhone 16 竖屏 | 通过 | `tios08-iphone-portrait-home.png` | <600dp 使用底部三栏；历史空态、搜索与「去配对」完整 |
| iPhone 16 横屏 | 通过 | `tios08-iphone-landscape-home.upright.png` | 宽度 ≥600dp 切换左侧 NavigationRail；历史页状态保持 |
| iPad (A16) 竖屏 | 通过 | `tios08-ipad-portrait-home.png` | 左侧 200dp NavigationRail；内容区不再是底部导航 |
| iPad (A16) 横屏 | 通过 | `tios08-ipad-landscape-home.upright.png` | Rail 保留；正文宽度受 880dp 上限约束，左右留白，列表不横向拉伸 |
| 旋转状态保持 | 通过 | `tios08-iphone-landscape-appearance.upright.png`、`tios08-ipad-landscape-appearance.upright.png` | 外观页旋转后仍停留原页，主题与语言选择未丢失 |

横屏说明：无头模拟器的 `XCUIScreen` 像素缓冲保持竖屏尺寸、内容旋转 90°；截图原始文件保留，另生成 `.upright.png`（逆时针旋转 90°，仅纠正方向，不修改像素内容）用于阅读。

### 8.2 主题与三语

| 项 | 截图 | 结论 |
| --- | --- | --- |
| 简体中文 · 浅色 | `tios08-iphone-appearance-zh-hans.png`、`tios08-ipad-appearance-zh-hans.png` | 主题/语言三段完整，预览卡正常 |
| English · 浅色 | `tios08-iphone-appearance-en.png`、`tios08-ipad-appearance-en.png` | 英文长标题与说明不截断 |
| English · 深色 | `tios08-iphone-appearance-en-dark.png`、`tios08-ipad-appearance-en-dark.png` | 深色选中段、边框、说明文字对比度正常；tokens 对比度沿用 §1.1 结论 |
| 繁體中文 · 深色 | `tios08-iphone-appearance-zh-hant-dark.png`、`tios08-ipad-appearance-zh-hant-dark.png` | 繁體字完整，无截断或重叠 |

### 8.3 分享扩展三语

- `ClipboxShareExtension.appex` 内含 `zh-Hans.lproj`、`zh-Hant.lproj`、`en.lproj` 三套 `Localizable.strings`，各 10 个 `share_*` key；扩展不再硬编码中文。
- 主 App 语言变化时通过 `IosAppLanguageStore` 写入 App Group `group.com.wangxingxing.clipbox` 的 `appLanguage`；XCUITest 切换语言期间轮询观察到值由 `zh-Hans` 变为 `zh-TW`，证明镜像随切换写入。
- 扩展读取同一 App Group 值并映射到 `.lproj`；分享面板真实交互放到 T-IOS-09 验证。

### 8.4 可访问性抽查

- XCUITest 语义树中底部导航项为 130×64pt，Rail 行高 48pt，历史页顶部设置图标按钮 48×48pt，满足 ≥48dp。
- 记录行、置顶/更多、复制/发送等标签沿用 T-UI-19 的合并语义与 `contentDescription`；VoiceOver 可读出设备、相对时间、正文与字节数。

### 8.5 本卡偏差

- **机型**：任务卡写 iPad (10th generation)，本机不可用；按用户决策使用 iPad (A16)。
- **断点**：任务卡写 ≥840dp，按用户决策沿用架构与实现的 600dp。
- **横屏截图方向**：见 8.1，原始截图 + `.upright.png` 并存。
- **iOS 日志 API**：`IosLogSink` 使用 `NSLog` 而非 `os_log`（Kotlin/Native 绑定限制）；仅写系统日志，不落盘、不上传。本卡同时修复了 `NSLog("%@", Kotlin String)` 可变参数桥接导致的启动崩溃。
- **扩展 UI 实机分享面板**：本卡验证资源打包与语言镜像，真实分享流程由 T-IOS-09 覆盖。

### 8.6 验收记录

```bash
cd ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # BUILD SUCCESSFUL
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPad (A16)' build   # BUILD SUCCEEDED
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build      # BUILD SUCCEEDED
xcodebuild ... -only-testing:iosAppUITests/ClipboxUITests/testAdaptiveLayoutAppearanceAndRotation test
# iPhone 16：1 passed / 0 failures / 32.434s
# iPad (A16)：1 passed / 0 failures / 35.163s
```

---

## 2026-11-18 追加（ADR-0006）

上文的“客户端不能生成配对码、无重新生成按钮、提示到服务端管理端获取”已失效。当前设置页提供「配对新设备」弹层，含 6 位码、倒计时、重新生成与复制；配对页提供空库初始化入口。视觉样式沿用既有设计语言，待 T-UI-21 补齐截图后另行登记偏差。
