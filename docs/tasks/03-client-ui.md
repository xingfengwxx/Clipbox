# 客户端 UI 与平台入口任务卡（T-UI）

工程：`ClipboxClient/composeApp`。视觉基准：`design/Clipbox 三端设计.html`、`design/README.md`、`design/brand-spec.md`。
屏幕编号 S01–S21、D01–D02 见 `design/README.md` 屏幕地图。所有文案必须走多语言资源，不硬编码中文。

除特别说明，验收命令默认在 `ClipboxClient/` 下执行。Android 设备使用 AVD `Medium_Phone_API_37`；与本地服务端联调前执行 `adb reverse tcp:8080 tcp:8080`。

---

## T-UI-01 设计 tokens、主题与导航骨架

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-01 |
| 需求 | REQ-SET-005、REQ-PLAT-002/005、REQ-NFR-014 |
| 预估 | 1 天 |

**目标**：三端共享的设计系统与导航壳：浅色 / 深色 tokens、字体、底部导航与 Desktop NavigationRail。

**必读**：`design/README.md` 设计系统；`design/brand-spec.md`；`docs/客户端技术架构.md` §7.1–§7.2。

**涉及路径**：`composeApp/src/commonMain/kotlin/com/wangxingxing/clipbox/ui/theme/`、`.../ui/navigation/`、`App.kt`。

**实现要点**

- 色彩 tokens 严格使用 `design/README.md` 的 11 个角色与浅 / 深两套值；不做动态取色、不引入 Material You。
- 字体：正文系统无衬线；设备名 / 时间 / 字节数 / 同步状态用等宽栈；正文 ≥14px，标签 ≥12px，触控目标 ≥48dp。
- 圆角与间距：间距 4/8/12/16/24/32；弹层 6–10px；按钮 3px；列表发丝分隔线。
- 导航：手机底部三栏（历史 / 工具箱 / 设置）；宽度 ≥ 840dp 时切换为 NavigationRail（Desktop 与 iPad 共用规则）。
- 三个页面先放占位内容；主题跟随系统可运行。
- 提供 `AppTheme` 与 tokens 对象，禁止页面内散落十六进制色值。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
```

预期：Desktop 窗口出现，浅色 / 深色跟随系统；手机底栏可切换三个占位页；无硬编码颜色（可全局搜索 `0xFF` 检查，仅 theme 包允许）。

**DoD**

- [ ] tokens 与设计文档逐项一致。
- [ ] 导航状态可保存 / 恢复（进程重建后回到原页）。
- [ ] 所有字符串进入资源文件。

**禁止**

- 不要使用紫渐变、霓虹色或第三方品牌色。
- 不要把设计稿里的示例数据写死进页面。

---

## T-UI-02 配对页与未加密连接警示

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-UI-01、T-CORE-03、T-CORE-04 |
| 需求 | REQ-PAIR-002/003/004、REQ-SET-001 |
| 预估 | 1 天 |

**目标**：完成 S10 / S11A / S11B：输入服务器地址、6 位配对码、设备名，处理过期与 429 冷却。

**必读**：`design/README.md` S10–S11B；`docs/功能需求文档.md` PAIR 域；`docs/api.md` §2。

**涉及路径**：`composeApp/src/commonMain/.../ui/pairing/`、对应 ViewModel、资源文件。

**实现要点**

- 服务器地址：默认预填 `http://`；保存时校验 scheme 与主机；`http://` 时顶部展示「未加密连接，仅用于学习」警示。
- 配对码：6 位数字输入，允许前导零；设备名默认按平台生成，可编辑。
- 提交中禁用按钮；错误按码区分：`INVALID_CODE` / `CODE_EXPIRED` / `CODE_USED` / `RATE_LIMITED` / 网络错误。
- 429 展示冷却倒计时并禁用提交；`CODE_EXPIRED` 展示重新生成入口说明。
- 成功：保存令牌与设备信息，触发首次同步，返回历史页。
- 未配对时历史页空态保留「去配对」入口（在 T-UI-03 接线）。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.wangxingxing.clipbox/.MainActivity
```

预期：在 AVD 上可完成配对（服务端本机 + `adb reverse`）；输入 `http://` 地址出现警示；错误码文案与需求一致。

**DoD**

- [ ] 地址、码、设备名均有输入校验。
- [ ] 配对中重复点击不会发两次请求。
- [ ] 警示文案进入多语言资源。

**禁止**

- 不要强制 HTTPS。
- 不要在客户端生成设备令牌。

---

## T-UI-03 历史页、发送器与记录列表

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-UI-01、T-CORE-06 |
| 需求 | REQ-SEND-001/002、REQ-HIST-001…004、REQ-HIST-009、REQ-PAIR-008 |
| 预估 | 1 天 |

**目标**：S01 / S02：发送输入框、记录列表、置顶分组、来源设备与相对时间、复制。

**必读**：`design/README.md` S01/S02；`docs/客户端技术架构.md` §7.2。

**涉及路径**：`composeApp/src/commonMain/.../ui/history/`、`.../ui/components/ClipRow.kt`、资源文件。

**实现要点**

- 顶栏：产品名 + 同步状态占位 + 设置入口。
- 发送器：多行输入、发送按钮、UTF-8 字节数；发送后清空并滚动到最新。
- 列表：置顶组在前 + 非置顶最近 200 条；每项最多 3 行预览、来源设备、相对时间（>24h 显示绝对时间）与等宽元数据。
- 点击记录：复制并给出轻提示；行内提供置顶与更多操作入口（更多面板在 T-UI-05 完成）。
- 空态：未配对显示线稿 + 「还没有记录」+「去配对」；已配对无记录显示发送引导。
- 不展示已删除记录；待同步 / 失败角标由 T-UI-05 接入。
- 所有字符串进入资源文件。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb reverse tcp:8080 tcp:8080
adb shell am start -n com.wangxingxing.clipbox/.MainActivity
```

预期：输入文本发送后立即出现在列表顶部；点击复制可在其他应用粘贴；未配对空态可进入配对页。

**DoD**

- [ ] 列表滚动流畅，长文本不撑破布局。
- [ ] 等宽元数据与设计一致。
- [ ] 空态两种状态正确。

**禁止**

- 不要在 UI 直接访问 Room 或 ApiService。
- 不要为列表实现分页（本地最多 200 条非置顶 + 置顶）。

---

## T-UI-04 UTF-8 字节计数、超限预警与降级

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-UI-03、T-CORE-02 |
| 需求 | REQ-SEND-001/006/009 |
| 预估 | 0.5 天 |

**目标**：S05：实时字节数、90% 预警、超限禁用发送并改为复制到剪贴板。

**必读**：`design/README.md` S05；`docs/功能需求文档.md` SEND 域。

**涉及路径**：`composeApp/src/commonMain/.../ui/history/Composer.kt`、资源文件。

**实现要点**

- 实时显示 UTF-8 字节数；颜色只在接近 / 超限时使用 warn / danger。
- ≥90% 展示预警；>65536 禁用发送，显示「改为复制」按钮并说明原因。
- 粘贴超大文本不崩溃；计数与 Repository 校验使用同一个工具函数。
- 警告状态不改变输入内容。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :shared:allTests --tests '*TextSize*'
```

预期：输入 70 KiB 文本时发送禁用且出现复制降级入口。

**DoD**

- [ ] 中文 / emoji 计数正确。
- [ ] 超限文案多语言。
- [ ] 复制降级不写历史。

**禁止**

- 不要用字符数代替字节数。
- 不要自动截断用户文本。

---

## T-UI-05 搜索、置顶、删除确认与失败操作

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-UI-03、T-CORE-07 |
| 需求 | REQ-HIST-003/005/006/007/008、REQ-SYNC-005/006 |
| 预估 | 1 天 |

**目标**：S03 / S03B / S04 / S06 / S09：搜索、置顶、删除二次确认、失败重试与操作面板。

**必读**：`design/README.md` 对应屏；`docs/客户端技术架构.md` §6.5。

**涉及路径**：`composeApp/src/commonMain/.../ui/history/`、`.../ui/components/ClipActionsSheet.kt`、`.../ui/components/ConfirmDialog.kt`。

**实现要点**

- 搜索：300ms 防抖、大小写不敏感、只搜本地；结果计数；无结果空态与清空恢复。
- 置顶：即点即改，本地立即生效；置顶组排序正确。
- 操作面板（S06）：复制 / 置顶 / 生成分享 / 删除；未同步时分享禁用并说明。
- 删除：二次确认对话框，明确不可恢复；危险色只出现在确认按钮。
- 失败记录：错误角标 + 重试 / 删除；离线状态条展示待同步数量。
- 详情操作不新增页面，全部用弹层 / 面板。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
```

预期：搜索 300ms 后过滤；置顶立即置顶；删除需确认；失败记录可重试。

**DoD**

- [ ] 搜索无结果与列表空态可区分。
- [ ] 删除取消不产生本地或网络变更。
- [ ] 操作面板文案随语言切换。

**禁止**

- 不要在搜索中请求服务端。
- 不要为删除提供撤销（v1.0.0 无恢复接口）。

---

## T-UI-06 分享流程 UI

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-14、T-UI-05 |
| 需求 | REQ-SHARE-001/002/006/007/009/010 |
| 预估 | 1 天 |

**目标**：S07 / S08：选择有效期（1 小时 / 24 小时 / 7 天 / 阅后即焚），生成并展示完整 URL。

**必读**：`design/README.md` S07/S08；`docs/api.md` §5.1。

**涉及路径**：`composeApp/src/commonMain/.../ui/share/`、资源文件。

**实现要点**

- 两步：选择有效期与是否一次性 → 生成 → 展示 URL、过期时间、一次性标识。
- 默认 24 小时；提交中禁用；失败按错误类型提示，可重试。
- 未同步记录入口置灰，提示「同步后可分享」。
- 生成后提供复制链接；URL 原样使用服务端返回值，不拼接。
- 关闭弹层不丢失当前操作结果（可再次打开）。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
```

预期：已同步记录可生成链接；未同步记录入口禁用；复制后浏览器可打开。

**DoD**

- [ ] 有效期四档与文档一致。
- [ ] 一次性标识在结果页可见。
- [ ] 分享失败不写入任何本地分享记录。

**禁止**

- 不要在客户端构造 `/s/` 链接。
- 不要为分享增加列表页。

---

## T-UI-07 同步状态与「新记录」提示

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-08、T-CORE-09、T-UI-03 |
| 需求 | REQ-RECV-001/006/007、REQ-SYNC-011 |
| 预估 | 0.5–1 天 |

**目标**：S04 / S21：同步状态条、离线提示、工具箱页收到记录时的 Snackbar。

**必读**：`design/README.md` S04/S21；`docs/客户端技术架构.md` §6.5、§7.5。

**涉及路径**：`composeApp/src/commonMain/.../ui/components/SyncStatusBar.kt`、`.../App.kt`、`.../ui/toolbox/`。

**实现要点**

- 状态：同步中、已同步、离线、待同步 N 条、失败 N 条、未授权；不打断当前操作。
- 离线时本地功能照常；顶部状态条可点击查看说明或重试。
- 非历史页收到新记录：显示 Snackbar「收到新记录」，点击切回历史并定位最新记录；同一批只提示一次。
- 历史页激活时不弹 Snackbar，直接插入列表。
- 状态文案与颜色符合 tokens；成功 / 警告 / 危险色仅用于对应状态。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
```

预期：断网发送后显示待同步；恢复后自动消失；停留在工具箱页收到记录出现 Snackbar。

**DoD**

- [ ] Snackbar 不重复堆叠。
- [ ] 未授权状态与离线状态可区分。
- [ ] 状态更新不导致列表闪烁。

**禁止**

- 不要用 Toast 替代状态条。
- 不要在历史页重复提示新记录。

---

## T-UI-08 设置页、外观语言入口与解除配对

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-05、T-CORE-03 |
| 需求 | REQ-SET-001…011、REQ-PAIR-006/007 |
| 预估 | 1 天 |

**目标**：S12 / S14：服务器、设备、外观、数据、诊断、关于、危险区；解除配对二次确认。

**必读**：`design/README.md` S12/S14；`docs/客户端技术架构.md` §7.4。

**涉及路径**：`composeApp/src/commonMain/.../ui/settings/`、资源文件。

**实现要点**

- 分组：服务器（地址、连接状态、立即同步）、设备（名称、配对状态）、Desktop 剪贴板（仅 Desktop）、外观（主题、语言）、诊断（开关 + 说明）、关于（版本、构建、隐私说明）、危险区（解除配对）。
- 修改服务器地址必须先解除配对；解除配对清空令牌、本地数据与 Outbox，二次确认写明后果。
- release 开启诊断日志需二次确认；debug 默认开启。
- 自动发送开关默认关；Desktop 开启时展示隐私确认。
- 未配对状态隐藏「解除配对」，显示「去配对」。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
```

预期：Desktop 设置页出现剪贴板分组；解除配对后回到未配对空态；诊断开关状态持久化。

**DoD**

- [ ] 危险操作需二次确认。
- [ ] 所有设置立即生效并持久化。
- [ ] 关于页披露明文存储与未加密传输。

**禁止**

- 不要提供「退出登录」等文档外概念。
- 不要在设置页直接修改数据库。

---

## T-UI-09 主题模式切换

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-01、T-CORE-03 |
| 需求 | REQ-SET-005 |
| 预估 | 0.5 天 |

**目标**：S13：跟随系统 / 浅色 / 深色，切换立即生效、无闪烁、重启保持。

**必读**：`design/README.md` S13；`docs/客户端技术架构.md` §7.6。

**涉及路径**：`composeApp/src/commonMain/.../ui/settings/AppearanceScreen.kt`、`.../ui/theme/`。

**实现要点**

- 三选一分段控件；默认跟随系统；`prefers-color-scheme` / 系统外观变化实时响应。
- 切换写入 DataStore，重启保持；启动首帧不闪白 / 闪黑。
- 弹层、Snackbar、对话框同步换色。
- 不修改系统主题，不动态取色。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:run
```

预期：三种模式切换即时生效；系统切换深浅色时「跟随系统」模式跟随；重启保持。

**DoD**

- [ ] 所有页面与弹层使用同一 tokens。
- [ ] 切换不重建整个应用。
- [ ] 对比度符合设计文档。

**禁止**

- 不要引入 Material You 动态色。
- 不要为深色模式单独维护一套页面。

---

## T-UI-10 多语言资源与语言切换

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-08 |
| 需求 | REQ-SET-009、REQ-NFR-016 |
| 预估 | 1 天 |

**目标**：简体中文（默认）/ 繁體中文 / English 三套资源完整，运行时切换立即生效。

**必读**：`docs/客户端技术架构.md` §7.7；`design/README.md` 已知假设；`docs/功能需求文档.md` REQ-SET-009。

**涉及路径**：`composeApp/src/commonMain/composeResources/values*/strings.xml`、`.../ui/settings/`、平台 Locale 注入。

**实现要点**

- 默认 `values` 为简体中文；`values-zh-rTW` 繁體中文；`values-en` English。
- 首次启动固定 `zh-Hans`，不跟随系统；切换写入 DataStore 并立即重组界面。
- 清除所有硬编码用户可见文案，包括错误提示、对话框、Snackbar、空态。
- 英文与繁體中文不得截断；按钮与列表项弹性布局。
- 系统弹窗（权限、分享）跟随操作系统语言，这不是缺陷。
- 提供一份关键文案对照表（三语）供 T-REL-06 验收。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
```

预期：三语切换全部页面立即更新；重启保持；无中文硬编码残留（可搜索 Kotlin 源文件中的中文字符串）。

**DoD**

- [ ] 三套资源 key 完全一致。
- [ ] 错误码映射使用资源，不拼接英文。
- [ ] 日期 / 时间格式随语言变化。

**禁止**

- 不要引入第三方翻译 SDK。
- 不要按系统语言自动切换。

---

## T-UI-11 工具箱入口与工具页骨架

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-01 |
| 需求 | REQ-TOOL-001、REQ-NFR-012 |
| 预估 | 0.5 天 |

**目标**：S15：5 个工具卡入口；工具页共用布局：输入、输出、复制、清空、错误区。

**必读**：`design/README.md` S15；`docs/客户端技术架构.md` §6.8。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/`、资源文件。

**实现要点**

- 工具卡：JSON、Base64、URL、时间戳、正则；未配对也可用。
- 共用组件：标题、说明、输入框、操作按钮、结果面板、复制按钮、错误提示。
- 工具页不写历史、不联网、不请求权限。
- 100 KiB 输入上限统一提示；计算放 `Dispatchers.Default`。
- 返回导航回到工具箱网格。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
```

预期：5 个入口可进入占位页；未配对状态可访问；复制按钮可用。

**DoD**

- [ ] 工具页不依赖 Repository / 网络。
- [ ] 大输入不阻塞主线程。
- [ ] 所有工具页共用布局组件。

**禁止**

- 不要在工具箱中加入网络请求或 AI 能力。
- 不要为每个工具复制一套布局。

---

## T-UI-12 JSON 格式化 / 压缩 / 校验

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-11 |
| 需求 | REQ-TOOL-002/009 |
| 预估 | 1 天 |

**目标**：S16 / S16B：JSON 格式化、压缩、校验；错误定位到行与列。

**必读**：`design/README.md` S16/S16B；`docs/功能需求文档.md` TOOL 域。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/json/`、`shared/src/commonMain/.../toolbox/`（纯逻辑）、测试。

**实现要点**

- 格式化缩进 2 空格；压缩移除无意义空白；校验只报结果。
- 错误显示行、列与简短原因；不打印完整堆栈。
- 输入上限 100 KiB；超限提示且不计算。
- 计算在 `Dispatchers.Default`；结果面板支持复制。
- 纯逻辑放入共享层并用单元测试覆盖：合法、非法、嵌套、Unicode、超大输入。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*JsonTool*'
./gradlew :composeApp:assembleDebug
```

预期：非法 JSON 显示行 / 列；合法 JSON 格式化与压缩结果正确。

**DoD**

- [ ] 错误定位与测试一致。
- [ ] 不修改输入内容。
- [ ] 结果复制可用。

**禁止**

- 不要引入大型 JSON 编辑组件。
- 不要联网校验。

---

## T-UI-13 Base64 工具

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-11 |
| 需求 | REQ-TOOL-003/009 |
| 预估 | 0.5 天 |

**目标**：S17：标准 / URL-safe 编解码，容错解码。

**必读**：`design/README.md` S17；`docs/功能需求文档.md` TOOL 域。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/base64/`、`shared/src/commonMain/.../toolbox/`、测试。

**实现要点**

- 模式切换：标准 / URL-safe；自动处理填充。
- 编码 UTF-8；解码容错空白与缺失填充；非法字符给出明确错误。
- 输入上限 100 KiB；结果可复制。
- 纯逻辑单测覆盖中文、emoji、空串、非法输入。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*Base64Tool*'
```

预期：标准与 URL-safe 往返一致；非法输入不崩溃。

**DoD**

- [ ] 不使用平台特定编码 API 导致三端差异。
- [ ] 解码错误可读。
- [ ] 结果复制可用。

**禁止**

- 不要静默忽略非法字符。

---

## T-UI-14 URL 编解码工具

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-11 |
| 需求 | REQ-TOOL-004/009 |
| 预估 | 0.5 天 |

**目标**：S18：组件 / 整串编码解码，`+` 空格开关。

**必读**：`design/README.md` S18；`docs/功能需求文档.md` TOOL 域。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/url/`、`shared/src/commonMain/.../toolbox/`、测试。

**实现要点**

- 模式：组件编码 / 整串编码；解码对应。
- `+` 视为空格开关；非法百分号编码给出明确错误。
- UTF-8 往返；输入上限 100 KiB；结果可复制。
- 单测覆盖中文、空格、保留字符、非法序列。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*UrlTool*'
```

预期：编解码往返一致；开关行为符合文案。

**DoD**

- [ ] 三端结果一致。
- [ ] 错误不崩溃。
- [ ] 不联网解析短链。

**禁止**

- 不要自动打开浏览器。

---

## T-UI-15 时间戳转换工具

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-11 |
| 需求 | REQ-TOOL-005/009 |
| 预估 | 0.5 天 |

**目标**：S19：当前时间戳一键填入，本地 / UTC / ISO 互转。

**必读**：`design/README.md` S19；`docs/功能需求文档.md` TOOL 域。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/timestamp/`、`shared/src/commonMain/.../toolbox/`、测试。

**实现要点**

- 支持秒 / 毫秒输入自动识别或显式切换；输出本地时间、UTC、ISO-8601。
- 「当前时间戳」一键填入；结果可复制。
- 非法输入与超出范围给出明确错误；使用 kotlinx-datetime，不依赖平台时区实现差异。
- 单测固定时区与时刻，覆盖闰年、边界、负数。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*TimestampTool*'
```

预期：固定时刻在三端输出一致。

**DoD**

- [ ] 时区处理集中在共享层。
- [ ] ISO 输出带时区标识。
- [ ] 不出现系统默认时区导致的测试漂移。

**禁止**

- 不要使用 `System.currentTimeMillis` 直接散落在 UI。
- 不要联网校时。

---

## T-UI-16 正则测试工具

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-11 |
| 需求 | REQ-TOOL-006/009 |
| 预估 | 1 天 |

**目标**：S20：匹配高亮、分组展示、常用标志开关。

**必读**：`design/README.md` S20；`docs/功能需求文档.md` TOOL 域；`docs/功能需求文档.md` 风险表（正则卡死）。

**涉及路径**：`composeApp/src/commonMain/.../ui/toolbox/regex/`、`shared/src/commonMain/.../toolbox/`、测试。

**实现要点**

- 标志：忽略大小写、多行、点匹配换行；界面逐项开关。
- 展示所有匹配区间与分组；高亮不破坏原文；无匹配明确提示。
- 输入上限 100 KiB；计算在 `Dispatchers.Default`；非法正则显示错误。
- 对灾难性回溯不做承诺，但必须有输入上限，避免主线程卡死。
- 单测覆盖分组、命名分组（若平台支持一致）、空匹配、Unicode。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*RegexTool*'
./gradlew :composeApp:assembleDebug
```

预期：高亮区间与分组正确；非法正则不崩溃。

**DoD**

- [ ] 三端正则行为一致（使用 Kotlin 公共 API）。
- [ ] 100 KiB 上限生效。
- [ ] 不阻塞 UI。

**禁止**

- 不要在每次按键时同步执行大文本正则。
- 不要引入平台特有正则引擎分支。

---

## T-UI-17 Android 分享入口、透明接收页与明文网络配置

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-01 |
| 需求 | REQ-SEND-003/004/005、REQ-PLAT-004/006、REQ-NFR-001 |
| 预估 | 1 天 |

**目标**：Android 可从系统分享与文本选中菜单进入 Clipbox；HTTP 地址可访问。

**必读**：`docs/客户端技术架构.md` §6.7.1、§8.2；`docs/功能需求文档.md` PLAT 域；`docs/api.md` §1.1。

**涉及路径**：`composeApp/src/androidMain/AndroidManifest.xml`、`.../ShareReceiverActivity.kt`、`.../res/xml/network_security_config.xml`、资源。

**实现要点**

- Manifest 注册 `ACTION_SEND`（`text/plain`）与 `ACTION_PROCESS_TEXT`（`text/plain`），`exported=true`。
- 分享文本先写入本地草稿（内存 / DataStore 短生命周期），再打开主界面；未配对时保留草稿，配对后预填。
- 透明接收页风格使用 `Theme.Translucent.NoTitleBar`；不显示通知、不做后台剪贴板。
- `network_security_config` 允许明文 HTTP（学习版），仅应用于本应用；不得全局放开其他域名策略以外内容。
- 应用图标 / 名称按 `brand-spec.md`；`android:label` 使用 Clipbox。
- 不申请存储、通知、后台权限。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb reverse tcp:8080 tcp:8080
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "来自分享的文本" -n com.wangxingxing.clipbox/.ShareReceiverActivity
```

预期：分享后进入 App，文本出现在输入框 / 列表并可发送；应用不崩溃。

**DoD**

- [ ] 分享入口在系统分享面板可见。
- [ ] 选中文本菜单可见（依赖设备支持）。
- [ ] 无额外权限声明。

**禁止**

- 不要注册 `ACTION_BOOT_COMPLETED` 或后台服务。
- 不要把整个应用网络配置为无差别信任证书。

---

## T-UI-18 Desktop 窗口入口

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-01 |
| 需求 | REQ-SEND-003、REQ-RECV-002/003、REQ-PLAT-002 |
| 预估 | 0.5 天 |

**目标**：D01 基础形态：1000×700 窗口、NavigationRail、历史页可发送与接收。

**必读**：`design/README.md` D01；`docs/客户端技术架构.md` §7.1、§10。

**涉及路径**：`composeApp/src/desktopMain/kotlin/.../Main.kt`、窗口配置、资源。

**实现要点**

- 窗口默认 1000×700，最小 640×480；标题 Clipbox；关闭窗口即退出（无托盘）。
- 与手机共用历史页布局；宽屏使用 NavigationRail。
- 桌面发送入口复用发送器；接收后列表更新。
- 不实现托盘、全局快捷键。
- 窗口图标使用品牌字标（若有资源；无则跳过并记录）。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:run
```

预期：窗口打开并可缩放；最小尺寸生效；发送后列表出现记录。

**DoD**

- [ ] 窗口关闭后进程退出。
- [ ] 最小尺寸下布局不重叠。
- [ ] 无托盘图标残留。

**禁止**

- 不要在 Desktop 实现后台常驻。
- 不要为 Desktop 单独维护一套历史页。

---

## T-UI-19 可访问性与视觉核对

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-05、T-UI-09、T-UI-10、T-CORE-12 |
| 需求 | REQ-NFR-014/015/016、REQ-PLAT-001/002 |
| 预估 | 1 天 |

**目标**：对照设计稿逐屏核对 Android 24 屏与 Desktop 2 态；触控目标、对比度、字号、TalkBack 标签达标。

**必读**：`design/README.md` 全部屏幕地图；`design/brand-spec.md`；`docs/功能需求文档.md` NFR 域。

**涉及路径**：UI 组件与资源；产出 `docs/tasks/visual-checklist.md`（核对结果，不写设计文档）。

**实现要点**

- 逐屏截图（Android 412×915 视口；Desktop 1000×700 与 640×480），与设计稿并排核对。
- 触控目标 ≥48dp；正文 ≥14px；标签 ≥12px；正文对比度 ≥4.5:1。
- 为图标按钮、记录行、开关、工具卡补充 contentDescription；TalkBack 可读完一条记录的关键信息。
- 检查三语不截断、深浅色不丢失层级。
- 记录不可避免的偏差（如字体渲染差异），不擅自改设计。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
adb exec-out screencap -p > /tmp/clipbox-history.png
```

预期：截图与设计稿对照通过；核对表列出每屏结论与偏差。

**DoD**

- [ ] 24 屏 + 2 桌面态全部核对。
- [ ] 无对比度 / 字号红灯。
- [ ] 偏差有记录与理由。

**禁止**

- 不要为了截图修改真实数据逻辑。
- 不要引入截图自动化框架。

---

## T-UI-20 本地端到端手测

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-UI-05、T-UI-06、T-UI-07、T-UI-08、T-S-11 |
| 需求 | AC-01…AC-20 中的本地可验证项 |
| 预估 | 1 天 |

**目标**：在本机跑通 Android + Desktop + 服务端的完整闭环，留下证据。

**必读**：`docs/功能需求文档.md` §5 验收场景；`docs/tasks/README.md` M1–M3 Gate。

**涉及路径**：不新增业务代码；产出 `docs/tasks/e2e-local.md` 记录。

**实现要点**

- 启动服务端（本机 8080），生成配对码；Android 通过 `adb reverse tcp:8080 tcp:8080` 配对，Desktop 用 `http://localhost:8080` 配对。
- 验证：手机分享 → Desktop 剪贴板；Desktop 发送 → Android 前台可见并复制；断网发送 → 恢复补发；WS 断线 → 重连补拉；删除 / 置顶双端一致；分享链接浏览器可读；一次性链接第二次 410。
- 验证：未配对空态、超限降级、搜索、5 个工具、主题、三语、诊断开关。
- 截图 / 终端输出留档，记录失败项与复现步骤。

**验收**

```bash
cd ClipboxServer && ./gradlew run
cd ClipboxClient && adb reverse tcp:8080 tcp:8080
cd ClipboxClient && ./gradlew :composeApp:run
```

预期：上述场景全部通过；`docs/tasks/e2e-local.md` 有逐项结论。

**DoD**

- [ ] 每个场景有证据（截图或命令输出）。
- [ ] 失败项已转为 bug 卡或修复。
- [ ] 不修改验收标准来「通过」。

**禁止**

- 不要用 mock 数据替代真实服务端做本卡验收。
- 不要跳过断网与重连场景。

---

## T-UI-21 设置页配对新设备与配对页初始化

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5（v1.0.0 收尾） |
| 前置 | T-UI-20、T-CORE-16 |
| 需求 | REQ-PAIR-001、REQ-PAIR-009、REQ-SET-001、REQ-NFR-003 |
| 预估 | 1 天 |

**目标**：已配对设备可在设置页按需生成配对码；空库服务器可在配对页自助初始化；三语文案完整。

**必读**：`docs/客户端技术架构.md` §7.3、§7.4、§8.1；`design/README.md` S10/S11；`docs/tasks/i18n-key-phrases.md`。

**涉及路径**：`composeApp/src/commonMain/.../ui/settings/`、`ui/pairing/`、`composeResources/values{,-en,-zh-rTW}/strings.xml`、`docs/tasks/i18n-key-phrases.md`。

**实现要点**

- 设置页设备分组新增「配对新设备」（仅已配对可见）。
- `PairingCodeDialog`：6 位等宽展示、按 `expiresAt` 倒计时、重新生成、复制走 `ClipboardService`；关闭即清空内存态。
- `SettingsViewModel/UiState`：`pairingCode`、`pairingCodeExpiresAt`、`isGeneratingCode`、`codeError`、429 冷却；错误映射集中管理。
- 配对页新增「初始化此服务器（首次部署）」+ 二次确认；409 提示「服务器已初始化，请输入已配对设备的配对码」。
- 三语文案全部进入 composeResources，不硬编码。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
```

预期：设置页生成/复制/倒计时/重新生成可用；配对页初始化成功与 409 提示正确；三语无缺失。

**DoD**

- [ ] 关闭弹层后内存与界面不再保留配对码。
- [ ] 一卡一提交：`T-UI-21: <一句话>`。

**禁止**

- 不要新增权限、二维码或后台轮询。
