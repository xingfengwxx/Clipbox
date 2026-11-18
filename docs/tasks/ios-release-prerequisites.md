# iOS 真机发布前置清单（T-IOS-10）

- 日期：2026-11-18
- 范围：v1.0.0 只交付模拟器构建；本文列出真机安装、TestFlight 与 App Store 上架所需的账号、签名、资产、隐私与商店材料。
- 边界：本文只做盘点与步骤说明，不申请账号、不创建证书、不修改 `iosApp/`、`composeApp/iosMain/` 或任何资产。
- 决策依据：[ADR-0005](../adr/0005-v1-multiplatform-release.md)（iOS 真机发布延后）；模拟器验收见 [04-ios.md](04-ios.md) T-IOS-09。

## 1. 现状核对（2026-11-18）

| 项 | 当前状态 | 结论 |
| --- | --- | --- |
| Xcode | 27.2（Build 27B52） | 已具备归档工具链 |
| 签名身份 | `security find-identity -v -p codesigning` → `0 valid identities found` | 无有效证书 |
| 主应用 Bundle ID | `com.wangxingxing.clipbox`（`project.pbxproj`） | 已固定 |
| 分享扩展 Bundle ID | `com.wangxingxing.clipbox.share`（`project.pbxproj`） | 已固定 |
| App Group | `group.com.wangxingxing.clipbox`（主应用与扩展 entitlements 均已声明） | 工程已配置，开发者后台未注册 |
| Team | `project.pbxproj` 中无 `DEVELOPMENT_TEAM` | **缺失** |
| 签名方式 | `CODE_SIGN_STYLE = Automatic` | 有账号后由 Xcode 管理 |
| 版本 | `MARKETING_VERSION = 1.0.0`、`CURRENT_PROJECT_VERSION = 1` | 与三端一致；每次上传需递增构建号 |
| AppIcon | 无 `Assets.xcassets`，无 `ASSETCATALOG_COMPILER_APPICON_NAME` | **缺失** |
| 图标源文件 | `design/brand/exports/ios-1024.png`，1024×1024，无 alpha | 可直接导入，尚未进入工程 |
| 隐私清单 | 无 `PrivacyInfo.xcprivacy`（主应用与扩展均无） | **缺失** |
| ATS | `NSAllowsArbitraryLoads = true`（`iosApp/Info.plist`） | 学习版 HTTP 直连所需，上架需说明 |
| 出口合规 | Info.plist 未声明 `ITSAppUsesNonExemptEncryption` | **缺失** |
| 本地网络说明 | `NSLocalNetworkUsageDescription` 已配置 | 已具备 |
| 分享扩展 | `NSExtensionActivationRule` 仅接收 `public.plain-text` | 已具备 |
| App Store Connect | 无应用记录、无 TestFlight 构建 | **缺失** |
| 真机归档 | 未执行；仅在 iPhone 16 / iOS 27.2 模拟器构建通过 | **未验证** |

## 2. 账号与标识

1. **加入 Apple Developer Program**：个人或组织账号，年费以申请时官网为准；免费 Apple ID 不支持 App Group 与长期分发。
2. **记录 Team ID**：在开发者后台 Membership 页取得 10 位 Team ID；后续写入 Xcode 的 `DEVELOPMENT_TEAM`。
3. **注册 App ID**：
   - 主应用：`com.wangxingxing.clipbox`；
   - 分享扩展：`com.wangxingxing.clipbox.share`。
4. **注册 App Group**：`group.com.wangxingxing.clipbox`，并在两个 App ID 的 Capabilities 中启用 App Groups。
5. **确认能力边界**：本版不启用 Push Notifications、Background Modes、Associated Domains；分享扩展只需要 App Groups。

验收：开发者后台能看到两个 App ID 与一个 App Group，App Group 同时关联主应用与扩展。

## 3. 证书、描述文件与 Xcode 配置

1. 创建 **Apple Development** 证书用于真机调试；创建 **Apple Distribution** 证书用于 TestFlight / App Store。
2. 让 Xcode 自动管理签名：在 `iosApp` target 与 `ClipboxShareExtension` target 设置同一 `DEVELOPMENT_TEAM`，保持 `CODE_SIGN_STYLE = Automatic`。
3. 若改为手动签名，需为主应用与扩展分别准备 Provisioning Profile，并保证 App Group 能力出现在两者中。
4. 首次真机运行前，在 Xcode 的 Devices and Simulators 中注册设备；Ad Hoc 分发还需要单独的设备清单与 Ad Hoc Profile。
5. 验证命令（账号与证书就绪后）：

```bash
security find-identity -v -p codesigning
xcodebuild -project ClipboxClient/iosApp/iosApp.xcodeproj -scheme iosApp \
  -showBuildSettings | grep -E "DEVELOPMENT_TEAM|PRODUCT_BUNDLE_IDENTIFIER|CODE_SIGN_STYLE"
```

预期：至少一张有效签名身份；两个 target 的 Team 与 Bundle ID 正确。

## 4. 图标、隐私与合规资产

1. **AppIcon**：新建 `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`，导入 `design/brand/exports/ios-1024.png`（1024×1024，无 alpha），设置 `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon`；两个 target 共用或分别校验。
2. **PrivacyInfo.xcprivacy**：主应用与分享扩展各放一份，声明 `NSPrivacyTracking = false`、实际收集的数据类型（若数据只发送到用户自建服务器，按 App Store 问卷口径声明「不收集」）与 Required Reason API；以提交时 Xcode 检查结果为准。
3. **隐私政策 URL**：准备可公开访问的隐私政策页面，说明文本仅发送到用户自建服务器、无第三方统计与崩溃 SDK、诊断日志默认关闭。
4. **App Privacy 问卷**：在 App Store Connect 按实际行为填写；不得把用户自建服务器描述成开发者收集数据。
5. **出口合规**：在 Info.plist 增加 `ITSAppUsesNonExemptEncryption = false`；若后续引入自定义加密，需要重新评估。
6. **ATS 例外**：当前允许任意 HTTP 加载，仅适用于学习版。上架前必须在以下方案中做选择：
   - 为发布版提供 HTTPS（推荐，但超出 ADR-0003 学习版基线）；
   - 保留 HTTP，在审核备注中说明「仅连接用户自建学习服务器」，并接受被拒风险；
   - 只做 TestFlight 内测，不提交 App Store 公开审核。

## 5. App Store Connect 与 TestFlight

1. 创建 App 记录：名称「字匣」或「Theca」、主语言简中、Bundle ID `com.wangxingxing.clipbox`、SKU、免费价格与供应地区。
2. 准备三语商店素材：简中 / 繁中 / English 的描述、关键词、 promotional text、支持 URL、隐私政策 URL。
3. 截图：按提交时 App Store Connect 要求提供 iPhone 与 iPad 尺寸；至少覆盖历史页、配对页、分享页与设置页。
4. 审核备注与演示环境：应用必须依赖用户自建服务器，审核员无法自行配对；需提供临时审核服务器地址与配对码，或在备注中给出可复现步骤。**没有可用演示环境时，公开审核大概率无法通过。**
5. 上传构建：Xcode Archive → Distribute App → App Store Connect；首次上传后等待处理完成。
6. TestFlight：内部测试（最多 100 名团队成员）通常无需 Beta App Review；外部测试需要 Beta App Review。先内部验证，再决定是否外部测试或提交正式审核。
7. 若公开上架：填写年龄分级、类别（工具 / 效率）、版权与审核联系方式，提交后跟进审核问题。

## 6. 版本与归档命令

- `MARKETING_VERSION` 与 Android、Desktop、官网保持一致（当前 `1.0.0`）； `CURRENT_PROJECT_VERSION` 每次上传必须递增。
- 归档命令（账号、Team 与证书就绪后执行）：

```bash
cd ClipboxClient
./gradlew :composeApp:linkReleaseFrameworkIosArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/iosApp.xcarchive archive
```

- 导出 IPA 需在 Xcode Organizer 或 `xcodebuild -exportArchive` 中选择 App Store Connect / Ad Hoc 方式；导出前确认两个 target 的签名均通过。
- 当前 `linkReleaseFrameworkIosArm64` 与真机归档均未验证；模拟器使用 `linkDebugFrameworkIosSimulatorArm64`，两者不能互相替代。

## 7. 真机验收矩阵（发布前）

| 项 | 验证内容 |
| --- | --- |
| 系统版本 | iOS 16、当前最新 iOS 各一台；iPhone 与 iPad 各一台 |
| 配对 | 输入自建服务器地址与配对码，重启后仍保持配对 |
| 同步 | 前台收发；切后台再回前台补拉；断网重连重放 |
| 分享扩展 | 从 Safari / 备忘录分享纯文本，扩展写入草稿后主应用可见 |
| 剪贴板 | 前台复制、粘贴；确认无后台剪贴板与轮询 |
| 分享页 | 生成链接并在 Safari 打开，检查过期与一次性语义 |
| 主题与语言 | 简中 / 繁中 / English，浅色 / 深色，iPad 分栏 |
| 隐私 | 诊断日志默认关闭；开启后仅本机系统日志 |

## 8. 缺口汇总与下一步

| 缺口 | 阻塞级别 | 需要谁处理 |
| --- | --- | --- |
| Apple Developer Program 与 Team ID | 阻塞签名与 TestFlight | 用户 |
| App ID、App Group 后台注册 | 阻塞真机运行分享扩展 | 用户 |
| Development / Distribution 证书与 Profile | 阻塞归档 | 用户 + Xcode |
| `DEVELOPMENT_TEAM` | 阻塞真机构建 | Agent（账号就绪后） |
| AppIcon 资产目录 | 阻塞上传 | Agent |
| PrivacyInfo.xcprivacy 与隐私政策 URL | 阻塞审核 | Agent + 用户 |
| ATS 方案（HTTPS 或审核说明） | 阻塞公开上架 | 用户决策 |
| 出口合规声明 | 阻塞上传 | Agent |
| App Store Connect 记录、截图、三语文案 | 阻塞提交 | 用户 + Agent |
| 审核演示服务器与配对码 | 阻塞公开审核 | 用户 |
| 真机归档与验收 | 阻塞发布 | 用户设备 + Agent |

建议顺序：账号与后台标识 → 证书与 Team → AppIcon / 隐私清单 / 合规声明 → 真机归档与验收 → TestFlight 内测 → 决定是否公开上架。

## 9. 明确不在本卡范围

- 不申请 Apple Developer Program、不创建证书或描述文件。
- 不新增或修改 iOS 资产、Xcode 工程、签名配置与网络基线。
- 不导出 IPA、不上传 TestFlight、不提交 App Store 审核。
- 不改变「无后台剪贴板、无推送、无第三方统计 / 崩溃 SDK」的产品边界。
