# 客户端共享层任务卡（T-CORE）

工程：`ClipboxClient/`，KMP 根工程，包名 `com.wangxingxing.clipbox`。
技术基线：Compose Multiplatform + Room KMP + Ktor Client + DataStore + Koin + kotlinx.serialization/datetime。
接口唯一依据：`docs/api.md`；实现细节：`docs/客户端技术架构.md`。

所有卡的验收命令默认在 `ClipboxClient/` 下执行。测试任务名以 T-CORE-01 实际生成的 Gradle 任务为准；本文使用 `:shared:allTests` 作为约定名。

---

## T-CORE-01 KMP 工程骨架与三端入口

| 字段 | 值 |
| --- | --- |
| 里程碑 | M0 |
| 前置 | 无 |
| 需求 | REQ-PLAT-001…009 |
| 预估 | 1 天 |

**目标**：从空目录建立可构建、可启动的 KMP 工程：`composeApp`（UI）+ `shared`（共享层），Android / Desktop / iOS 三端目标齐全。

**必读**：`docs/客户端技术架构.md` §4、§5、§7.1–§7.2；`design/README.md` 输出尺寸。

**涉及路径**：`ClipboxClient/settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、`composeApp/`、`shared/`、`gradle.properties`。

**实现要点**

- 以 JetBrains KMP 模板初始化，锁定版本到 version catalog；不追新，不升级模板之外依赖。
- 模块：`composeApp`（Compose UI、平台入口、平台实现）、`shared`（model / network / db / sync / repository / settings / util）。
- Android：namespace 与 applicationId `com.wangxingxing.clipbox`，minSdk 26，targetSdk 用模板稳定值；`MainActivity` 启动空 `App()`。
- Desktop：`Main.kt` 启动窗口，默认 1000×700，最小 640×480。
- iOS targets：`iosArm64` / `iosSimulatorArm64` / `iosX64`，静态 framework；`MainViewController.kt` 暴露 `ComposeUIViewController { App() }`。Xcode 工程在 T-IOS-02 创建，本卡只需 framework 链接任务可执行。
- `shared` commonMain 加入 kotlinx-coroutines、serialization、datetime；UI 用 Compose Material 3。
- 依赖注入：Koin 基础模块占位，后续卡按模块注册。
- 根 `README.md` 记录三端构建命令。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
./gradlew :shared:allTests
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

预期：Android debug 构建成功；Desktop 窗口出现并显示占位文案；共享层测试任务通过（可 0 个测试）；iOS framework 链接成功。

**DoD**

- [ ] `applicationId` / namespace / 包目录均为 `com.wangxingxing.clipbox`。
- [ ] 三端入口都存在且可启动。
- [ ] 版本全部来自 version catalog。

**禁止**

- 不要引入 Hilt / Dagger、导航框架或未在文档中出现的第三方库。
- 不要创建 `server` 模块或跨仓库依赖。

---

## T-CORE-02 领域模型、DTO、ULID 与错误类型

| 字段 | 值 |
| --- | --- |
| 里程碑 | M0 |
| 前置 | T-CORE-01 |
| 需求 | REQ-SEND-001/008、REQ-SYNC-001…004 |
| 预估 | 0.5–1 天 |

**目标**：共享层拥有全部领域模型、API DTO、ULID 生成、UTF-8 字节计数与统一错误类型。

**必读**：`docs/api.md` §1.1、§1.3、§1.5、§3、§4、§5；`docs/客户端技术架构.md` §6.1。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/model/`、`.../util/`、`shared/src/commonTest/`。

**实现要点**

- 领域模型：`Clip`（id、content、sourceDeviceId、sourceDeviceName、pinned、createdAt、deleted、syncState、lastError）、`SyncState`（SYNCED / PENDING / DELETE_PENDING / FAILED_*）、`DeviceInfo`、`ShareInfo`。
- DTO：与 `api.md` 字段逐一对齐，`@Serializable`；未知字段忽略；`deleted` 墓碑只含 id/serverSeq/deleted。
- `Ulid`：26 字符 Crockford Base32、统一大写、时间有序；提供 `generate(now)` 与格式校验；单测覆盖正则、排序、时钟回拨。
- `TextSize`：UTF-8 字节计数（不是 `String.length`），用于 64 KiB 校验与界面计数。
- 错误：`ApiErrorCode` 枚举覆盖 `api.md` §7；`ApiFailure` 分层为可重试 / 永久 / 401 / 429 / 网络离线。
- `SyncStatus` 与 `SyncFailure` 类型，供 UI 与 SyncEngine 共用。
- 所有工具函数纯 Kotlin，不依赖平台与 Compose。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*Ulid*' --tests '*TextSize*' --tests '*Dto*'
```

预期：ULID 正则与排序、中文 / emoji 字节计数、DTO 反序列化（含未知字段）全部通过。

**DoD**

- [ ] DTO 字段名与 `api.md` 完全一致，不改名、不省略。
- [ ] 不存在 `String.length` 充当字节数的实现。
- [ ] 模型层不依赖 Compose / Room / Ktor。

**禁止**

- 不要在 DTO 中加入数据库字段或 UI 状态字段。
- 不要使用随机 UUID 替代 ULID。

---

## T-CORE-03 DataStore 设置与服务器地址校验

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-02 |
| 需求 | REQ-SET-001/002/003/004/005/009/011、REQ-PAIR-002 |
| 预估 | 0.5–1 天 |

**目标**：设置项可持久化、可观察；服务器地址校验与未加密警示标志可用。

**必读**：`docs/客户端技术架构.md` §6.2；`docs/功能需求文档.md` REQ-SET-001、REQ-SET-005、REQ-SET-009。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/settings/`、平台 DataStore 实现、`shared/src/commonTest/`。

**实现要点**

- 键与默认值严格按架构文档 §6.2：`serverUrl`、`deviceToken`、`deviceId`、`deviceName`、`lastServerUrl`、`autoPaste=true`、`autoClipboardSend=false`、`themeMode=system`、`appLanguage=zh-Hans`、`diagnosticLoggingEnabled`（debug 默认 true、release 默认 false）。
- 地址规范化：允许 `http://` / `https://`，必须含 scheme 与主机，去除末尾斜杠；缺少 scheme 或主机时返回校验错误。
- `isInsecureServerUrl`：`http://` 为 true；设置页与配对页据此展示「未加密连接，仅用于学习」。
- 提供清空配对信息（token / deviceId / lastServerUrl）但保留主题与语言的方法。
- 使用 `expect/actual` 或平台注入创建 DataStore，路径在应用沙箱内。
- 测试：默认值、规范化、非法地址、http 警示、清空令牌不影响外观设置。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*Settings*' --tests '*ServerUrl*'
```

预期：全绿；输入 `203.0.113.10:8080` 返回格式错误；输入 `http://203.0.113.10:8080/` 规范化为无尾斜杠且标记未加密。

**DoD**

- [ ] 首次启动写入 `appLanguage=zh-Hans`，不读取系统语言。
- [ ] 诊断日志默认值按构建类型确定。
- [ ] 设置变更可被 Flow 观察，UI 无需轮询。

**禁止**

- 不要把令牌写入 SharedPreferences / 明文文件之外的第三方存储。
- 不要在地址校验中强制 HTTPS。

---

## T-CORE-04 Ktor HTTP 客户端与 ApiService

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-03 |
| 需求 | REQ-PAIR-002…005、REQ-SYNC-001、REQ-SEND-001、REQ-SHARE-001 |
| 预估 | 1 天 |

**目标**：三端可用的 HTTP 客户端与全部 REST 接口封装，错误映射统一。

**必读**：`docs/api.md` §1–§5；`docs/客户端技术架构.md` §6.3.1。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/network/`、各平台引擎实现、`shared/src/commonTest/`。

**实现要点**

- Ktor Client 引擎：Android OkHttp、iOS Darwin、Desktop CIO；平台模块注入。
- 插件：ContentNegotiation（`ignoreUnknownKeys=true`）、DefaultRequest（`serverUrl + /api/v1`）、Auth Bearer、超时、UA。
- `ApiService`：`pair`、`getClips(since, limit)`、`createClip`、`updateClip`、`deleteClip`、`createShare`；不实现 `before` 翻页与搜索。
- 错误：解析 `{"error":{"code","message"}}` 为 `ApiFailure`；网络异常映射为离线；401 / 429 / 4xx / 5xx 分类。
- 请求 / 响应体不默认打印；诊断日志接入点在 T-CORE-13。
- 使用 Ktor `MockEngine` 编写 commonTest：成功、401、409、413、429 + `Retry-After`、未知字段、非法 JSON。
- 服务器地址变更后客户端实例重建（由 T-CORE-15 编排）。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ApiService*' --tests '*ErrorMapping*'
```

预期：全绿；无真实网络请求；429 解析出 `retryAfterSeconds`。

**DoD**

- [ ] 所有路径与 `api.md` 一致。
- [ ] Bearer 头只在设备接口出现，配对接口不带令牌。
- [ ] 错误映射可供 Outbox 直接分支。

**禁止**

- 不要在 API 层修改本地数据库。
- 不要吞掉错误或返回 null 表示失败。

---

## T-CORE-05 Room Schema、DAO 与本地裁剪

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-02 |
| 需求 | REQ-HIST-001…006/010、REQ-SYNC-009/010 |
| 预估 | 1 天 |

**目标**：本地库成为唯一数据源：记录、Outbox、同步元数据的表与查询全部落地。

**必读**：`docs/客户端技术架构.md` §6.1.2–§6.1.4；`docs/功能需求文档.md` HIST 域。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/db/`、平台 Room 数据库构建、`shared/src/commonTest/` 或 `desktopTest`。

**实现要点**

- `clips`：服务端字段 + `sync_state`、`last_error`、`updated_at`；索引 `created_at`、`pinned`、`sync_state`。
- `outbox`：`op_id`、`clip_id`、`type`（POST / PATCH / DELETE）、`payload`、`retry_count`、`next_retry_at`、`created_at`；同一 `clip_id` 保持入队顺序。
- `meta`：`last_seq` 等键值；默认 0。
- DAO：`observeVisibleClips()`（全部置顶 + 最近 200 条非置顶）、`searchClips(query)`、`getById`、`upsert`、`deleteById`、`trimLocal()`、Outbox 的 `nextDue` / `markDone` / `markRetry`。
- 本地裁剪只删除非置顶且超出 200 条的 `SYNCED` 记录；不删除待同步 / 失败 / 置顶记录，不向服务端传播。
- 数据库版本 1；预留迁移入口，不写空迁移。
- 测试：置顶排序、200 条裁剪、搜索大小写不敏感、Outbox 顺序、重启后数据保留。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ClipDao*' --tests '*OutboxDao*' --tests '*Trim*'
```

预期：全绿；裁剪测试展示裁剪前后条数与置顶保留。

**DoD**

- [ ] UI 只通过 DAO / Repository 观察数据。
- [ ] 所有写操作支持事务。
- [ ] 表名、字段名与架构文档一致。

**禁止**

- 不要把服务器返回数据直接推给 UI。
- 不要在本地裁剪时删除置顶或未同步记录。

---

## T-CORE-06 写入路径与 Outbox 入队

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-CORE-05 |
| 需求 | REQ-SEND-001…009、REQ-HIST-003/007、REQ-SYNC-005/006 |
| 预估 | 1 天 |

**目标**：发送、置顶、删除全部先落本地并在同一事务进入 Outbox，UI 立即可见。

**必读**：`docs/客户端技术架构.md` §6.5；`docs/功能需求文档.md` SEND / HIST 域。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/repository/ClipRepository.kt`、`shared/src/commonTest/`。

**实现要点**

- `send(content)`：trim 首尾、保留内部空白；空白拒绝；UTF-8 字节 >65536 拒绝；生成 ULID 与 `createdAt`；插入 `PENDING` 记录 + Outbox POST，同一 Room 事务。
- `pin(id, pinned)`：本地立即更新并置 `PENDING`；入队 PATCH；同一记录已有待发操作时按顺序追加，不并发覆盖。
- `delete(id)`：本地立即标记 `DELETE_PENDING`（UI 隐藏或标记删除），入队 DELETE。
- 合并连续操作：同一记录未发送的 PATCH 可合并；POST 未完成前禁止跳过 POST 发 PATCH/DELETE（保持顺序）。
- Repository 暴露 `observeClips()`、`send()`、`pin()`、`delete()`、`retry(id)`；不直接调用网络。
- 测试：本地立即可见、事务原子性（模拟失败时无半条 Outbox）、超限拒绝、连续置顶只产生一次最终操作、删除后重试。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ClipRepository*' --tests '*OutboxEnqueue*'
```

预期：全绿；断网状态下发送仍返回成功并留下 PENDING 记录。

**DoD**

- [ ] 写入本地与入队在同一事务。
- [ ] 64 KiB 校验在 Repository 层，UI 只是提前提示。
- [ ] 不产生重复 Outbox 记录。

**禁止**

- 不要在发送时同步等待网络结果。
- 不要为离线写入创建内存队列。

---

## T-CORE-07 Outbox 重放、退避与失败状态

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-06 |
| 需求 | REQ-SYNC-005…008、REQ-HIST-008 |
| 预估 | 1 天 |

**目标**：Outbox 自动重放，正确区分可重试、永久失败、401 与 429；失败可由用户手动重试。

**必读**：`docs/客户端技术架构.md` §6.5.2–§6.5.4；`docs/api.md` §7、§8。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/sync/OutboxWorker.kt`、`shared/src/commonTest/`。

**实现要点**

- 循环：取到期操作 → 执行 → 成功删除并更新本地 `SYNCED`；失败按类型处理。
- 退避：1s → 2s → 4s … 上限 60s，±20% 抖动；429 优先使用 `Retry-After`；网络错误使用退避。
- 401：暂停 worker，标记未配对状态，等待重新配对；不把记录标为永久失败。
- 永久失败：400 / 409 / 413 / 415 标记 `FAILED_*`，保留本地记录与错误码，停止自动重试。
- 手动 `retry(id)`：重置重试计数、清除失败状态、重新入队。
- 同一时刻只有一个 worker；与其他同步任务共享互斥锁（T-CORE-08 接入）。
- 可注入时钟与 `ApiService` 假实现，测试退避时间点、429、401、永久失败、手动重试。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*OutboxWorker*' --tests '*Backoff*'
```

预期：全绿；测试可断言第 1/2/3 次重试的时间间隔与抖动范围。

**DoD**

- [ ] 重启后 Outbox 继续重放。
- [ ] 永久失败不无限重试。
- [ ] 401 不丢记录、不标失败。

**禁止**

- 不要在 UI 线程执行重放。
- 不要用固定间隔轮询忽略 `next_retry_at`。

---

## T-CORE-08 增量拉取、游标推进与全量重同步

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-05、T-CORE-07 |
| 需求 | REQ-SYNC-001…004、REQ-SYNC-009…015 |
| 预估 | 1 天 |

**目标**：客户端按 `serverSeq` 增量拉取，正确处理分页、墓碑、缺口与强制重同步。

**必读**：`docs/api.md` §3.1、§9；`docs/adr/0001-server-seq-change-feed.md`。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/sync/SyncEngine.kt`、`shared/src/commonTest/`。

**实现要点**

- `pull()`：从 `meta.last_seq` 开始，`limit=200` 循环到 `hasMore=false`；每页按 id upsert，墓碑物理删除本地记录。
- `lastSeq` 只由 `nextSince` 推进；空页保持不变。
- `resyncRequired=true`：保留 `PENDING` / `DELETE_PENDING` / `FAILED_*` 与 Outbox，删除本地 `SYNCED` 记录，从 0 全量拉取，完成后写回最新 `nextSince`。
- 服务端 `since > serverMaxSeq`（恢复旧备份）同样触发全量重同步。
- 与 Outbox 共享单一互斥锁，避免拉取覆盖本地待发状态。
- 测试：多页、空页、墓碑、`resyncRequired`、游标不因写响应推进、全量重同步保留待发记录。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*SyncEnginePull*' --tests '*Resync*'
```

预期：全绿；测试覆盖 `lastSeq` 在所有分支的取值。

**DoD**

- [ ] 任何写接口响应都不推进 `lastSeq`。
- [ ] 全量重同步不清空 Outbox。
- [ ] 重同步期间 UI 仍可读本地记录。

**禁止**

- 不要用 `createdAt` 或 ULID 推断同步进度。
- 不要删除用户未同步的记录。

---

## T-CORE-09 WebSocket 客户端、生命周期与缺口补拉

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-04、T-CORE-08 |
| 需求 | REQ-RECV-001/002/007、REQ-SYNC-007/008 |
| 预估 | 1 天 |

**目标**：三端可建立 WS 连接并接收实时事件；断线重连并补齐缺口。

**必读**：`docs/api.md` §4；`docs/客户端技术架构.md` §6.4。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/network/SyncSocket.kt`、`.../sync/SyncEngine.kt`、`shared/src/commonTest/`。

**实现要点**

- 连接地址由 `serverUrl` scheme 决定：`http` → `ws`，`https` → `wss`，追加 `/api/v1/sync`。
- 认证：连接后 5 秒内发 `{"type":"auth","token"}`；等待 `auth.ok` 并读取 `serverSeq`；失败按 4401 处理（暂停，等待重新配对）。
- 重连退避与 Outbox 相同区间；前台恢复时先 REST 补拉再连接。
- 事件应用：`clip.created` / `clip.updated` → upsert；`clip.deleted` → 删除本地；`seq == lastSeq + 1` 才推进 `lastSeq`；`seq > lastSeq + 1` 触发 REST 补拉；`seq <= lastSeq` 忽略。
- 事件与 REST 共用互斥锁，避免并发覆盖。
- 生命周期接口由平台层调用：`onForeground()`、`onBackground()`；本卡只实现共享逻辑与假平台测试。
- 测试：认证成功/失败、事件连续推进、缺口触发补拉、重复事件幂等、断线重连。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*SyncSocket*' --tests '*Gap*'
```

预期：全绿；缺口测试断言触发一次 REST 补拉且未重复推进游标。

**DoD**

- [ ] 不依赖 WS 重放。
- [ ] 401 / 4401 不进入无限重连。
- [ ] 事件处理不阻塞主线程。

**禁止**

- 不要在 WS 上发送上传请求。
- 不要乱序推进 `lastSeq`。

---

## T-CORE-10 本地与远端合并规则

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-07、T-CORE-08、T-CORE-09 |
| 需求 | REQ-SYNC-009/010、REQ-HIST-003/007 |
| 预估 | 0.5–1 天 |

**目标**：把「本地待发状态」与「远端事件 / 拉取结果」的合并规则集中到一处，避免各处重复判断。

**必读**：`docs/客户端技术架构.md` §6.1.6、§6.5；`docs/GLOSSARY.md`。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/sync/ClipMerger.kt`、`shared/src/commonTest/`。

**实现要点**

- 单一合并函数：输入本地记录（可空）、远端记录 / 事件、当前 Outbox 状态，输出应写入的本地记录。
- `SYNCED`：远端覆盖。
- `PENDING`：保留本地正文与置顶意图，不因远端旧状态回退；服务端确认后转 `SYNCED`。
- `DELETE_PENDING`：保留删除意图；远端更新不得复活记录；删除确认后移除。
- `FAILED_*`：保留失败状态，除非用户手动重试或远端墓碑到达。
- 墓碑优先于任何本地非删除状态，但未发送的本地新记录（服务端无此 id）不受影响。
- 纯函数、无 IO、可穷举测试；用状态矩阵表驱动测试。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ClipMerger*'
```

预期：全绿；状态矩阵每个组合都有断言。

**DoD**

- [ ] 合并规则只有一个实现，SyncEngine / Socket / Repository 都调用它。
- [ ] 没有散落的 `if (syncState == ...)` 复制逻辑。
- [ ] 测试覆盖墓碑、失败、删除中、置顶冲突。

**禁止**

- 不要在合并层调用网络或数据库。
- 不要引入 LWW 时间戳比较（仅 Snippet 未来使用）。

---

## T-CORE-11 同步状态与手动同步

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-CORE-10 |
| 需求 | REQ-SYNC-011、REQ-SET-002、REQ-RECV-003 |
| 预估 | 0.5 天 |

**目标**：UI 可观察稳定的同步状态，并支持用户手动触发同步。

**必读**：`docs/客户端技术架构.md` §6.5、§7.5；`docs/功能需求文档.md` SYNC 域。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/sync/SyncStatusHolder.kt`、`.../repository/SyncRepository.kt`、`shared/src/commonTest/`。

**实现要点**

- 状态：`Idle`、`Syncing`、`Offline`、`Pending(count)`、`Failed(count)`、`Unauthorized`；互斥且可组合（例如 Pending + Offline）。
- 状态由 Outbox 数量、最近错误、WS 连接状态、配对状态推导；不由 UI 拼接。
- `syncNow()`：触发一次 Outbox 重放 + REST 拉取；并发调用合并为一次。
- 状态流去重，避免 UI 频繁重组。
- 测试：各状态转换、并发 `syncNow` 只执行一次、失败后状态恢复。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*SyncStatus*'
```

预期：全绿；状态转换表与文档一致。

**DoD**

- [ ] UI 不直接读取 Outbox 表计算状态。
- [ ] 手动同步不阻塞调用线程。
- [ ] 401 状态明确区别于离线。

**禁止**

- 不要新增文档未定义的状态字符串。
- 不要把状态持久化到 DataStore。

---

## T-CORE-12 剪贴板服务与 Desktop 回环防护

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-CORE-06、T-CORE-09 |
| 需求 | REQ-RECV-002/003/004/005、REQ-SET-003/004、REQ-PLAT-003/009 |
| 预估 | 1 天 |

**目标**：三端剪贴板读写按平台限制工作；Desktop 自动粘贴 / 自动发送具备回环防护。

**必读**：`docs/客户端技术架构.md` §6.6；`docs/功能需求文档.md` RECV / SET 域。

**涉及路径**：`shared/src/commonMain/.../platform/ClipboardService.kt`、`composeApp/src/{androidMain,desktopMain,iosMain}/...ClipboardService.kt`、`shared/src/commonTest/`。

**实现要点**

- `expect` 接口：`readText()`、`writeText()`、`startWatching()`、`stopWatching()`；移动端 `startWatching` 为空实现。
- Android：仅前台用户操作读写；不监听、不后台读取。
- iOS：读取必须由用户点击「复制」或「从剪贴板粘贴」触发；不自动读。
- Desktop：AWT 系统剪贴板；500ms 轮询 + 内容哈希比对；自动发送默认关闭，开启后 debounce 800ms 再上传。
- 远端写入：仅 WS `clip.created` 且 `autoPaste=true` 时写系统剪贴板；记录 `lastAppliedHash`。
- 回环：写入 / 监听命中 `lastAppliedHash` 或 `lastSentHash` 且 2 秒内则跳过；哈希用 SHA-256 或等价稳定哈希。
- 失败（剪贴板被占用等）返回可展示错误，不崩溃。
- 纯逻辑（哈希窗口、debounce）抽到可测试类；平台层只做 API 调用。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ClipboardGuard*'
./gradlew :composeApp:assembleDebug
```

预期：回环测试全绿；Android 构建通过；iOS framework 链接通过。

**DoD**

- [ ] 自动粘贴默认开、自动发送默认关。
- [ ] 2 秒窗口内不产生回环记录。
- [ ] 移动端无后台剪贴板代码。

**禁止**

- 不要在 Android / iOS 上实现轮询剪贴板。
- 不要把剪贴板内容写入日志。

---

## T-CORE-13 诊断日志

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-CORE-04 |
| 需求 | REQ-SET-011、REQ-NFR-006 |
| 预估 | 0.5–1 天 |

**目标**：release 默认零应用日志，debug 默认开启；开关即时生效；诊断日志只输出到本机系统日志。

**必读**：`docs/客户端技术架构.md` §6.3.4；`docs/功能需求文档.md` REQ-SET-011、REQ-NFR-006。

**涉及路径**：`shared/src/commonMain/.../logging/`、`composeApp/src/{androidMain,iosMain,desktopMain}/...LogSink.kt`、`shared/src/commonTest/`。

**实现要点**

- `AppLogger`：级别、开关、字段白名单；默认日志只含方法、路径模板、状态码、耗时、错误码、操作类型、记录 ID。
- 诊断开启：追加请求体、响应体、`Authorization` 令牌、WS 认证帧令牌。
- 输出目标：Android Logcat、iOS os_log、Desktop stdout；不落盘、不上传、不接第三方 SDK。
- 开关变更立即生效；关闭后不再输出任何业务 / 网络日志。
- release 首次开启需二次确认（UI 由 T-UI-08 实现）；logger 提供 `requiresConfirmation` 状态。
- 崩溃日志不含正文与令牌（本卡只保证不主动写入）。
- 测试：开关关闭零输出、白名单字段、诊断模式包含 body/token、release 默认为关。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*Logging*'
```

预期：全绿；测试断言默认模式不出现正文与令牌。

**DoD**

- [ ] 不新增日志文件与上传通道。
- [ ] 日志级别与开关联动。
- [ ] 所有网络日志走同一个 logger。

**禁止**

- 不要在日志中输出配对码、分享 token。
- 不要用 `println` 绕过 logger。

---

## T-CORE-14 分享仓库与离线约束

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-CORE-04、T-CORE-05 |
| 需求 | REQ-SHARE-001/002/009 |
| 预估 | 0.5 天 |

**目标**：只有已同步记录可创建分享；离线 / 待同步 / 失败状态给出明确结果。

**必读**：`docs/api.md` §5.1；`docs/功能需求文档.md` SHARE 域。

**涉及路径**：`shared/src/commonMain/kotlin/com/wangxingxing/clipbox/repository/ShareRepository.kt`、`shared/src/commonTest/`。

**实现要点**

- `createShare(clipId, ttlSeconds, oneTime)`：读取本地记录；仅 `SYNCED` 允许；否则返回 `NotSynced` 错误，不调用网络。
- 调用 `ApiService.createShare`；成功返回 `url`、`expiresAt`、`oneTime`。
- 不做本地缓存、不提供分享列表 / 撤销。
- 测试：SYNCED 成功、PENDING / DELETE_PENDING / FAILED 均拒绝、离线错误映射、参数边界。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests --tests '*ShareRepository*'
```

预期：全绿；未同步记录不产生网络请求（可用 MockEngine 断言请求数 0）。

**DoD**

- [ ] 分享 URL 原样使用服务端返回值，客户端不拼接。
- [ ] 错误类型可供 UI 显示「同步后可分享」。
- [ ] 不写本地分享历史。

**禁止**

- 不要为离线分享做队列。
- 不要自行构造分享链接。

---

## T-CORE-15 Koin 装配与启动编排

| 字段 | 值 |
| --- | --- |
| 里程碑 | M3 |
| 前置 | T-CORE-11、T-CORE-12、T-CORE-13 |
| 需求 | REQ-PLAT-001、REQ-SYNC-011 |
| 预估 | 0.5 天 |

**目标**：把共享层与平台实现装配成可启动、可关闭的应用容器，避免 UI 手工构造依赖。

**必读**：`docs/客户端技术架构.md` §6.9；各卡已实现的模块。

**涉及路径**：`composeApp/src/commonMain/.../di/`、`shared/src/commonMain/.../AppContainer.kt`、平台 `startKoin` / `stopKoin` 入口。

**实现要点**

- 模块聚合：settings、network、db、repository、sync、clipboard、logging；平台模块提供引擎、DataStore、Room 构建器、剪贴板、日志输出。
- 启动顺序：加载设置 → 构建数据库与网络 → 若已配对启动 SyncEngine / WS；未配对只启动本地功能。
- 解除配对：停止同步 → 清空设备信息 → 关闭数据库 / 网络实例 → 重建容器。
- 应用生命周期：前台恢复、后台暂停由平台入口调用共享 `AppLifecycle`。
- 关闭时取消协程作用域，避免泄漏。
- 不承载业务规则；只做构造与生命周期。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
```

预期：Desktop 启动无异常；Android 构建通过；未配对时不发起网络请求（可观察日志 / Mock）。

**DoD**

- [ ] UI 不直接 `new` Repository 或数据库。
- [ ] 解除配对后无残留协程与连接。
- [ ] 启动失败有明确错误，不静默空白。

**禁止**

- 不要在 DI 模块中写业务判断。
- 不要引入第二个 DI 框架。

---

## T-CORE-16 设备取码与空库初始化共享层

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5（v1.0.0 收尾） |
| 前置 | T-CORE-15、T-S-12 |
| 需求 | REQ-PAIR-001、REQ-PAIR-009 |
| 预估 | 0.5 天 |

**目标**：共享层提供空库初始化与设备取码能力，供设置页与配对页调用。

**必读**：`docs/api.md` §2.2–§2.3；`docs/客户端技术架构.md` §6.3、§8.1。

**涉及路径**：`shared/src/commonMain/.../network/ApiService.kt`、`repository/PairingRepository.kt`、`repository/PairingCodeRepository.kt`、`sync/SyncRuntime.kt`、`app/AppContainer.kt` 与对应测试。

**实现要点**

- `ApiService` 新增 `bootstrap(deviceName)`、`createPairingCode()` 与 DTO；错误码增加 `ALREADY_INITIALIZED`。
- `PairingRepository.initialize(serverUrl, deviceName)` 复用 `setServerUrl + setPairingInfo`；`PairingError` 增加 `AlreadyInitialized`。
- `PairingCodeRepository` 基于认证 `ApiService` 生成码，返回 `code/expiresAt`，不持久化。
- `SyncRuntime` 暴露 `apiService`；`AppSession` 暴露 `pairingCodeRepository`。
- 测试覆盖成功、401、409、429 映射，断言配对码不写入设置存储。

**验收**

```bash
cd ClipboxClient
./gradlew :shared:allTests
```

预期：全部通过；新增用例可定位。

**DoD**

- [ ] 配对码不进入 DataStore/Room。
- [ ] 一卡一提交：`T-CORE-16: <一句话>`。

**禁止**

- 不要在共享层记录配对码明文。
