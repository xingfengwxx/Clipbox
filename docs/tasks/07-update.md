# 检查更新任务卡（T-UPD）

M7 目标：在 v1.1.0 交付「检查更新 → 下载 → 校验 → 安装」闭环。发行服务器提供公开、无鉴权的更新描述；三端设置页可检查，Android / Desktop 可下载并交给系统安装，iOS 只展示版本与发布说明。

前置：M5 已产出四平台 release 产物，M6 的下载区由 release CI 自动上线（[ADR-0009](../adr/0009-auto-publish-downloads.md)）。学习版仍为 HTTP + IP 直连（[ADR-0003](../adr/0003-http-ip-learning-deployment.md)），更新与安装决策见 [ADR-0010](../adr/0010-app-update-and-install.md)。

需求基线：[功能需求文档](../功能需求文档.md) REQ-SET-012/013、AC-21…AC-26；接口契约：[api.md](../api.md) §13；实现细节：[客户端技术架构](../客户端技术架构.md) §6.10/§7.8/§9.4/§13.1、[服务端技术架构](../服务端技术架构.md) §7.6/§11.7。

```text
T-WEB-11 -> T-WEB-10 -> T-WEB-12
T-UPD-01 -> T-UPD-02 -> T-UPD-03 -> T-UPD-04
T-UPD-03 -> T-UPD-05 -> T-UPD-06
T-UPD-03 -> T-UPD-07 -> T-UPD-08
T-UPD-06 + T-UPD-08 -> T-UPD-09 / T-UPD-10 / T-UPD-11
T-UPD-04 + T-UPD-08 -> T-UPD-12
T-UPD-06 + 08 + 09 + 10 + 11 + 12 -> T-UPD-13 -> T-UPD-14
```

---

## T-UPD-01 治理与 ADR-0010

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | 无 |
| 需求 | REQ-SET-012/013、NFR-017 |
| 预估 | 0.5 天 |

**目标**：把更新与安装的边界写进执行协议与决策记录，后续卡片只按这份边界实现。

**必读**：`AGENTS.md`、`docs/adr/0005-v1-multiplatform-release.md`、`docs/adr/0009-auto-publish-downloads.md`。

**涉及路径**：`AGENTS.md`、`docs/adr/0010-app-update-and-install.md`（新增）、`docs/tasks/README.md`。

**实现要点**

- §3 第 12 条新增唯一权限例外：Android `REQUEST_INSTALL_PACKAGES`，仅用于用户主动触发的安装包安装。
- §3 第 16 条允许 DownloadManager 的系统下载通知；应用自建通知、前台服务、后台轮询仍禁止。
- 新增「更新与安装」硬不变量：只查官方发行服务器、不带设备令牌、安装前 SHA-256 校验、不静默安装、日志不写说明与下载信息。
- ADR-0010 记录公开接口、清单 schema v2、进程级控制器、三端安装边界、HTTP + SHA 的已知局限与无强制更新。
- §1 当前状态改为 v1.1.0；§2 ADR 列表加入 0010。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
test -f docs/adr/0010-app-update-and-install.md
grep -n "REQUEST_INSTALL_PACKAGES\|官方发行服务器\|SHA-256\|静默安装" AGENTS.md
grep -n "ADR-0010\|0010-app-update" AGENTS.md docs/tasks/README.md
```

预期：ADR 状态为 accepted；AGENTS 含权限例外与四条更新不变量；README 引用 ADR-0010。

**DoD**

- [ ] 不改冻结的接口、需求与架构文档（由 T-UPD-02/03 单独处理）。
- [ ] ADR 含 Considered Options 与 Consequences，不把学习版 HTTP 描述为安全方案。
- [ ] 一卡一提交：`T-UPD-01: <一句话>`。

**禁止**

- 不要新增 Android 通知、前台服务或存储权限。
- 不要把更新源写成配对服务器或用户可配置地址。

---

## T-UPD-02 接口契约 §13

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-01 |
| 需求 | REQ-SET-012、NFR-017 |
| 预估 | 0.5 天 |

**目标**：在 `api.md` 冻结 `GET /api/v1/app/latest` 的请求、响应、失败与限流，服务端与三端只按此实现。

**必读**：`docs/api.md` 现有错误码、限流与安全头章节、`docs/adr/0010-app-update-and-install.md`。

**涉及路径**：`docs/api.md`。

**实现要点**

- §1.2 公开接口与 §7 503 条件加入更新接口；§8 限流表加入 60 次/分钟/IP。
- 新增 §13：无认证、官方发行服务器专用、响应含 `version`、`publishedAt`、三语 `notes` 与五平台 `downloadUrl`。
- 明确 `downloadUrl` 由服务端用 `PUBLIC_BASE_URL` 拼接；示例的 `sizeBytes` / `sha256` 只作占位并标注。
- 明确 503 条件、HEAD、`no-store`、日志脱敏与版本比较口径；补 §12 变更记录。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
grep -n "GET /api/v1/app/latest" docs/api.md
grep -n "schemaVersion\|downloadUrl\|Retry-After" docs/api.md | tail -20
git diff --check -- docs/api.md
```

预期：§13 字段表、示例、失败条件与限流完整；无空白错误。

**DoD**

- [ ] 示例不伪造真实哈希或文件大小。
- [ ] 不新增错误码；更新失败复用 `SERVICE_UNAVAILABLE`。
- [ ] 一卡一提交：`T-UPD-02: <一句话>`。

**禁止**

- 不要让接口读取配对服务器地址或设备令牌。
- 不要承诺后台推送、强制更新或灰度字段。

---

## T-UPD-03 需求、架构、术语与任务卡同步

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-02 |
| 需求 | REQ-SET-012/013、AC-21…AC-26 |
| 预估 | 0.5 天 |

**目标**：让需求、两份架构、术语与任务卡对同一套更新行为形成单一说法。

**必读**：`docs/功能需求文档.md`、`docs/客户端技术架构.md`、`docs/服务端技术架构.md`、`docs/GLOSSARY.md`、`docs/tasks/README.md`。

**涉及路径**：上述五份文档与 `docs/tasks/07-update.md`（新增）。

**实现要点**

- 需求：REQ-SET-012 检查更新、REQ-SET-013 下载与安装；AC-21…AC-26 与覆盖矩阵；默认值补充更新来源、提示方式与安装方式。
- 客户端架构：§6.10 更新控制器（进程级、不入 `AppSession`）、§7.8 设置入口与弹窗、§9.4 状态机、§13.1 测试；修正 `AppContainer` 只重建会话的口径。
- 服务端架构：§7.6 更新描述、§8.3 限流、§11.4 下载目录配置、§11.7 清单切换与回滚、§13 测试用例。
- 术语：发行服务器、配对服务器、更新检查、更新描述、发布说明、安装包校验；下载清单改为 schema v2。
- 任务卡总表加入 M7、T-WEB-12 与 T-UPD-01…14 的依赖、派单顺序与状态。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
grep -n "REQ-SET-012\|REQ-SET-013\|AC-26" docs/功能需求文档.md
grep -n "6.10 更新控制器\|7.8 更新入口\|9.4 更新检查\|13.1 更新与安装" docs/客户端技术架构.md
grep -n "7.6 应用更新描述\|11.7 下载目录\|应用更新描述" docs/服务端技术架构.md
grep -n "发行服务器\|更新描述\|安装包校验" docs/GLOSSARY.md
grep -n "M7\|T-UPD-01\|07-update.md" docs/tasks/README.md
git diff --check -- docs
```

预期：章节存在、编号连续、术语无歧义；`git diff --check` 通过。

**DoD**

- [ ] 未重排既有章节，只在约定位置追加子节。
- [ ] 文档间冲突已停下确认，不自行选择。
- [ ] 一卡一提交：`T-UPD-03: <一句话>`。

**禁止**

- 不要修改 T-UPD-01/02 已冻结的 AGENTS 与 `api.md` 结论。
- 不要把实现计划写成新的需求或接口字段。

---

## T-UPD-04 三方向设计门

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-03 |
| 需求 | REQ-SET-012/013、REQ-PLAT |
| 预估 | 1 天 |

**目标**：用真实 HTML 与截图确定设置页更新区、更新弹窗、下载/校验/安装确认的视觉方向；用户选定前不写 Compose UI。

**必读**：`design/README.md`、`design/brand-spec.md`、`design/Clipbox 三端设计.html`、现有 `ClipboxTokens`，并按 `huashu-design` 技能执行三方向门。

**涉及路径**：`design/updates/directions/`、`design/updates/shots/`、`design/direction-approved.md`（新增）。

**实现要点**

- 先跑 `learn.py`、`design_lint.py ledger`、`capabilities.py`，读本机品味档案与外观账本。
- 每个方向做三屏：设置页「更新」区、有新版本弹窗（版本/日期/说明/大小）、HTTP 与未签名风险确认 + 下载/校验/安装状态；不新增导航路由。
- 沿用固定色板与品牌 tokens，不做动态取色；三方向必须彼此不同，避免同一外观家族。
- 产出六格缩略图与三版完整 HTML；每版截 390px 与 1440px，明暗至少各一张。
- 摆出截图后停，等待用户选择；把选择原话与截图路径写入 `design/direction-approved.md`。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
HD=/Users/wangxingxing/.qoder/skills/huashu-design
python3 "$HD/scripts/design_lint.py" check design/updates/directions/D1.html
python3 "$HD/scripts/design_lint.py" check design/updates/directions/D2.html
python3 "$HD/scripts/design_lint.py" check design/updates/directions/D3.html
ls design/updates/shots design/direction-approved.md
```

预期：三版 HTML 可打开、截图齐全、lint 红灯为零；`direction-approved.md` 含用户选择原话。

**DoD**

- [ ] 未在批准前写 Compose UI 或修改现有设置页。
- [ ] 未新增颜色、字体或组件库；文案为真实中文/繁中/英文，不用占位。
- [ ] 一卡一提交：`T-UPD-04: <一句话>`。

**禁止**

- 不要用「用户说继续」代替选择；未明确选定就停住。
- 不要把三版做成同一模板换色。

---

## T-UPD-05 清单 v2 与三语发布说明

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-03 |
| 需求 | REQ-SET-012 |
| 预估 | 0.5 天 |

**目标**：让 `downloads.json` 携带三语发布说明，并保证任何说明文本都不会破坏 JSON。

**必读**：`scripts/generate-downloads-manifest.sh`、`docs/adr/0009-auto-publish-downloads.md`、`docs/adr/0010-app-update-and-install.md` §3。

**涉及路径**：`scripts/generate-downloads-manifest.sh`、`release-notes/v1.1.0/zh-CN.md`、`release-notes/v1.1.0/zh-TW.md`、`release-notes/v1.1.0/en.md`。

**实现要点**

- `schemaVersion` 升为 2，新增 `notes` 三键；其余平台字段不变。
- 新增 `--notes-dir`，默认 `release-notes/<version>`；`zh-CN.md` 必需，`zh-TW.md` / `en.md` 缺失时回退 `zh-CN`。
- 单个说明文件 >64 KiB 直接失败；发布说明只接受纯文本与 `- ` 列表，不渲染 Markdown。
- JSON 转义覆盖引号、反斜杠、制表符、CRLF 与换行；用包含这些字符的夹具生成后由 `python3 -m json.tool` 解析。
- `--check` 忽略 `generatedAt`，其余字段含 `notes` 全量比对；`--notes-dir` 默认值使现有 release CI 无需改动即可生成 v2 清单。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
bash -n scripts/generate-downloads-manifest.sh
tmp="$(mktemp -d)"
mkdir -p "$tmp/dist" "$tmp/notes"
for f in theca-android.apk theca-macos.dmg theca-windows.msi theca-linux.deb; do
  printf 'x' > "$tmp/dist/$f"
done
printf '标题 "引号" 与 \\ 反斜杠\n- 第一项\n\n第二段\r\n' > "$tmp/notes/zh-CN.md"
printf '繁體說明\n' > "$tmp/notes/zh-TW.md"
scripts/generate-downloads-manifest.sh \
  --dist "$tmp/dist" --version 1.1.0 --notes-dir "$tmp/notes" \
  --output "$tmp/downloads.json"
python3 -m json.tool "$tmp/downloads.json" >/dev/null
python3 - <<'PY' "$tmp/downloads.json"
import json, sys
m = json.load(open(sys.argv[1]))
assert m["schemaVersion"] == 2
assert m["notes"]["zh-CN"].startswith("标题 \"引号\"")
assert m["notes"]["en"] == m["notes"]["zh-CN"]
PY
scripts/generate-downloads-manifest.sh \
  --dist "$tmp/dist" --version 1.1.0 --notes-dir "$tmp/notes" \
  --check "$tmp/downloads.json"
```

预期：生成与 `--check` 均通过；英文缺失回退简中；含引号/反斜杠/CRLF 的说明可解析。

**DoD**

- [ ] 不依赖 jq 或新增运行时依赖。
- [ ] 说明文件不写内部路径、令牌或未发布内容。
- [ ] 一卡一提交：`T-UPD-05: <一句话>`。

**禁止**

- 不要把发布说明渲染成 HTML 或引入 Markdown 依赖。
- 不要在清单里写入下载 URL。

---

## T-UPD-06 服务端更新描述接口

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-05 |
| 需求 | REQ-SET-012、NFR-017 |
| 预估 | 1 天 |

**目标**：实现公开、无鉴权的 `GET /api/v1/app/latest`，把 schema v2 清单投影为客户端可用的更新描述。

**必读**：`docs/api.md` §13、`docs/服务端技术架构.md` §7.6/§8.3/§11.7、现有 `Application.kt`、`plugins/RateLimits.kt`、`plugins/Security.kt`。

**涉及路径**：`ClipboxServer/src/main/kotlin/.../service/UpdateManifestService.kt`（新增）、`routes/UpdateRoutes.kt`（新增）、`Application.kt`、`plugins/RateLimits.kt`、`ClipboxServer/src/test/.../integration/UpdateApiIntegrationTest.kt`（新增）及必要测试夹具。

**实现要点**

- 读取 `config.downloadDir/downloads.json`；每次请求重新读取，不做进程内缓存。
- 校验 schema 2、版本号、`generatedAt`、五平台不重不漏、`file` 为纯文件名、`sizeBytes > 0`、`sha256` 为 64 位小写十六进制；任一失败 503，不泄露路径。
- `downloadUrl = config.publicBaseUrl + "/downloads/" + file`；`notes` 缺 `zh-TW` / `en` 时回退 `zh-CN`，缺 `zh-CN` 或超 64 KiB 时 503。
- `RateLimitSettings` 新增 `appLatestPerMinute = 60`，`RateLimiters` 新增独立实例；GET 与 HEAD 合并按 IP 计数，429 带 `Retry-After`。
- 路由接入 `Application.kt`；`/api/*` 已由 `Security.kt` 返回 `no-store`，只做回归验证。
- 日志只记录状态码与错误码，不记录说明、文件名、URL、请求头或 IP。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxServer
./gradlew test
```

预期：新增集成用例覆盖正常、schema 1、缺失、坏 JSON、坏 SHA、平台缺失/重复、说明回退、HEAD、no-store、429；既有用例全绿。

**DoD**

- [ ] 不新增第三方依赖、不改数据库 schema。
- [ ] 清单缺失时接口 503，服务端其余功能不受影响。
- [ ] 一卡一提交：`T-UPD-06: <一句话>`。

**禁止**

- 不要回退旧清单、生成空描述或缓存非法内容。
- 不要让接口读取配对令牌、用户地址或环境变量之外的运行配置。

---

## T-UPD-07 共享更新层

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-03 |
| 需求 | REQ-SET-012、AC-26 |
| 预估 | 1 天 |

**目标**：在 `shared/commonMain` 提供三端共用的 DTO、版本比较、SHA-256 与无令牌更新 API。

**必读**：`docs/客户端技术架构.md` §6.10、`docs/api.md` §13、现有 `ApiHttpClient` 与 `PlatformServices`。

**涉及路径**：`ClipboxClient/shared/src/commonMain/kotlin/com/wangxingxing/clipbox/update/`（新增 `UpdateModels.kt`、`Version.kt`、`Sha256.kt`、`UpdateApi.kt`）、对应 `commonTest`。

**实现要点**

- DTO 使用 kotlinx.serialization，忽略未知字段；`notes` 解析为三键，缺失时回退 `zh-CN`。
- `compareVersions` 只接受纯数字点分版本；非法版本返回不可比较，不抛异常。
- 纯 Kotlin SHA-256，流式读取文件，在 `Dispatchers.Default` 执行；用 NIST 向量测试。
- `UpdateApi` 使用独立 `HttpClient`，base URL 只取 `PlatformServices.defaultServerUrl`，不装 Auth 插件，不读取设置地址。
- 超时与 503 映射为可区分的失败结果；不记录 URL、说明或响应体。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :shared:allTests
```

预期：版本比较、说明回退、SHA-256 向量、MockEngine 无 `Authorization` 与固定 base URL 全部通过。

**DoD**

- [ ] 不新增 Gradle 依赖；不复制 `ApiHttpClient` 的令牌逻辑。
- [ ] 新 Kotlin 文件带统一文件头；注释只写约束。
- [ ] 一卡一提交：`T-UPD-07: <一句话>`。

**禁止**

- 不要读取 `settings.serverUrl`、`initialServerUrl()` 或配对会话。
- 不要把 `notes`、下载 URL 写入日志。

---

## T-UPD-08 更新控制器与设置开关

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-07 |
| 需求 | REQ-SET-012、AC-21/AC-22 |
| 预估 | 1 天 |

**目标**：用进程级控制器驱动自动检查、手动检查、红点与状态机，并把 `autoUpdateCheck` 落入设置。

**必读**：`docs/客户端技术架构.md` §6.9/§6.10/§9.4、`AppContainer.kt`、`SettingsRepository.kt`。

**涉及路径**：`ClipboxClient/shared/.../update/UpdateController.kt`、`UpdateUiState.kt`（新增）、`settings/SettingsRepository.kt`、`app/AppContainer.kt`、对应测试。

**实现要点**

- `AppContainer` 创建唯一 `UpdateController`，使用 `services.appScope`；会话重建只替换 `AppSession`，控制器不重建；`close()` 调用 `updateController.close()`。
- `AppContainer.start()` 成功后调用一次 `onAppStart()`；`autoCheckDone` 保证每进程只自动检查一次。
- `autoUpdateCheck` 默认 `true`；关闭后跳过自动检查，手动检查仍可用。
- 自动检查失败静默；手动失败进入 `Failed`；`hasUnseenUpdate` 仅内存，打开弹窗置 false。
- 状态机严格按 `Idle / Checking / Latest / Available / Downloading / Verifying / Ready / Failed / Unsupported` 迁移，不新增忽略或强制状态。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :shared:allTests
grep -n "autoUpdateCheck" shared/src/commonMain/kotlin/com/wangxingxing/clipbox/settings/SettingsRepository.kt
```

预期：自动检查仅一次、关闭开关、503/离线静默、手动失败、红点清除与状态迁移用例通过。

**DoD**

- [ ] 更新能力不依赖配对状态；未配对也可检查。
- [ ] 不新增设置项之外的 UI 文案。
- [ ] 一卡一提交：`T-UPD-08: <一句话>`。

**禁止**

- 不要把控制器放进 `AppSession` 或在会话重建时重置下载状态。
- 不要自动下载、自动安装或弹出强制更新。

---

## T-UPD-09 Android 下载与安装

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-06、T-UPD-08 |
| 需求 | REQ-SET-013、AC-23 |
| 预估 | 1 天 |

**目标**：Android 用系统 DownloadManager 下载 APK，SHA-256 校验后由用户点击拉起系统安装器。

**必读**：`docs/客户端技术架构.md` §6.10/§13.1、`docs/adr/0010-app-update-and-install.md` §4、现有 AndroidManifest 与入口。

**涉及路径**：`ClipboxClient/composeApp/src/androidMain/AndroidManifest.xml`、`res/xml/file_paths.xml`（新增）、`androidMain/.../update/`（新增下载与安装实现）、Android 入口装配。

**实现要点**

- Manifest 只新增 `android.permission.REQUEST_INSTALL_PACKAGES`；不新增通知、存储、前台服务权限。
- FileProvider authority `${applicationId}.fileprovider`，路径 `<external-files-path name="updates" path="Download/updates/" />`，`exported=false`、`grantUriPermissions=true`。
- DownloadManager 写入 `Download/updates/<file>`，MIME 为 APK，`VISIBILITY_VISIBLE_NOTIFY_COMPLETED`；标题与描述取 `composeResources`。
- 持久化 download ID，重启后查询并恢复；`ACTION_DOWNLOAD_COMPLETE` 接收器按 ID 匹配；失败/暂停映射 `Failed`，重试前清理旧 ID 与旧文件。
- 校验通过后等待用户点「安装」；`canRequestPackageInstalls()` 为 false 时跳转 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，返回后重试。
- 安装使用 `FileProvider.getUriForFile` + `ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`；不静默安装、不自动重启。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

手工 AC-23：本地服务端放置 v1.1.0 清单与 APK 夹具 → 下载 → SHA 通过 → 未知来源引导 → 系统安装器 → 覆盖安装；篡改 SHA 后文件被删除并提示失败。

预期：权限列表只多安装权限；下载通知由系统下载器发布；安装器由用户点击后出现。

**DoD**

- [ ] 不申请 `POST_NOTIFICATIONS`、不使用前台服务或 WorkManager。
- [ ] API 26 与 API 35 均完成一次安装路径验证。
- [ ] 一卡一提交：`T-UPD-09: <一句话>`。

**禁止**

- 不要用 `REQUEST_INSTALL_PACKAGES` 做静默安装或绕过未知来源设置。
- 不要在日志中写下载 URL、文件路径或说明内容。

---

## T-UPD-10 Desktop 下载与打开安装包

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-06、T-UPD-08 |
| 需求 | REQ-SET-013、AC-24 |
| 预估 | 1 天 |

**目标**：Desktop 应用内流式下载安装包，关闭弹窗继续，退出应用取消并清理 `.part`，校验后打开 DMG/MSI/DEB。

**必读**：`docs/客户端技术架构.md` §6.10/§13.1、`docs/adr/0010-app-update-and-install.md` §4、`composeApp/src/desktopMain/.../Main.kt`。

**涉及路径**：`ClipboxClient/composeApp/src/desktopMain/.../update/`（新增）、`Main.kt`、Desktop 装配。

**实现要点**

- Ktor 流式 GET，写入应用数据目录 `updates/<file>.part`，完成后原子重命名；进度推送 `UpdateController`。
- 关闭弹窗不取消下载；退出应用调用 `UpdateController.close()` 取消并删除 `.part`。
- SHA-256 校验通过进入 `Ready`；失败删除文件并进入 `Failed`。
- 安装使用 `Desktop.open(file)`；失败展示路径与「打开所在文件夹」/「复制路径」。
- 启动时清理旧版本文件与残留 `.part`，只保留当前版本；路径基于用户目录，不写死。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :composeApp:packageDmg
./gradlew :composeApp:run
```

手工 AC-24：下载中关闭弹窗 → 进度继续；退出应用 → `.part` 被清理；完成后打开 DMG。Windows / Linux 由对应平台包验证 MSI / DEB。

预期：macOS 上 DMG 可打开；退出后无残留临时文件；校验失败不留下安装包。

**DoD**

- [ ] 不申请系统权限、不写工作目录之外的自定义位置。
- [ ] 关闭窗口与应用退出的清理语义区分明确。
- [ ] 一卡一提交：`T-UPD-10: <一句话>`。

**禁止**

- 不要在弹窗关闭时取消下载。
- 不要把安装包路径或 URL 写入日志。

---

## T-UPD-11 iOS 只读更新展示

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-06、T-UPD-08 |
| 需求 | REQ-SET-013、AC-25 |
| 预估 | 0.5 天 |

**目标**：iOS 复用共享层检查更新，只展示版本、日期与三语说明，不下载、不安装。

**必读**：`docs/客户端技术架构.md` §7.8、`docs/adr/0010-app-update-and-install.md` §4、`docs/tasks/04-ios.md` 的 UITest 命令。

**涉及路径**：`ClipboxClient/composeApp/src/iosMain/` 更新展示适配、iOS 宿主装配、UITest（如现有测试覆盖需要）。

**实现要点**

- iOS 只调用更新 API；不修改 ATS，不新增下载代码或文件访问。
- 弹窗展示版本、发布日期、当前语言说明（缺失回退简中）与「iOS 暂未提供安装包」。
- 按钮只有「知道了」；关闭后红点已清除，不出现下载或安装入口。
- 平台 `available=false` 或缺失时仍可展示版本说明，不报错。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

再按 `04-ios.md` 的 iPhone 16 / iOS 27.2 UITest 命令跑一次更新展示；截图为证。

预期：iOS 无下载按钮；说明与版本可见；关闭后重开仍显示有新版本。

**DoD**

- [ ] 不新增 iOS 权限、不修改 ATS 例外。
- [ ] 不展示 App Store、TestFlight 或外部跳转。
- [ ] 一卡一提交：`T-UPD-11: <一句话>`。

**禁止**

- 不要为 iOS 实现安装包下载或打开。

---

## T-UPD-12 设置更新区、弹窗与三语文案

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-04、T-UPD-08、T-UPD-09/10/11 |
| 需求 | REQ-SET-012/013、AC-21、AC-25 |
| 预估 | 1 天 |

**目标**：按已批准的设计方向落地设置页更新区、根级更新弹窗与导航红点，三端共用一套 Compose UI。

**必读**：`design/direction-approved.md`、`docs/客户端技术架构.md` §7.8、现有 `SettingsScreen.kt`、`ClipboxNavigation.kt`、`ClipboxTokens`。

**涉及路径**：`composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt`、`ui/navigation/ClipboxNavigation.kt`、根组合与更新弹窗（新增）、三份 `composeResources` 文案。

**实现要点**

- 设置页在「关于」与「危险」之间插入「更新」区：自动检查开关（默认开）与检查更新行；检查中禁用。
- 底部栏与侧栏的设置项右上角 6dp 红点，使用 `ClipboxTokens.colors.accent`；不改变选中态。
- 根组合挂载 `UpdateDialog`，不新增导航路由；打开即清除红点，关闭不清除已下载文件或 `Ready` 状态。
- 状态文案覆盖检查中、已是最新、有新版本、风险确认、下载中、校验中、已就绪、失败、平台不可用、iOS 只读；失败错误码集中映射。
- 说明按纯文本展示并保留换行，超长可滚动；三语资源齐全，无硬编码文案。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox/ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
```

视觉走查：390px 与 1440px、明暗、简中/繁中/English、超长说明滚动、红点出现与清除；截图存 `design/updates/shots/`。

预期：布局与批准方向一致；三语无截断；iOS 无下载按钮；无红点闪烁。

**DoD**

- [ ] 未新增路由、未改冻结色板、未硬编码用户可见文案。
- [ ] 未在批准前提前实现（依赖 T-UPD-04）。
- [ ] 一卡一提交：`T-UPD-12: <一句话>`。

**禁止**

- 不要加入「忽略此版本」「强制更新」「稍后提醒」等未定义交互。
- 不要把说明渲染为 Markdown 或 HTML。

---

## T-UPD-13 版本线升到 1.1.0

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-UPD-06、T-UPD-08、T-UPD-09/10/11/12 |
| 需求 | AC-23、AC-24 |
| 预估 | 0.5 天 |

**目标**：四端版本号统一为 `1.1.0`，Android `versionCode 2`，Desktop 与 iOS 构建号同步。

**必读**：`scripts/check-release-version.sh`、`ClipboxClient/composeApp/build.gradle.kts`、iOS 工程文件、`ClipboxWeb/src/data/downloads.ts`。

**涉及路径**：`ClipboxClient/composeApp/build.gradle.kts`、Desktop `BundledConfig` / `PlatformModule.desktop.kt`、iOS `project.pbxproj`、`ClipboxWeb/src/data/downloads.ts`、`scripts/check-release-version.sh`（如需同步清单口径）。

**实现要点**

- Android `versionCode = 2`、`versionName = "1.1.0"`。
- Desktop `packageVersion = "1.1.0"`；`BundledConfig` 增加 `appVersion`，`PlatformModule.desktop.kt` 不再硬编码版本。
- iOS `MARKETING_VERSION = 1.1.0`、`CURRENT_PROJECT_VERSION = 2`。
- 官网 `VERSION = '1.1.0'`；`check-release-version.sh` 对四处与 tag 做一致性校验。
- 升级安装必须沿用 v1.0.0 的 release 签名配置，不做签名切换。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
./scripts/check-release-version.sh v1.1.0
```

预期：`android=1.1.0 desktop=1.1.0 ios=1.1.0 web=1.1.0`、`OK: 1.1.0`。

**DoD**

- [ ] 四端版本与 tag 完全一致；未打 tag 前不推送。
- [ ] 未改依赖、未改签名配置。
- [ ] 一卡一提交：`T-UPD-13: <一句话>`。

**禁止**

- 不要只改一处版本号或在构建脚本中保留旧版本常量。
- 不要为通过校验修改校验脚本的判定口径。

---

## T-UPD-14 全链路验收与发布顺序

| 字段 | 值 |
| --- | --- |
| 里程碑 | M7 |
| 前置 | T-WEB-12、T-UPD-13 |
| 需求 | AC-21…AC-26 |
| 预估 | 1 天 |

**目标**：按固定顺序完成 v1.1.0 发布，并留下可复查的更新链路证据。

**必读**：`docs/tasks/05-release.md`、`docs/tasks/release-artifacts.md`、`docs/adr/0009-auto-publish-downloads.md`、本文件全部卡片。

**涉及路径**：`docs/tasks/05-release.md`、`docs/tasks/release-artifacts.md`、`docs/tasks/07-update.md`（记录证据）。

**实现要点**

- 发布顺序：T-WEB-12 → 服务端部署 → 版本/发布说明/tag → GitHub Release → `deploy-site` 清单 v2 → 在线验证 → 公告。
- 在线验证：`app/latest` 与 `downloads.json` 均为 `no-store`；四平台 `downloadUrl` 可下载；清单 SHA 与 `SHA256SUMS.txt`、实际文件一致；61 次请求触发 429。
- AC-21…AC-26 逐条记录命令、截图与结果；回归 AC-01…AC-20 冒烟。
- 记录更新链路：冷启动红点、打开清除、关闭再开仍提示；离线/503 静默与手动失败。
- 在发布记录中写明 HTTP + 未签名的已知局限与回滚方式。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
./scripts/check-release-version.sh v1.1.0
curl -fsSI http://<SERVER_IP>:8080/api/v1/app/latest
curl -fsSI http://<SERVER_IP>:8080/downloads/downloads.json
curl -fsS -H 'Range: bytes=0-1023' -o /dev/null -w '%{http_code}\n' \
  http://<SERVER_IP>:8080/downloads/<file>
```

预期：两个描述接口 200 且 `no-store`；下载 206；清单版本为 1.1.0；AC 证据齐全。

**DoD**

- [ ] 发布顺序未被跳过；清单 v2 上线前接口保持 503 的预期已记录。
- [ ] 未把服务器 IP、令牌或私钥写入仓库。
- [ ] 一卡一提交：`T-UPD-14: <一句话>`。

**禁止**

- 不要在未授权时 push、打 tag 或执行远程不可逆操作。
- 不要用「页面能打开」代替 SHA、Range 与安装链路验收。
