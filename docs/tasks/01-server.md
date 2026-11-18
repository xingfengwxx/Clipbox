# 服务端任务卡（T-S）

工程：`ClipboxServer/`，独立 Gradle 工程，包名 `com.wangxingxing.clipbox.server`。
技术基线：Kotlin + Ktor Netty + Exposed + SQLite WAL + HikariCP；JDK 21；`-Xmx256m`。
接口唯一依据：`docs/api.md`；实现细节：`docs/服务端技术架构.md`。

所有卡的验收命令默认在 `ClipboxServer/` 下执行。若 `./gradlew` 尚未生成，先执行 `gradle wrapper --gradle-version <模板锁定版本>` 或使用系统 `gradle`，并在该卡提交说明中记录选择。

---

## T-S-01 服务端工程骨架与配置校验

| 字段 | 值 |
| --- | --- |
| 里程碑 | M0 |
| 前置 | 无 |
| 需求 | REQ-PLAT-006、REQ-PLAT-007、REQ-NFR-006 |
| 预估 | 0.5–1 天 |

**目标**：建立可启动、可测试、配置非法的启动即失败的 Ktor 服务端空壳，`/healthz` 可用。

> 2026-11-18 起按 ADR-0006 移除 `ADMIN_TOKEN` 必填校验；配置项以 `docs/服务端技术架构.md` §11.4 与 T-S-12 为准。

**必读**：`docs/服务端技术架构.md` §2、§4、§5、§11.1、§11.4；`docs/api.md` §1、§6。

**涉及路径**：`ClipboxServer/build.gradle.kts`、`settings.gradle.kts`、`gradle/libs.versions.toml`、`src/main/kotlin/com/wangxingxing/clipbox/server/`、`src/main/resources/`、`src/test/kotlin/`。

**实现要点**

- Gradle 应用工程：Kotlin JVM、Ktor server netty、content negotiation + kotlinx.serialization、logback、JUnit；版本写入 version catalog，不追新。
- `Application.kt`：读取配置 → 装配插件 → 装配路由 → 启动；`PORT` 默认 8080。
- `AppConfig`：`ADMIN_TOKEN` 必填且 ≥32 字符；`PUBLIC_BASE_URL` 必填，必须是合法的 `http://` 或 `https://` 绝对地址（含主机、无尾斜杠）；`DATA_DIR` 可写。任一非法立即退出并打印明确原因，不进入半启动状态。
- `/healthz`：本卡先返回 `{"status":"ok","version":"1.0.0","time":<epochMillis>}`；T-S-02 接入数据库后补 `SELECT 1`。
- 配置校验失败日志不得打印令牌明文。
- 单元测试覆盖：缺 `ADMIN_TOKEN`、`ADMIN_TOKEN` 过短、`PUBLIC_BASE_URL` 缺 scheme、合法 http 地址通过。

**验收**

```bash
cd ClipboxServer
./gradlew test
ADMIN_TOKEN=$(printf 'a%.0s' {1..32}) PUBLIC_BASE_URL=http://203.0.113.10:8080 ./gradlew run &
sleep 5
curl -sS http://127.0.0.1:8080/healthz
```

预期：测试全绿；`/healthz` 返回 200 且 `status=ok`；去掉 `PUBLIC_BASE_URL` 启动时进程退出并打印明确错误。

**DoD**

- [ ] `./gradlew test` 通过，测试报告可查。
- [ ] 启动命令与配置项写入 `ClipboxServer/README.md`（简短即可）。
- [ ] 无未使用的插件、依赖或空包。

**禁止**

- 不要引入 Docker、数据库或业务路由；不要提前实现配对。
- 不要启用 `XForwardedHeaders` 或任何反向代理假设。

---

## T-S-02 SQLite Schema、迁移与序号分配

| 字段 | 值 |
| --- | --- |
| 里程碑 | M0 |
| 前置 | T-S-01 |
| 需求 | REQ-SYNC-001…004、REQ-PLAT-006/007 |
| 预估 | 1 天 |

**目标**：SQLite 落盘、迁移可重复执行、仓储可读写、`server_seq` 分配并发安全。

**必读**：`docs/服务端技术架构.md` §6（全部）、§10.1；`docs/api.md` §1.3。

**涉及路径**：`src/main/resources/db/migration/V001__init.sql`、`src/main/kotlin/.../db/`、`src/main/kotlin/.../repository/`、`src/test/kotlin/.../db/`。

**实现要点**

- HikariCP：连接数上限 4；SQLite 开启 WAL、`busy_timeout`、外键约束；所有阻塞 JDBC 走 `Dispatchers.IO`。
- 自研 `Migrator`：读取 `schema_version`，按版本号顺序执行 SQL，在事务中记录 `applied_at`；重复启动不重复执行。
- `V001__init.sql` 严格按《服务端技术架构》§6.2：`schema_version`、`devices`、`clips`、`pairing_codes`、`shares`、`meta` 与索引；`meta` 初始化 `server_seq=0`、`sync_floor=0`。
- `SeqAllocator.nextSeq()`：在写事务内读取 `meta.server_seq` → +1 → 写回 → 返回，保证唯一；禁止在事务外预分配。
- Repository 基础：DeviceRepository、ClipRepository、PairingRepository、ShareRepository、MetaRepository；只做参数化查询。
- `/healthz` 增加数据库 `SELECT 1`，失败返回 503。
- 测试：迁移空库、迁移后重启、`server_seq` 并发分配无重复、`/healthz` 数据库故障返回 503（可用临时目录关闭数据库模拟）。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*Migration*' --tests '*SeqAllocator*' --tests '*Health*'
ls -l data/clipbox.db
sqlite3 data/clipbox.db '.schema'
```

预期：迁移测试全绿；数据库文件存在；schema 与文档一致；并发分配 1000 次无重复。

**DoD**

- [ ] 数据目录不存在时自动创建；目录不可写时启动失败并说明原因。
- [ ] SQL 文件只包含 DDL 与 `meta` 初始值，不包含业务数据。
- [ ] 测试使用临时目录，不污染仓库。

**禁止**

- 不要用 Exposed 的 `SchemaUtils.create` 代替迁移。
- 不要引入 PostgreSQL / H2 作为测试替代；测试使用真实 SQLite。

---

## T-S-03 配对码与管理接口

> 已被 T-S-12 取代（ADR-0006）：管理接口、`ADMIN_TOKEN` 与启动自动发码均已删除；配对码改由已配对设备生成。本卡保留为历史记录，当前行为以 `docs/api.md` 与 T-S-12 为准。

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-S-02 |
| 需求 | REQ-PAIR-001…005、REQ-NFR-004/005 |
| 预估 | 1 天 |

**目标**：管理端生成 6 位一次性配对码，客户端用码换取设备令牌；令牌只返回一次。

**必读**：`docs/api.md` §1.2、§2、§7；`docs/功能需求文档.md` PAIR 域；`docs/服务端技术架构.md` §8.2。

**涉及路径**：`src/main/kotlin/.../routes/PairingRoutes.kt`、`.../service/PairingService.kt`、`.../service/AdminService.kt`、`src/test/kotlin/.../PairingTest.kt`。

**实现要点**

- `POST /api/v1/admin/pairing-codes`：仅 `X-Admin-Token`，常量时间比较；生成 6 位数字（允许前导零），`SecureRandom`；10 分钟 TTL；主键冲突重试最多 10 次；响应不打印、不记录码。
- `POST /api/v1/pair`：body `{code, deviceName}`；校验非空与长度；区分 `INVALID_CODE` / `CODE_EXPIRED` / `CODE_USED`；成功时在同一事务内标记 `used_at`、创建设备、生成 32 字节令牌（Base64URL 无填充）、保存 `SHA-256(token)`。
- 响应 `{deviceId, token}`；`token` 只在此响应出现；日志不记录 code 与 token。
- 启动时若不存在未使用且未过期的配对码，自动生成一个并打印到启动日志（仅一次，带明确标记）；管理接口生成的码不打印。
- 设备名长度与字符集按 `功能需求文档` 校验；错误统一走 StatusPages 格式（若 T-S-07 尚未实现，本卡先返回临时结构，T-S-07 再统一）。
- 测试：成功配对、码不存在、过期、已使用、管理令牌缺失/错误、令牌只出现一次、数据库只存哈希。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*Pairing*'
./gradlew run &
sleep 5
CODE=$(curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/pairing-codes -H "X-Admin-Token: $ADMIN_TOKEN" | jq -r .code)
curl -sS -X POST http://127.0.0.1:8080/api/v1/pair -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\",\"deviceName\":\"Pixel 8\"}" | jq
curl -sS -X POST http://127.0.0.1:8080/api/v1/pair -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\",\"deviceName\":\"X\"}" | jq
```

预期：首次配对返回 `deviceId` 与 43 字符 token；第二次返回 `CODE_USED`；数据库 `token_hash` 不等于 token。

**DoD**

- [ ] 配对码为 6 位数字字符串；前导零保留。
- [ ] 令牌 32 字节、base64url 无填充、长度 43。
- [ ] 测试断言数据库中没有明文令牌。

**禁止**

- 不要在 URL / 查询参数中传配对码或令牌。
- 不要把配对码写入常规请求日志。

---

## T-S-04 设备认证与增量查询

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-S-03 |
| 需求 | REQ-SYNC-001…004、REQ-PAIR-008 |
| 预估 | 1 天 |

**目标**：实现 Bearer 设备认证与 `GET /api/v1/clips?since=&limit=` 的完整同步语义。

**必读**：`docs/api.md` §3.1、§9；`docs/服务端技术架构.md` §6.4、§9.1。

**涉及路径**：`src/main/kotlin/.../plugins/Security.kt`、`.../routes/ClipRoutes.kt`、`.../service/ClipService.kt`、`src/test/kotlin/.../ClipQueryTest.kt`。

**实现要点**

- Bearer 认证：SHA-256 后查 `devices.token_hash`，附加 `DevicePrincipal(deviceId, name)`；失败 401 `UNAUTHORIZED`。
- 认证成功更新 `last_seen_at`，同一设备最多每 5 分钟写一次。
- `GET /clips`：`since` 默认 0，`limit` 默认 200、范围 1…200；按 `server_seq ASC` 查询 `server_seq > since`，多取一条判断 `hasMore`。
- 响应：`items`、`nextSince`、`hasMore`、`resyncRequired`、`minAvailableSeq`、`serverMaxSeq`。
- `resyncRequired`：`since > 0` 且 `since < meta.sync_floor`，或 `since > serverMaxSeq`；此时 items 为空。
- 墓碑只返回 `id`、`serverSeq`、`deleted=true`；活跃记录返回完整字段并附加 `sourceDeviceName`（联查 devices，缺失为“未知设备”）。
- 空页 `nextSince == since`。
- 测试：多页分页、`hasMore`、游标推进、墓碑、`sync_floor` 触发重同步、来源设备名。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*ClipQuery*' --tests '*Auth*'
```

预期：全绿；手工 `curl` 无 Bearer 返回 401；带 Bearer 返回结构完整的 JSON。

**DoD**

- [ ] 查询只使用参数化 SQL。
- [ ] `serverSeq` 是唯一排序依据，不使用 `createdAt`。
- [ ] 响应字段名与 `api.md` 完全一致。

**禁止**

- 不要实现 `before` 翻页或服务端搜索。
- 不要在响应中暴露 `token_hash` 或设备令牌。

---

## T-S-05 剪贴记录写入、置顶与软删除

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-S-04 |
| 需求 | REQ-SEND-001…009、REQ-HIST-003/007/011、REQ-SYNC-005/006 |
| 预估 | 1 天 |

**目标**：实现 `POST /clips`、`PATCH /clips/{id}`、`DELETE /clips/{id}`，含幂等、冲突、上限校验与序号分配。

**必读**：`docs/api.md` §3.2–§3.4、§7；`docs/服务端技术架构.md` §6.3、§6.5。

**涉及路径**：`src/main/kotlin/.../service/ClipService.kt`、`.../routes/ClipRoutes.kt`、`src/test/kotlin/.../ClipWriteTest.kt`。

**实现要点**

- `POST /clips`：body `{id, content, createdAt}`；ID 必须匹配 ULID 正则；content trim 后非空；UTF-8 字节 ≤65536，否则 400 `VALIDATION_ERROR`；`sourceDeviceId` 从认证信息写入。
- 幂等：同 ID 同内容（且未删除）返回 200，不分配新序号；同 ID 不同内容返回 409 `CONFLICT`；已删除记录再创建返回 409。
- 首次创建返回 201，分配新 `server_seq`，写入与序号更新同一事务。
- `PATCH /clips/{id}`：body `{pinned}`；相同值幂等不推进序号；变化推进 `server_seq`；不存在返回 404。
- `DELETE /clips/{id}`：软删除，写 `deleted_at` 并推进序号；重复删除返回 200 且序号不变。
- 这些接口只返回单条记录的最新状态，不返回 `serverMaxSeq`；写响应不承担游标推进语义。
- 测试：重复创建、冲突、超限、空白正文、置顶幂等、删除幂等、序号推进次数。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*ClipWrite*'
```

预期：全绿；测试中可断言每次真实变更只分配一个序号。

**DoD**

- [ ] 正文大小按 UTF-8 字节计算，不是字符数。
- [ ] 所有写操作在事务内完成。
- [ ] 错误码与 `api.md` 一致。

**禁止**

- 不要用正文哈希替代直接比较。
- 不要物理删除剪贴记录（墓碑清理由 T-S-08 负责）。

---

## T-S-06 WebSocket 认证、注册表与广播

| 字段 | 值 |
| --- | --- |
| 里程碑 | M1 |
| 前置 | T-S-05 |
| 需求 | REQ-RECV-001/002、REQ-SYNC-007/008 |
| 预估 | 1 天 |

**目标**：设备可通过 `/api/v1/sync` 建立单连接并接收 `clip.created/updated/deleted` 实时事件。

**必读**：`docs/api.md` §4；`docs/服务端技术架构.md` §7、§10.1。

**涉及路径**：`src/main/kotlin/.../ws/`、`.../service/Broadcaster.kt`、`src/test/kotlin/.../WebSocketTest.kt`。

**实现要点**

- 连接后 5 秒内必须收到 `{"type":"auth","token":"..."}`；超时或格式错误关闭连接。
- 认证成功回 `{"type":"auth.ok","deviceId":"...","serverSeq":<当前最大序号>}`；失败回 `error` 并关闭 4401。
- 单设备单连接：同设备新连接关闭旧连接（4001）；连接尝试超限关闭 4429（计数逻辑可先留接口，T-S-07 接入）。
- `ConnectionRegistry` 线程安全；`Broadcaster` 在数据库事务提交后广播，广播失败不影响写响应。
- 事件：`clip.created` / `clip.updated` / `clip.deleted`，每条带 `seq`；不重放历史，客户端断线用 REST 补拉。
- 写入方也收到自己的事件（客户端按 id 幂等合并）。
- 测试：认证成功、认证失败、超时、重复连接踢旧、写操作触发事件、事件 `seq` 单调。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*WebSocket*'
```

预期：全绿；日志中不出现认证帧原文与令牌。

**DoD**

- [ ] WebSocket 不阻塞 Netty 事件循环。
- [ ] 连接关闭时从注册表移除，无泄漏。
- [ ] 事件 JSON 结构与 `api.md` 一致。

**禁止**

- 不要在 WS 上提供上传接口。
- 不要做事件重放或离线消息队列。

---

## T-S-07 限流、错误映射、安全响应头与日志脱敏

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-S-06 |
| 需求 | REQ-NFR-001/004/005/006、REQ-PLAT-007 |
| 预估 | 1 天 |

**目标**：把公网暴露面的防护补齐：统一错误、请求体上限、分项限流、安全头、日志不泄露正文与令牌。

**必读**：`docs/api.md` §1.4–§1.5、§7、§8；`docs/服务端技术架构.md` §8.3–§8.6。

**涉及路径**：`src/main/kotlin/.../plugins/`、`.../plugins/RateLimits.kt`、`.../plugins/Security.kt`、`src/test/kotlin/.../LimitsTest.kt`。

**实现要点**

- StatusPages：所有非 2xx 返回 `{"error":{"code","message"}}`；异常不泄露堆栈。
- RequestBodyLimit 512 KiB，超限 413 `PAYLOAD_TOO_LARGE`；非 JSON 415 `UNSUPPORTED_MEDIA_TYPE`；方法不允许 405。
- 限流：配对 5/min/IP、管理 10/min/IP、设备 120/min/设备、分享页 60/min/IP、`/healthz` 120/min/IP、WS 连接尝试 10/min/设备；超限 429 且带 `Retry-After`。
- IP 取自 TCP 连接（`call.request.origin.remoteHost`）；忽略 `X-Forwarded-For`。
- 安全响应头：`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、API `Cache-Control: no-store`；不返回 HSTS。
- 日志：`requestId`、方法、路径模板、状态码、耗时、deviceId、错误码；禁止正文、令牌、配对码、分享令牌、请求/响应体；`/s/{token}` 记录为路径模板。
- 启动日志只打印版本、监听地址、数据目录、迁移版本、自动生成的配对码（仅一次）。
- 测试：413、415、429 + `Retry-After`、错误 JSON、日志字段白名单。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*Limits*' --tests '*ErrorMapping*' --tests '*Logging*'
```

预期：全绿；连续 6 次配对请求第 6 次返回 429。

**DoD**

- [ ] 所有错误响应结构一致。
- [ ] 429 一定带 `Retry-After`。
- [ ] 日志测试断言不包含正文与 token。

**禁止**

- 不要引入 Redis 或外部限流组件。
- 不要信任任何转发头。

---

## T-S-08 保留清理、墓碑与同步下界

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-S-07 |
| 需求 | REQ-HIST-011、REQ-SYNC-012…015 |
| 预估 | 1 天 |

**目标**：服务端按保留规则清理记录，墓碑 30 天后物理删除并推进 `sync_floor`。

**必读**：`docs/api.md` §9.2–§9.4；`docs/服务端技术架构.md` §6.5、§9.3。

**涉及路径**：`src/main/kotlin/.../job/RetentionJob.kt`、`src/test/kotlin/.../RetentionTest.kt`。

**实现要点**

- 活跃记录：全部置顶永久保留；非置顶按 `createdAt DESC` 保留最近 500 条，超出部分软删除并逐个分配新 `server_seq`（不能直接物理删除）。
- 清理分批执行，避免长事务；每批提交后再继续。
- 墓碑：`deleted_at` 超过 30 天的记录物理删除；删除后在事务内更新 `meta.sync_floor = max(sync_floor, 本批最大 server_seq)`。
- 分享按 `expires_at + 24h` 清理；配对码按过期时间清理。
- 调度：每天一次 + 启动时一次；使用可注入 `Clock` 便于测试。
- 测试：501 条非置顶只保留 500；置顶不参与；墓碑 30 天边界；`sync_floor` 只由物理清理推进；软删除本身可被 `since` 拉到。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*Retention*'
```

预期：全绿；测试可展示清理前后 `server_seq`、`deleted_at`、`sync_floor` 的变化。

**DoD**

- [ ] 清理任务失败不导致服务退出；记录错误并等待下次运行。
- [ ] 清理不产生重复序号。
- [ ] 墓碑物理删除前必须已完成 30 天保留。

**禁止**

- 不要清理置顶记录。
- 不要因客户端本地裁剪而在服务端删除记录。

---

## T-S-09 分享创建接口

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-S-05 |
| 需求 | REQ-SHARE-001/002/007/009 |
| 预估 | 0.5 天 |

**目标**：已同步记录可生成只读分享链接，保存内容快照。

**必读**：`docs/api.md` §5.1；`docs/adr/0002-share-content-snapshot.md`。

**涉及路径**：`src/main/kotlin/.../service/ShareService.kt`、`.../routes/ShareRoutes.kt`、`src/test/kotlin/.../ShareCreateTest.kt`。

**实现要点**

- `POST /api/v1/shares`：Bearer 认证；body `{clipId, ttlSeconds, oneTime}`。
- `ttlSeconds` 范围 60…604800；`oneTime` 必须布尔；`clipId` 必须存在且未软删除，否则 404。
- 生成 32 字节分享令牌（Base64URL 无填充），只存 `SHA-256(token)`；创建时把当前正文写入 `shares.content` 快照。
- `url = PUBLIC_BASE_URL + "/s/" + token`；`expiresAt = now + ttlSeconds*1000`。
- 响应 `{token, url, expiresAt}`；令牌只在创建响应出现。
- 测试：范围边界、记录不存在、已删除、URL 拼接、数据库无明文 token、快照与后续正文修改解耦。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*ShareCreate*'
```

预期：全绿；创建后修改原剪贴记录，分享内容不变（后续 T-S-10 验证）。

**DoD**

- [ ] 不提供分享列表、撤销、续期接口。
- [ ] 分享令牌不进入日志。
- [ ] `PUBLIC_BASE_URL` 尾部斜杠已在配置层去除。

**禁止**

- 不要用 `clip_id` 动态查询正文渲染分享页。
- 不要允许对未同步 / 不存在的记录创建分享。

---

## T-S-10 分享页渲染与一次性消费

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-S-09 |
| 需求 | REQ-SHARE-003/004/005/008、REQ-NFR-003 |
| 预估 | 1 天 |

**目标**：浏览器可打开只读分享页，转义安全，一次性链接首次访问原子消费。

**必读**：`docs/api.md` §5.1–§5.2；`docs/服务端技术架构.md` §7.4、§8.4。

**涉及路径**：`src/main/kotlin/.../service/SharePageRenderer.kt`、`.../routes/ShareRoutes.kt`、`src/test/kotlin/.../SharePageTest.kt`。

**实现要点**

- `GET /s/{token}`：公开；按 SHA-256 查分享；不存在或格式非法 404；过期 410 `SHARE_EXPIRED`；一次性已消费 410 `SHARE_CONSUMED`。
- 一次性消费必须原子：`UPDATE shares SET opened_at=? WHERE token_hash=? AND opened_at IS NULL AND expires_at>?`，受影响行数为 1 才渲染正文。
- HTML 严格转义正文（`& < > " '`）；页面只含正文、到期提示、页脚；无脚本、无外部字体 / 图片 / CSS。
- 响应头：`Content-Type: text/html; charset=utf-8`、`Cache-Control: no-store`、`Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`。
- viewport meta、`robots noindex,nofollow`；正文块 `pre-wrap`，最大宽度约 680px。
- 错误页极简，不泄露正文。
- 测试：`<script>alert(1)</script>` 原样展示；`<img onerror>` 不产生请求；并发两次 GET 只有一次成功；过期/已用/无效状态码正确。

**验收**

```bash
cd ClipboxServer
./gradlew test --tests '*SharePage*'
```

预期：全绿；测试断言响应头与转义结果。

**DoD**

- [ ] 页面无 JS、无外部资源。
- [ ] 一次性消费并发安全。
- [ ] 访问日志路径为 `/s/{token}`。

**禁止**

- 不要在页面中加入复制按钮或任何脚本。
- 不要返回分享 token 或正文以外的设备信息。

---

## T-S-11 集成、契约与并发测试

| 字段 | 值 |
| --- | --- |
| 里程碑 | M2 |
| 前置 | T-S-08、T-S-10 |
| 需求 | REQ-NFR-007…017、全部服务端 REQ |
| 预估 | 1 天 |

**目标**：用 `testApplication` 跑通完整服务端契约，形成后续所有卡可复用的回归基线。

**必读**：`docs/api.md` 全部；`docs/服务端技术架构.md` §13。

**涉及路径**：`src/test/kotlin/.../integration/`、测试夹具与辅助函数。

**实现要点**

- 测试夹具：临时数据目录、独立 SQLite、可注入 Clock、生成设备与令牌的 helper。
- 覆盖端到端流程：生成配对码 → 配对 → 创建记录 → 另一设备增量拉取 → 置顶 / 删除 → WS 收到事件 → 分享创建 → 分享页访问 → 一次性第二次 410。
- 契约：请求 / 响应字段名、错误码、状态码、分页字段、`Retry-After`。
- 并发：并行创建 200 条序号唯一；清理与写入并发；WS 注册 / 注销；一次性分享并发。
- 资源：单测试进程内存不明显增长；测试结束清理临时目录。
- 输出一份测试清单到 `ClipboxServer/README.md`（命令 + 用例名），供后续卡回归。

**验收**

```bash
cd ClipboxServer
./gradlew test
```

预期：全部测试通过；无跳过、无 `@Ignore`；测试报告中集成用例可单独定位。

**DoD**

- [ ] `./gradlew test` 一条命令可复现。
- [ ] 失败时能定位到具体用例。
- [ ] 测试不依赖真实公网、真实域名或本机固定端口。

**禁止**

- 不要为了让测试通过而放宽业务校验。
- 不要引入 Testcontainers 或 Docker 依赖。

---

## T-S-12 设备自有配对码与空库初始化

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5（v1.0.0 收尾） |
| 前置 | T-S-11、T-REL-14 |
| 需求 | REQ-PAIR-001、REQ-PAIR-009、REQ-NFR-003、REQ-NFR-005、REQ-PLAT-007 |
| 预估 | 1 天 |

**目标**：按 ADR-0006 把配对码改为已配对设备生成、服务端只校验哈希；新增空库初始化；删除管理接口与 `ADMIN_TOKEN`。

**必读**：`docs/api.md` §2、§7、§8；`docs/服务端技术架构.md` §5–§9、§11；[ADR-0006](../adr/0006-device-issued-pairing-codes.md)。

**涉及路径**：`src/main/resources/migrations/V002__device_pairing_codes.sql`、`src/main/kotlin/.../db/`、`service/`、`routes/`、`plugins/`、`config/`、`job/`、`Application.kt`、`src/test/kotlin/.../`、`README.md`。

**实现要点**

- V002：新建 `device_pairing_codes(device_id PK → devices ON DELETE CASCADE, code_hash UNIQUE, created_at, expires_at, used_at)` 与 `idx_dpc_expires`；`DROP TABLE pairing_codes`；注册到 Migrator。
- 仓储：`upsertCode`（同 `device_id` 覆盖旧码）、`findByCodeHash`、`markUsedIfUnused`、`deleteExpired`。
- 服务：`DevicePairingCodeService` 用 `SecureRandom` 生成 6 位补零、SHA-256 落库、冲突重试 10 次、TTL 600 秒、返回明文一次；`PairingService.pair` 改按哈希查码；`initialize` 在写事务内判空并建设备（409 `ALREADY_INITIALIZED`）。
- 路由：删管理路由；`POST /api/v1/devices/me/pairing-code` 走设备认证；`POST /api/v1/bootstrap` 公开。
- 限流：`bootstrapPerMinute=3`、`pairingCodePerMinute=10`，均按 IP；删除 admin 限流。
- 日志：路径模板加两条新路由；启动不再生成/打印配对码；删除 `ADMIN_TOKEN` 配置与校验。
- 清理：设备码按 `expires_at < now-24h` 删除。
- 测试：新增 `DevicePairingCodeTest`、`BootstrapTest`；修改 `PairingTest`、`LimitsTest`、`RetentionTest`、`MigrationTest`、`AppConfigTest`、`DatabaseStartupTest`、`IntegrationFixture`，去除 admin 依赖。

**验收**

```bash
cd ClipboxServer
./gradlew test
grep -RIn "ADMIN_TOKEN\|admin/pairing-codes\|pairing_codes" src/main || true
```

预期：全部测试通过；`src/main` 无残留；数据库只存配对码哈希；日志无配对码。

**DoD**

- [ ] 空库并发初始化只有一个成功。
- [ ] 换码后旧码返回 `INVALID_CODE`。
- [ ] 一卡一提交：`T-S-12: <一句话>`。

**禁止**

- 不要在日志、异常或测试输出中打印真实配对码。
- 不要保留管理接口兼容层。
