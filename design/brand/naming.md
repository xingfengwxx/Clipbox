# 命名记录

产品对外品牌为一对中英名；`Clipbox` 保留为工程代号（包名、Bundle ID、URL Scheme、目录名不变）。本文件只记录命名口径、候选与可用性核查，不定义产品行为。

## 口径

- 中文 2 字，英文 1 个短词或拉丁短词根；中英各自好读，不强求音译；简繁同字。
- 语义场：容器与仪器。三个方向分别用「匣」「格」「槽」。
- 可用性硬门槛：App Store 或 Google Play 有同名应用，或搜索首页出现同类竞品，即淘汰。
- 商标只做初筛提示，不构成法律意见。

## 已淘汰

| 候选 | 原因 | 看到的东西 |
| --- | --- | --- |
| Capsa | App Store 有 Capsa App（文档与项目整理），同类 | apps.apple.com：Capsa App |
| Cista | Google Play 有 Cista 应用 | Google Play：com.colajy.cista |
| Cella | 多个同名 App，含「本机文档、笔记、语音」类产品 | App Store / Google Play 搜索结果 |
| Alveus | 多家同名软件开发公司 | alveuspk.com、alveusco.com |
| Sulcus | 同名 AI agent 运行时产品，属开发工具同类 | LinkedIn：sulcusdev |
| Envoi | Google Play 有 Envoi - Simple Invoice | Google Play |
| Arcula | App Store 有 Arcula 归档管理器，语义又极近 | apps.apple.com：Arcula |
| Loculus | loculus.org 开源软件 + LoculusPro App | loculus.org |
| Sortcase | 同名企业通信产品 | sortcase.com |
| Sortbox | App Store 有 SortBox 工具 | apps.apple.com：SortBox |
| Cliprail | 同名剪贴板工具 cliprail.store | cliprail.store |
| Clipcase | 同名剪贴板格式化工具与 Android 应用 | clipcase.app |
| Capsora | 同名 Flutter 应用模板 | gplpixel.com |
| Stria | 同名自由职业者工作台与 iPad 合成器 | getstria.com、App Store |
| Slipcase | App Store / Google Play 同名应用 | apps.apple.com：Slipcase |
| Boxlet | 同名日记 App | Google Play / App Store |
| Hutch | 同名电信运营商 App | hutch.lk |
| Kiste | 多个德语区同名应用 | Google Play |

## 六个候选的状态

| 方向 | 中文 | 英文 | 状态 | 备注 |
| --- | --- | --- | --- | --- |
| 一 · 匣 | 字匣 | Theca | **已定稿** | 拉丁语「匣、藏所」；核查结论见下节 |
| 一 · 匣 | 帖匣 | Capsel | 回退备选 | capsa 的缩略造词；Secop 有一个 CapSel 选型 App，非同类 |
| 二 · 格 | 信格 | Casier | 未选用 | 法语「分格架、投递格」；搜索未见同名产品，多为地名与人名 |
| 二 · 格 | 字格 | Cistora | 未选用 | cista + 后缀的造词；初筛未见同名结果，独特度最高 |
| 三 · 槽 | 信槽 | Sulci | 未选用 | 拉丁 sulcus 的复数「沟、槽」；初筛只见解剖学文献，未见同名 App |
| 三 · 槽 | 卡槽 | Cistrel | 未选用 | cista 的缩略造词；初筛未见同名结果 |

## 定稿核查（2026-11-16）

用户选定 D1「字匣 / Theca」。按硬门槛（App Store 同名，或搜索首页同类竞品即换）逐项核查：

| 检索 | 结果 | 判定 |
| --- | --- | --- |
| App Store「Theca」 | 无同名应用；仅 `iTheca`（西语神学阅读，名称不同、品类不同） | 通过 |
| Google Play「Theca」 | 有同名 `Theca: File Manager & Vault`（`com.logicgategames.theca`，文件管理 / 清理 / 保险库）；Play 允许同名，且与本产品「跨设备文本中转 + 开发者工具箱」不同品类 | 通过（记风险） |
| 搜索首页「Theca」 | 同名咨询公司与加密项目；另有 SEO 内容页把 Theca 描述为 Android 文件转换器 | 非同品类，通过 |
| App Store / Google Play「字匣」 | 未见同名应用；相近名为「词匣」「知匣」「隐匣」等，均不同名 | 通过 |
| 搜索首页「字匣」 | Linux 桌面字体管理器 `TypeVault 字匣`（deepin 应用商店，本地字体管理，开源） | 同名但不同平台、不同品类；按下述风险处理 |

结论：**保留「字匣 / Theca」**。以下风险在定稿后持续跟踪：

1. `TypeVault 字匣` 与本产品同名、同为「本地优先工具」气质，虽然一个管字体、一个管文本，仍可能造成搜索混淆。若在发布前发现中文区出现同类剪贴板 / 文本中转产品使用该名，按硬门槛回退到 `帖匣 / Capsel`（同 D1 词源，图形与字标结构不变，只需替换文字资产）。
2. Google Play 同名应用属文件管理类，不构成直接竞品，但商店搜索结果会并排出现；商店标题建议使用「字匣 · 文本中转」这类限定词组。
3. 商标仅做初筛提示，以上均不构成法律意见；正式发布前需做第 9 / 42 类检索。

检索来源：

- Google Play：`Theca: File Manager & Vault` — <https://play.google.com/store/apps/details?id=com.logicgategames.theca>
- App Store：`iTheca`（非同名） — <https://apps.apple.com/sg/app/itheca/id6739783184>
- `TypeVault 字匣` — <https://www.deepin.org/zh/deepin-typevault-v0-8/>、<https://gitcode.com/buyike/TypeVault/overview>
- SEO 内容页（非应用商店） — <https://choriodx.stanford.edu/guides/what-is-theca-simple-file-converter-for-android.html>

## 下一步

已定稿，进入资产与接入：`direction-approved.md` 记录选择，`brand-spec.md` 记录字标与图标规范，客户端按系统语言显示「字匣 / Theca」。若触发上节回退条件，只替换语言资源与字标文本，图形与图标不变。
