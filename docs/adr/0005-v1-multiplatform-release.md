# v1.0.0 扩展桌面发布范围并破例引入 release-only CI

**Status**: accepted

**Date**: 2026-11-18

## Context

Clipbox v1.0.0 的冻结交付范围只包含 Android APK、macOS DMG 与 iOS 模拟器构建，明确排除 Windows / Linux 包与 iOS 真机签名（`docs/tasks/README.md`、T-REL-05、WEB-DL-004）。但 `docs/功能需求文档.md` REQ-PLAT-005 要求 Windows 10+、macOS 12+ 与主流 Linux 桌面都提供安装包，两份文档因此互相矛盾。

用户于 2026-11-18 逐轮确认：Windows / Linux 并入 v1.0.0；iOS 真机发布延后；桌面安装包均不签名；Windows / Linux 不做安装验收；Android 现有密钥库继续留在公开仓库；官网同步开放 Windows / Linux 下载。

`jpackage` 不能跨操作系统产出：MSI 必须在 Windows、DEB 必须在 Linux、DMG 必须在 macOS。本机只有 macOS，且没有 WiX、dpkg 与 Linux 工具链，因此必须有 Windows 与 Linux 构建环境。仓库为 public，GitHub Actions 的标准 runner 可免费使用，其中 `macos-15` 为 arm64。

## Decision

- v1.0.0 桌面交付四种产物：Android release 签名 APK、macOS arm64 DMG、Windows x64 MSI、Linux x86_64 DEB；后三者均不签名、不公证。
- iOS 仍只交付模拟器构建；真机签名、TestFlight 与 App Store 上架延后，前置条件单独成文，不在本版实现。
- 破例允许 **release-only GitHub Actions**：仅由 `v*` tag 或手动 `workflow_dispatch` 触发，测试通过后构建四平台产物并创建 GitHub Release；不做 PR CI、常驻 CI 或全量 UI 自动化。服务器下载目录仍由人工搬运，不把服务器凭据放进 CI。
- Windows / Linux 只做产物结构检查（存在、大小、`dpkg-deb --info`、SHA-256），不做安装与运行验收，登记为 v1.0.0 已知限制。
- Android 签名材料维持现状：`ClipboxClient/signing/` 继续被 Git 跟踪，CI 不使用 Secrets。该风险按 security-review F4 由用户复核接受。
- 版本号继续为 `1.0.0`，四处硬编码（Android `versionName`、Desktop `packageVersion`、iOS `MARKETING_VERSION`、官网 `VERSION`）由一致性脚本校验，不引入版本目录或动态版本插件。
- 官网上 Windows / Linux 变为可下载，并披露未签名与 SmartScreen 提示；iOS 继续显示「暂未提供」。
- `v1.0.0` tag 标记代码交付点；官网下载 SHA 数据提交与远程部署允许在 tag 之后，以 tag 触发的 CI 产物为唯一哈希口径。

## Considered Options

- **维持冻结范围**：文档最省事，但与 REQ-PLAT-005 冲突，也不满足用户「三端桌面都能装」的目标。
- **只提供源码构建说明**：不需要 Windows / Linux 构建环境，但普通用户拿不到可安装包，不算交付。
- **在本机交叉编译**：jpackage 不支持跨 OS，WiX 与 dpkg 也不可用，不可行。
- **完整 CI（PR + 主分支 + 发布）**：反馈更早，但超出学习项目需要，维护成本与 token 消耗更高。
- **Linux AppImage 单文件**：用户最初选择此项，但 Compose 1.9.3 的 `TargetFormat.AppImage` 实际映射 jpackage `app-image` 目录，不是 `.AppImage` 容器；官方仅承诺 Deb/Rpm，故改为 DEB。
- **给桌面安装包签名 / 公证**：需要 Windows 代码签名证书与 Apple Developer Program，成本与流程超出本版；改用 SHA-256 与明文未签名提示。
- **本版完成 iOS 真机发布**：需要付费开发者账号、证书、App ID、App Group、隐私清单与审核，无法在无账号环境下完成。

## Consequences

- `AGENTS.md`、任务卡、官网需求与验收矩阵必须同步修订；ADR-0003 的 HTTP 学习版边界不变。
- 新增 `.github/workflows/release.yml` 与版本一致性脚本；runner 固定 `ubuntu-24.04`、`windows-2022`、`macos-15`，降低镜像漂移。
- 未签名安装包会触发 SmartScreen 与 Gatekeeper 提示；官网必须给出明确操作路径，不能把未签名包装成可信分发。
- 公开的 Android 签名密钥意味着任何人都能签同名 APK；SHA-256 只能校验完整性，不能证明发布者。该风险随官网公开分发扩大，后续轮换密钥库需要用户卸载重装。
- Windows / Linux 安装与运行问题可能在上线后暴露；若发现阻断问题，应新开缺陷卡，不回退本 ADR 的四平台目标。
- tag 触发的产物哈希是官网唯一口径；tag 后的官网数据提交不会改变已发布的安装包。
- 本 ADR 解决 README / T-REL-05 / WEB-DL-004 与 REQ-PLAT-005 的范围冲突；后续再扩展平台或签名策略需要新的 ADR。
