# Clipbox Server

服务端（Ktor Netty）。当前已实现启动配置校验、SQLite 迁移与仓储、`/healthz`、设备取码与设备配对、空库初始化。

## 版本基线

- JDK 21
- Gradle 9.3.0（wrapper；本机生成时使用系统 Gradle 9.6.0）
- Kotlin 2.3.21 / Ktor 3.4.3 / Logback 1.5.38 / JUnit Jupiter 5.14.4

## 启动

```bash
cd ClipboxServer
PUBLIC_BASE_URL=http://localhost:8080 \
./gradlew run
```

```bash
curl -sS http://127.0.0.1:8080/healthz
# {"status":"ok","version":"1.0.0","time":1730000000000}
```

学习版为 HTTP 直连，不启用 TLS（ADR-0003）；分享链接前缀由 `PUBLIC_BASE_URL` 决定。

## 配置项

| 环境变量 | 默认 | 必填 | 说明 |
| --- | --- | --- | --- |
| `PORT` | `8080` | 否 | 监听端口，1–65535 |
| `DATA_DIR` | `./data` | 否 | 数据目录，启动时自动创建并校验可写。容器部署由 compose 显式注入 `/data`（架构文档 §11.4 的 `/data` 为容器视角，本机默认取 `./data`） |
| `PUBLIC_BASE_URL` | 无 | 是 | 分享链接基地址，合法的 `http://` / `https://` 绝对地址，含主机、无尾斜杠 |

任一配置缺失或非法时进程立即退出并打印原因，不打印令牌明文。

## 测试

```bash
cd ClipboxServer
./gradlew test
```

测试报告：`build/reports/tests/test/index.html`。全部用例使用 JUnit 5 + `testApplication` + 临时目录 SQLite，不依赖 Docker / Testcontainers、固定端口或公网；无 `@Ignore`。

只跑集成 / 契约 / 并发：

```bash
./gradlew test --tests 'com.wangxingxing.clipbox.server.integration.*'
```

### 测试清单

集成、契约与并发（`src/test/kotlin/com/wangxingxing/clipbox/server/integration/`，夹具 `IntegrationFixture` 提供临时 SQLite、可注入限流与配对/记录/分享/WS helper）：

- `SyncFlowIntegrationTest`
  - `two device sync share and one time consumption flow`
- `ContractTest`
  - `sync page exposes documented fields and tombstone shape`
  - `invalid query and write bodies are rejected`
  - `unauthenticated requests share one error envelope`
  - `share creation validates ttl and clip availability`
  - `pair rate limit returns retry after`
  - `device rate limit returns retry after`
  - `security headers guard api and share responses`
- `ConcurrencyTest`
  - `two hundred parallel creates keep server seq unique`
  - `retention cleanup runs safely alongside writes`
  - `websocket registry converges after concurrent connect and close`
  - `one time share is consumed by exactly one parallel reader`

契约与组件回归（`src/test/kotlin/com/wangxingxing/clipbox/server/`）：

- `PairingTest`：`admin endpoint rejects missing and wrong token without creating a code`、`admin issues a six digit code valid for ten minutes`、`pairing returns a device token once and stores only its hash`、`code with leading zeros can be paired`、`unknown code is rejected as invalid`、`expired code is rejected`、`used code cannot be paired twice`、`malformed code and invalid device names are validation errors`、`pairing ignores unknown json fields`、`startup generates one self service code when none exists`
- `ClipWriteTest`：`first create returns 201 and allocates one server seq`、`replaying the same id and content is idempotent`、`same id with different content conflicts without touching data`、`recreating a deleted clip conflicts and does not resurrect it`、`content limit is measured in UTF-8 bytes`、`blank content and invalid id or createdAt are rejected`、`source device comes from auth and unknown fields are ignored`、`missing fields and malformed json are validation errors`、`pinning the same value is idempotent and a change advances once`、`patch requires a boolean pinned field`、`patching missing or deleted clips returns not found`、`delete advances once and repeating it is idempotent`、`deleting a clip that never existed returns not found`
- `ClipQueryTest`：`pages advance by serverSeq and report hasMore`、`default since is zero and default limit is 200`、`active items carry the full contract field set`、`tombstones expose only id serverSeq and deleted`、`ordering follows serverSeq rather than createdAt`、`cursor below sync floor requires full resync`、`cursor beyond server max seq requires full resync`、`since zero never requires resync even with non zero sync floor`、`missing source device falls back to unknown device`、`invalid cursor and limit are validation errors`
- `DeviceAuthTest`：`missing token is rejected with the contract error body`、`malformed scheme and unknown token are rejected the same way`、`valid token returns the page without leaking token material`、`first authentication records last seen`、`fresh last seen is not rewritten within five minutes`、`stale last seen is refreshed`
- `WebSocketTest`：`auth succeeds and reports the current server seq`、`unknown token receives an error frame and closes with 4401`、`malformed auth frame is rejected with 4401`、`missing auth frame closes with 4408`、`a second connection for the same device replaces the first with 4001`、`writes broadcast created, updated and deleted events with monotonic seq`、`idempotent writes do not broadcast`、`no clips are delivered before authentication`、`closing a session removes it from the registry`
- `ShareCreateTest`：`creating a share returns token url and expiry and stores a hashed snapshot`、`ttl boundaries are enforced and defaults apply`、`one time flag must be boolean and is stored as given`、`invalid missing or deleted clips cannot be shared`、`soft deleted clips cannot be shared`、`share creation requires device auth and has no list endpoint`、`each creation issues a distinct token without touching the sync sequence`、`share content is a snapshot decoupled from later clip changes`
- `SharePageTest`：`regular share page renders escaped content with security headers and is repeatable`、`html content is strictly escaped and external payloads never render as markup`、`one time share is served once and consumed afterwards`、`concurrent one time access yields exactly one page and one consumed response`、`expired share returns gone without content`、`already opened one time share stays consumed even after expiry`、`unknown and malformed tokens return not found without leaking anything`
- `ErrorMappingTest`：`unknown route returns the unified 404 body`、`unsupported method returns the unified 405 body`、`invalid query parameters return the unified 400 body`、`malformed json is a validation error rather than a server error`、`missing device token returns the unified 401 body`、`error responses carry request id and security headers`、`successful responses carry the same baseline headers`
- `LimitsTest`：`sixth pairing attempt from the same ip is rate limited`、`admin pairing code endpoint stops after ten attempts`、`x forwarded for header does not bypass the ip limit`、`content length above the limit is rejected with 413`、`chunked body above the limit is rejected with 413`、`non json body is rejected with 415`、`unsupported method is rejected with 405`、`device quota is counted per device token`、`health endpoint is rate limited`、`web socket connection attempts are limited per device`、`fixed window limiter resets after the window elapses`
- `LoggingTest`：`request log contains only the whitelisted fields`、`share token is logged as a path template`、`request log never contains bodies or device tokens`、`pairing request log never contains the submitted code`、`startup log reports version listen address data dir and schema version`
- `RetentionTest`：`501 active unpinned clips keep the newest 500 by createdAt`、`soft delete runs in batches and never reuses a server seq`、`pinned clips are never trimmed even when they are the oldest`、`tombstones are purged only after strictly more than 30 days`、`sync floor only moves forward with physical purge`、`soft deleted clips stay visible to since until the tombstone is purged`、`shares are cleaned 24 hours after expiry or one-time opening`、`pairing codes are cleaned 24 hours after expiry whether used or not`、`empty database sweep is a no-op`
- `HealthRoutesTest`：`healthz returns ok payload`
- `config/AppConfigTest`：`missing admin token fails`、`short admin token fails`、`missing public base url fails`、`public base url without scheme fails`、`public base url with trailing slash fails`、`valid http address passes and data dir is created`、`invalid port fails`
- `db/MigrationTest`：`empty database is migrated with schema and meta defaults`、`migration is idempotent and survives restart`
- `db/RepositoryTest`：`repositories read back the rows they write`、`foreign keys are enforced on pooled connections`
- `db/SeqAllocatorTest`：`concurrent allocation never duplicates a sequence`
- `db/DatabaseStartupTest`：`missing data directory is created`、`unwritable data directory fails with reason`
- `db/DatabaseHealthTest`：`healthz returns ok when database is reachable`、`healthz returns 503 after database closes`

## 健康检查

`GET /healthz` 无需认证，返回 `200`：

```json
{"status": "ok", "version": "1.0.0", "time": 1730000000000}
```

## 配对

空数据库首次部署时，首台设备调用初始化接口取得设备令牌（仅 `devices` 为空时可用）：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/bootstrap \
  -H 'Content-Type: application/json' \
  -d '{"deviceName":"Pixel 8"}'
# {"deviceId":"01J8...","token":"<43 字符>","deviceName":"Pixel 8"}
```

之后由已配对设备生成 6 位配对码：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/devices/me/pairing-code \
  -H "Authorization: Bearer $TOKEN"
# {"code":"654321","expiresAt":1730000600000}
```

新设备用码换取设备令牌，令牌只在该响应出现一次：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/pair \
  -H 'Content-Type: application/json' \
  -d '{"code":"654321","deviceName":"MacBook"}'
# {"deviceId":"01J8...","token":"<43 字符>","deviceName":"MacBook"}
```

配对码 600 秒有效、一次性、单设备单活码；服务端只保存配对码与设备令牌的 SHA-256 摘要，不打印配对码。
