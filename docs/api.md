# Clipbox API 契约 v1.0.0

| 字段 | 内容 |
| --- | --- |
| 文档版本 | v1.0.0 |
| 更新日期 | 2026-11-13 |
| 状态 | 设计冻结；客户端与服务端实现的唯一接口依据 |
| 基线 | [Clipbox 项目交接文档](./Clipbox%20项目交接文档.md) v0.1 |
| 相关 | [GLOSSARY.md](GLOSSARY.md)、[ADR-0001](./adr/0001-server-seq-change-feed.md)、[ADR-0002](./adr/0002-share-content-snapshot.md) |
| 适用产品 | Clipbox v1.0.0 |
| 工程路径 | 服务端 `/Users/wangxingxing/AndroidStudioProjects/MyProjects/ClipBox/ClipboxServer`<br>客户端 `/Users/wangxingxing/AndroidStudioProjects/MyProjects/ClipBox/ClipboxClient` |

本文只定义客户端与服务端之间的接口契约。客户端内部实现见[客户端技术架构](./客户端技术架构.md)，服务端内部实现见[服务端技术架构](./服务端技术架构.md)，产品行为与验收见[功能需求文档](./功能需求文档.md)。

## 1. 通用约定

### 1.1 传输与编码

- 正式环境只允许 HTTPS / WSS，由 Caddy 终止 TLS；明文 HTTP 仅用于本机开发。
- 请求与响应均为 JSON UTF-8，`Content-Type: application/json; charset=utf-8`；不接受表单与 multipart。
- 时间戳统一为 epoch 毫秒（int64）。
- 剪贴记录 ID 为客户端生成的 ULID：26 个字符，Crockford Base32，统一大写，正则 `^[0-9A-HJKMNP-TV-Z]{26}$`。
- 设备 ID 为服务端生成的 ULID。
- 设备令牌与分享令牌均为 32 字节随机数的 base64url 无填充编码，长度 43 个字符。
- 配对码为 6 位 ASCII 数字字符串，允许前导零。
- 双方都必须忽略未知 JSON 字段，以支持向后兼容的追加式演进。

### 1.2 认证

| 范围 | 方式 |
| --- | --- |
| 设备接口 | `Authorization: Bearer <device-token>` |
| 管理接口 | `X-Admin-Token: <admin-token>` |
| 公开接口 | `POST /api/v1/pair`、`GET /s/{token}`、`GET /healthz` |

- 设备令牌只返回一次，服务端只保存 `SHA-256(token)`；令牌泄露等价于设备身份泄露。
- 设备令牌不得出现在 URL、查询参数、日志或错误信息中。
- 管理令牌只存在于服务端环境变量，比较时使用常量时间算法。

### 1.3 标识、时间与变更序号

| 字段 | 语义 |
| --- | --- |
| `id` | 剪贴记录主键，客户端生成的 ULID；创建后不可变 |
| `createdAt` | 客户端记录的创建时间，epoch 毫秒；服务端原样保存，用于展示排序 |
| `serverSeq` | 服务端变更序号，单调递增 int64；创建、置顶变更、删除都会推进 |
| `sourceDeviceId` | 创建该记录的设备 ID，由服务端从认证信息写入 |
| `sourceDeviceName` | 设备名的服务端联查结果；设备不存在时返回 `"未知设备"` |
| `deleted` | 是否为墓碑（软删除）；墓碑只返回 `id`、`serverSeq`、`deleted` |
| `expiresAt` / `openedAt` | 分享页的服务端时间，epoch 毫秒 |

`serverSeq` 是同步的唯一权威顺序。`createdAt` 只用于界面排序，允许因设备时钟偏差而不单调；客户端不得用 `createdAt` 推断同步进度。

### 1.4 请求与响应头

请求头：

| 头 | 说明 |
| --- | --- |
| `Authorization` | 设备接口必填，`Bearer <token>` |
| `X-Admin-Token` | 管理接口必填 |
| `Content-Type` | 有请求体时必填 `application/json` |

响应头：

| 头 | 说明 |
| --- | --- |
| `Content-Type` | JSON 接口为 `application/json; charset=utf-8`；分享页为 `text/html; charset=utf-8` |
| `X-Request-Id` | 服务端生成的请求标识，便于日志排查 |
| `Cache-Control` | 所有 API 响应为 `no-store` |
| `Retry-After` | 仅 429 响应，单位秒 |

- API 不启用 CORS：客户端是原生应用，分享页与 API 同源，不需要浏览器跨域调用。
- 服务端不信任客户端传入的 `X-Forwarded-For`，只信任 Caddy 注入的转发头。

### 1.5 错误格式

所有非 2xx 响应统一为：

```json
{
  "error": {
    "code": "PAYLOAD_TOO_LARGE",
    "message": "content exceeds 65536 UTF-8 bytes"
  }
}
```

- `code` 是稳定的机器可读错误码，客户端据此做分支与文案映射。
- `message` 仅供日志与排查，不保证稳定，客户端不得解析其内容。
- 响应不包含堆栈、SQL、令牌、配对码、分享令牌或剪贴正文。

### 1.6 大小限制与配额

| 项 | 限制 | 超限行为 |
| --- | --- | --- |
| 单条剪贴正文 | 64 KiB（65536 UTF-8 字节） | 400 `VALIDATION_ERROR` |
| 请求体 | 512 KiB（524288 字节） | 413 `PAYLOAD_TOO_LARGE` |
| 空正文 / 仅空白 | 拒绝 | 400 `VALIDATION_ERROR` |
| 设备名 | 去除首尾空白后 1–64 个字符 | 400 `VALIDATION_ERROR` |
| 配对码 | 恰好 6 位数字 | 400 `VALIDATION_ERROR` |
| 分享有效期 | 60–604800 秒 | 400 `VALIDATION_ERROR` |
| 增量拉取 `limit` | 1–500，默认 200 | 400 `VALIDATION_ERROR` |
| WebSocket 首帧认证 | 5 秒内完成 | 关闭码 4408 |
| WebSocket 心跳 | 服务端每 30 秒 ping | 超时断开 |

请求体上限取 512 KiB，而不是交接文档草案的 128 KiB：64 KiB 正文经 JSON 转义后最坏可达约 384 KiB（控制字符按 `\u00XX` 转义时每个字节占 6 字节），128 KiB 与 256 KiB 都会误杀合法正文。正文大小仍严格限制在 64 KiB。

### 1.7 版本与兼容

- 路径前缀 `/api/v1`；v1 内只做追加式变更（新增字段、新增错误码、新增可选参数）。
- 破坏性变更必须发布 `/api/v2`，不得在 v1 内改变既有字段语义。
- 服务端与客户端都必须忽略未知字段；缺失的可选字段按默认值处理。
- 分享页 `/s/{token}` 与 `/healthz` 不参与 API 版本号。

## 2. 认证与配对

### 2.1 POST /api/v1/pair

用配对码把新设备登记到唯一用户空间，并换取设备令牌。无需认证。

请求：

```json
{
  "code": "123456",
  "deviceName": "Pixel 8"
}
```

响应 `201 Created`：

```json
{
  "deviceId": "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
  "token": "3q2-7wJ8kLmN4pQrS6tUvWxYzA1bC2dE3fG4hJ5kL6M",
  "deviceName": "Pixel 8"
}
```

行为：

1. 校验配对码存在、未使用、未过期；任何一项不满足都返回对应错误码。
2. 在同一个事务内把配对码标记为已使用、创建设备记录、写入令牌哈希。
3. 令牌只在本响应返回一次；服务端之后无法还原明文令牌。
4. 每个设备独立令牌；同一用户可配对多台设备，互不影响。
5. 配对码为一次性，成功或过期后不可再次使用。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 配对码格式错误、设备名为空或超长 |
| 400 | `INVALID_CODE` | 配对码不存在 |
| 400 | `CODE_EXPIRED` | 配对码已过期 |
| 400 | `CODE_USED` | 配对码已被使用 |
| 429 | `RATE_LIMITED` | 超过每 IP 每分钟 5 次尝试 |

示例：

```bash
curl -sS https://clip.example.com/api/v1/pair \
  -H 'Content-Type: application/json' \
  -d '{"code":"123456","deviceName":"Pixel 8"}'
```

### 2.2 POST /api/v1/admin/pairing-codes

生成新的配对码。仅管理令牌可访问。

请求头：

```text
X-Admin-Token: <admin-token>
```

请求体为空或 `{}`。

响应 `201 Created`：

```json
{
  "code": "654321",
  "expiresAt": 1730000600000
}
```

行为：

- 新码有效期固定 600 秒，一次性使用。
- 生成新码不会使已有未使用码失效；多个有效码可并存。
- 服务端启动时若不存在“未使用且未过期”的配对码，会自动生成一个并写入容器日志，供自助配对；管理接口用于随时补发。
- 配对码不得写入常规访问日志，只有启动自动生成时允许打印一次。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 管理令牌缺失或不匹配 |
| 429 | `RATE_LIMITED` | 超过每 IP 每分钟 10 次 |

## 3. 剪贴记录

### 3.1 GET /api/v1/clips

增量拉取变更，兼作首次全量同步。需要设备认证。

查询参数：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `since` | int64 | 0 | 只返回 `serverSeq > since` 的变更；`0` 表示首次全量同步 |
| `limit` | int | 200 | 单页条数，1–500 |

响应 `200 OK`：

```json
{
  "items": [
    {
      "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
      "content": "hello",
      "sourceDeviceId": "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
      "sourceDeviceName": "Pixel 8",
      "pinned": false,
      "createdAt": 1730000000000,
      "serverSeq": 41,
      "deleted": false
    },
    {
      "id": "01J8W6Z3K4M5N6P7Q8R9S0T1V2",
      "serverSeq": 42,
      "deleted": true
    }
  ],
  "nextSince": 42,
  "hasMore": false,
  "resyncRequired": false,
  "minAvailableSeq": 1,
  "serverMaxSeq": 42
}
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `items` | 按 `serverSeq` 升序排列的变更；含活跃记录与墓碑 |
| `nextSince` | 本页最后一条的 `serverSeq`；空页时等于请求的 `since` |
| `hasMore` | 是否还有下一页 |
| `resyncRequired` | 客户端游标已早于服务端可保证的范围，必须全量重同步 |
| `minAvailableSeq` | 服务端当前可提供的最小变更序号，等于 `syncFloor + 1` |
| `serverMaxSeq` | 服务端当前已分配的最大变更序号 |

同步语义：

- `since` 是排他边界：只返回 `serverSeq > since`。
- 活跃记录返回完整字段；墓碑只返回 `id`、`serverSeq`、`deleted: true`，不返回正文。
- `sourceDeviceName` 由服务端联查设备表得到；设备不存在时返回 `"未知设备"`。
- `resyncRequired` 为 true 的条件：
  - `since > 0` 且 `since < syncFloor`：墓碑已被清理，游标之后可能有已丢失的删除变更；
  - `since > serverMaxSeq`：服务端数据被恢复或重置，客户端游标来自“未来”。
- `resyncRequired` 为 true 时，响应固定为 `items: []`、`nextSince: since`、`hasMore: false`；客户端必须保留待发队列、清空已同步的本地记录，然后从 `since=0` 重新全量拉取。
- `since=0` 表示首次全量同步，不触发 `resyncRequired`；客户端按页拉取直到 `hasMore=false`。
- 不提供 `before` / `after` 历史翻页：历史浏览完全由客户端本地库承担，服务端只负责变更同步。
- 本接口不修改数据，允许重复调用；重复调用同一 `since` 返回相同结果（除非期间有新变更）。

示例：

```bash
# 首次全量同步
curl -sS 'https://clip.example.com/api/v1/clips?since=0&limit=200' \
  -H "Authorization: Bearer $TOKEN"

# 增量同步
curl -sS 'https://clip.example.com/api/v1/clips?since=42&limit=200' \
  -H "Authorization: Bearer $TOKEN"
```

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `since` 为负、`limit` 越界或非整数 |
| 401 | `UNAUTHORIZED` | 令牌缺失、格式错误或不存在 |
| 429 | `RATE_LIMITED` | 超过设备限流 |

### 3.2 POST /api/v1/clips

创建剪贴记录。客户端生成 ULID，服务端分配 `serverSeq`。需要设备认证。

请求：

```json
{
  "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
  "content": "hello",
  "createdAt": 1730000000000
}
```

首次创建响应 `201 Created`：

```json
{
  "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
  "serverSeq": 41,
  "createdAt": 1730000000000,
  "pinned": false,
  "deleted": false
}
```

幂等语义：

| 服务端状态 | HTTP | 行为 |
| --- | --- | --- |
| `id` 不存在 | 201 | 创建记录，分配新 `serverSeq` |
| `id` 已存在且正文相同 | 200 | 返回既有记录与既有 `serverSeq`，不推进序号 |
| `id` 已存在但正文不同 | 409 | `CONFLICT`，不修改任何数据 |
| `id` 已存在且已软删除 | 409 | `CONFLICT`，不复活记录 |

- 正文原样保存，不去除首尾空白；空白正文在进入数据库前拒绝。
- `createdAt` 必须大于 0；服务端接受客户端时钟，不做未来时间截断。
- `sourceDeviceId`、`sourceDeviceName` 由认证设备决定，请求中不允许传入。
- 新记录的 `pinned` 固定为 false，`deleted` 固定为 false。
- 该幂等规则是 Outbox 超时重试不产生重复记录的基础：客户端始终重发同一个 `id`。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | ULID 格式错误、正文空白、正文超 64 KiB、`createdAt <= 0` |
| 401 | `UNAUTHORIZED` | 设备令牌无效 |
| 409 | `CONFLICT` | 同 ID 内容不同或记录已删除 |
| 413 | `PAYLOAD_TOO_LARGE` | 请求体超过 512 KiB |
| 429 | `RATE_LIMITED` | 超过设备限流 |

### 3.3 PATCH /api/v1/clips/{id}

修改置顶状态。需要设备认证。剪贴正文不可修改。

请求：

```json
{
  "pinned": true
}
```

响应 `200 OK`：

```json
{
  "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
  "pinned": true,
  "serverSeq": 42,
  "deleted": false
}
```

行为：

- 只允许修改 `pinned`；请求中出现其他字段时忽略。
- 记录不存在或已软删除：404 `NOT_FOUND`。
- 新值与旧值相同：返回 200 与既有 `serverSeq`，不推进序号。
- 新值与旧值不同：更新并分配新 `serverSeq`，向所有 WebSocket 连接广播 `clip.updated`。
- 重复提交同一置顶状态是幂等的，不会制造额外变更。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `pinned` 缺失或不是布尔值 |
| 401 | `UNAUTHORIZED` | 设备令牌无效 |
| 404 | `NOT_FOUND` | 记录不存在或已删除 |
| 429 | `RATE_LIMITED` | 超过设备限流 |

### 3.4 DELETE /api/v1/clips/{id}

软删除剪贴记录。需要设备认证。

响应 `200 OK`：

```json
{
  "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
  "deleted": true,
  "serverSeq": 43
}
```

行为：

- 活跃记录：写入 `deleted_at`，分配新 `serverSeq`，广播 `clip.deleted`。
- 已删除记录：返回 200 与既有 `serverSeq`，不重复推进序号；这是重试安全的关键。
- 从未存在的记录：404 `NOT_FOUND`。
- 墓碑保留 30 天；清理任务物理删除后推进 `syncFloor`，此后的过旧游标会得到 `resyncRequired`。
- 删除不会立即清理分享页：分享页是内容快照，按自身有效期结束（见 ADR-0002）。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 设备令牌无效 |
| 404 | `NOT_FOUND` | 记录从未存在 |
| 429 | `RATE_LIMITED` | 超过设备限流 |

## 4. WebSocket /api/v1/sync

### 4.1 连接与认证

- 地址：`wss://<host>/api/v1/sync`
- 连接建立后 5 秒内，客户端必须发送首帧认证；认证前不接收任何业务事件。

认证请求：

```json
{
  "type": "auth",
  "token": "3q2-7wJ8kLmN4pQrS6tUvWxYzA1bC2dE3fG4hJ5kL6M"
}
```

认证成功：

```json
{
  "type": "auth.ok",
  "deviceId": "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
  "serverSeq": 42
}
```

认证失败：

```json
{
  "type": "error",
  "error": {
    "code": "UNAUTHORIZED",
    "message": "invalid token"
  }
}
```

发送错误帧后关闭连接，关闭码 4401。

- 单设备同时只允许 1 条活跃连接；新连接认证成功时，旧连接以 4001 关闭。
- 令牌只放在首帧，不放在 URL 查询参数中，避免进入代理日志。
- 服务端每 30 秒发送 WebSocket ping；客户端由底层引擎自动回 pong。

### 4.2 事件

服务端在数据库事务提交后广播事件，事件中的 `seq` 等于 `data.serverSeq`。

创建：

```json
{
  "type": "clip.created",
  "seq": 43,
  "data": {
    "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
    "content": "hello",
    "sourceDeviceId": "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
    "sourceDeviceName": "Pixel 8",
    "pinned": false,
    "createdAt": 1730000000000,
    "serverSeq": 43,
    "deleted": false
  }
}
```

置顶变更：

```json
{
  "type": "clip.updated",
  "seq": 44,
  "data": {
    "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
    "content": "hello",
    "sourceDeviceId": "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
    "sourceDeviceName": "Pixel 8",
    "pinned": true,
    "createdAt": 1730000000000,
    "serverSeq": 44,
    "deleted": false
  }
}
```

删除：

```json
{
  "type": "clip.deleted",
  "seq": 45,
  "data": {
    "id": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
    "serverSeq": 45,
    "deleted": true
  }
}
```

- 所有已认证连接都会收到广播，包括上传方自己；客户端按 `id` / `serverSeq` 幂等 upsert。
- WebSocket 不提供断线重放；重连后的补拉一律走`GET /api/v1/clips?since=<lastSeq>`。
- 服务端不保证事件与 REST 响应的到达顺序；`serverSeq` 是唯一排序依据。

### 4.3 断线重连与补拉

客户端必须遵守以下规则：

1. 维护本地 `lastSeq`，只能由 REST 增量拉取或连续无缺口的 WS 事件推进。
2. 收到事件时：
   - `seq <= lastSeq`：已应用过，忽略。
   - `seq == lastSeq + 1`：应用并推进 `lastSeq`。
   - `seq > lastSeq + 1`：发现缺口，忽略该事件并立即执行 REST 补拉。
3. 重连成功后，以 `auth.ok.serverSeq` 为目标执行 REST 补拉，直到 `lastSeq >= auth.ok.serverSeq`；补拉期间收到的 WS 事件先缓冲，补拉结束后按上述规则处理。
4. 若补拉返回 `resyncRequired`，执行全量重同步：保留待发队列，清空已同步的本地记录，从 `since=0` 重新拉取。

重连退避：1s → 2s → 4s → 8s → 16s → 32s → 60s，带 ±20% 抖动。收到 4401 时停止自动重连并提示重新配对；收到 4001 时停止自动重连，避免两个实例互踢。

### 4.4 关闭码

| 关闭码 | 含义 | 客户端行为 |
| --- | --- | --- |
| 1000 | 正常关闭 | 不重连 |
| 4001 | 被同一设备的新连接替换 | 不自动重连 |
| 4401 | 令牌无效 | 暂停同步，提示重新配对 |
| 4408 | 首帧认证超时 | 重新连接 |
| 4429 | 连接频率超限 | 按退避重连 |
| 1011 | 服务端内部错误 | 按退避重连 |

## 5. 分享

### 5.1 POST /api/v1/shares

为一条已同步的剪贴记录生成只读分享页。需要设备认证。

请求：

```json
{
  "clipId": "01J8Z6Z3K4M5N6P7Q8R9S0T1V2",
  "ttlSeconds": 86400,
  "oneTime": false
}
```

| 字段 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- |
| `clipId` | 是 | - | 必须存在且未删除 |
| `ttlSeconds` | 否 | 86400 | 60–604800 秒 |
| `oneTime` | 否 | false | 是否阅后即焚 |

响应 `201 Created`：

```json
{
  "token": "9mN4pQrS6tUvWxYzA1bC2dE3fG4hJ5kL6M7nO8pQ",
  "url": "https://clip.example.com/s/9mN4pQrS6tUvWxYzA1bC2dE3fG4hJ5kL6M7nO8pQ",
  "expiresAt": 1730086400000
}
```

行为：

- 服务端在创建时把剪贴正文快照写入分享记录；之后剪贴记录被删除或清理，不影响分享页（ADR-0002）。
- 分享令牌 32 字节随机；服务端只保存 `SHA-256(token)`，令牌只在本响应出现。
- `url` 由服务端配置的 `PUBLIC_BASE_URL` 拼接，客户端不得自行拼域名。
- v1.0.0 不提供分享列表、撤销或续期接口；过期后重新创建。
- 剪贴记录不存在或已软删除：404 `NOT_FOUND`。

错误：

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `clipId` 格式错误、`ttlSeconds` 越界、`oneTime` 非布尔 |
| 401 | `UNAUTHORIZED` | 设备令牌无效 |
| 404 | `NOT_FOUND` | 剪贴记录不存在或已删除 |
| 429 | `RATE_LIMITED` | 超过设备限流 |

### 5.2 GET /s/{token}

无需认证，返回只读 HTML 分享页。

成功响应 `200 OK`，`Content-Type: text/html; charset=utf-8`，响应头固定包含：

```text
Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
Cache-Control: no-store
```

页面规则：

- 正文放在 `<pre>` 中，按 HTML 实体转义 `& < > " '`，原样展示，不解释 Markdown、不执行脚本。
- 不加载任何外部资源，不包含 JavaScript，不提供复制按钮（复制按钮需要脚本，v1.0.0 不引入）。
- 页面包含 viewport meta，移动端可读；`<meta name="robots" content="noindex, nofollow">`。
- 页面只展示正文与到期提示，不展示设备名、配对信息或其他记录。

一次性语义：

- `oneTime=true`：首次成功 GET 时原子消费（`opened_at` 从 NULL 变为当前时间）；之后任何访问返回 410 `SHARE_CONSUMED`。
- `oneTime=false`：过期前可重复访问；过期后返回 410 `SHARE_EXPIRED`。
- 令牌不存在或格式非法：404 `NOT_FOUND`。
- 过期与已消费都返回极简 HTML 错误页，状态码分别为 410；不泄露正文。

限流：每 IP 每分钟 60 次。分享令牌出现在 URL 路径中，服务端访问日志必须记录路径模板 `/s/{token}`，不得记录原始令牌。

## 6. GET /healthz

无需认证。用于容器与 Caddy 健康检查。

正常响应 `200 OK`：

```json
{
  "status": "ok",
  "version": "1.0.0",
  "time": 1730000000000
}
```

数据库不可用时 `503 Service Unavailable`：

```json
{
  "status": "error",
  "version": "1.0.0",
  "time": 1730000000000
}
```

- 不检查磁盘剩余空间、外部依赖或 WebSocket 连接数；只做进程存活与数据库 `SELECT 1`。
- 不返回任何配置、路径或错误详情。

## 7. 错误码总表

| HTTP | code | 含义 | 典型场景 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 参数校验失败 | 正文空白、ULID 格式错误、limit 越界 |
| 400 | `INVALID_CODE` | 配对码不存在 | 输入错误 |
| 400 | `CODE_EXPIRED` | 配对码过期 | 超过 10 分钟 |
| 400 | `CODE_USED` | 配对码已使用 | 重复配对 |
| 401 | `UNAUTHORIZED` | 认证失败 | 令牌缺失、无效、管理令牌错误 |
| 404 | `NOT_FOUND` | 资源不存在 | 记录不存在、分享令牌不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不允许 | 对只读路径发写请求 |
| 409 | `CONFLICT` | 幂等冲突 | 同 ID 不同内容、记录已删除 |
| 410 | `SHARE_EXPIRED` | 分享已过期 | 超过 `expiresAt` |
| 410 | `SHARE_CONSUMED` | 分享已焚毁 | 一次性分享已打开 |
| 413 | `PAYLOAD_TOO_LARGE` | 请求体超限 | 超过 512 KiB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 内容类型不支持 | 非 JSON 请求体 |
| 429 | `RATE_LIMITED` | 触发限流 | 配对尝试、设备请求、分享页访问 |
| 500 | `INTERNAL_ERROR` | 服务端内部错误 | 未捕获异常、数据库故障 |
| 503 | `SERVICE_UNAVAILABLE` | 服务不可用 | 健康检查失败 |

## 8. 限流与配额

| 范围 | 限制 | 维度 | 超限 |
| --- | --- | --- | --- |
| `POST /api/v1/pair` | 5 次/分钟 | IP | 429，`Retry-After: 60` |
| `POST /api/v1/admin/pairing-codes` | 10 次/分钟 | IP | 429 |
| 设备接口 | 120 次/分钟 | 设备 ID | 429 |
| `GET /s/{token}` | 60 次/分钟 | IP | 429 |
| `GET /healthz` | 120 次/分钟 | IP | 429 |
| WebSocket 连接尝试 | 10 次/分钟 | 设备 ID | 关闭码 4429 |
| WebSocket 活跃连接 | 1 条 | 设备 ID | 新连接关闭旧连接 4001 |

- 限流为单实例内存计数；服务重启后清零，可接受。
- 真实客户端 IP 由 Caddy 注入的转发头获得；应用端口只暴露在 Docker 内网，不得直接暴露公网。
- 429 响应必须带 `Retry-After`（秒），客户端优先使用该值。

## 9. 同步语义与保留规则

### 9.1 为什么使用 `serverSeq`

ULID 适合做主键与展示排序，但不适合做变更游标：离线设备补传旧 ULID 时，其他设备的游标已经越过该 ULID；删除与置顶不改变 ULID，离线设备无法通过 ULID 游标获得墓碑。`serverSeq` 由服务端在每次变更时分配，覆盖创建、置顶变化、删除，因此客户端用 `since` 拉取即可不丢变更。详见 [ADR-0001](./adr/0001-server-seq-change-feed.md)。

### 9.2 服务端保留

- 活跃记录：置顶记录永久保留；非置顶记录按 `createdAt` 保留最近 500 条。
- 超出 500 条的非置顶记录先软删除（生成墓碑与新的 `serverSeq`），不是直接物理删除；这样客户端能收到删除事件。
- 墓碑保留 30 天，之后物理删除并推进 `syncFloor`。
- 清理任务每天运行一次，启动时也运行一次；清理期间可能短暂超过 500 条，下一次清理收敛。
- 分享页按自身 `expiresAt` 清理，与剪贴记录保留无关。

### 9.3 客户端保留

- 本地展示最近 200 条非置顶记录 + 全部置顶记录。
- 本地裁剪只影响本地展示，不回传服务端，不产生删除请求。
- 服务端保留量始终大于客户端展示量，因此服务端因 500 条上限软删除的旧记录，客户端本地早已不在展示范围内；墓碑仍然保证离线设备的删除可见性。

### 9.4 全量重同步

当增量响应返回 `resyncRequired=true`：

1. 暂停 Outbox 发送与 WebSocket 事件应用。
2. 保留 `outbox`、待创建/待删除的本地记录与设置。
3. 清空已同步的本地记录，`lastSeq = 0`。
4. 从 `since=0` 分页拉取直到 `hasMore=false`。
5. 恢复 Outbox 发送，再执行一次增量补拉。

### 9.5 幂等与重试

- 创建以客户端 ULID 为幂等键；同 ID 同内容重复提交返回 200，不重复入库。
- 置顶与删除重复提交不重复推进序号。
- WebSocket 只做实时通知，任何“是否漏消息”的判断都以 REST `since` 补拉为准。

### 9.6 多设备

- 每个设备独立令牌；所有设备共享同一个用户空间。
- 一个设备写入后，其他在线设备通过 WebSocket 实时收到；离线设备下次打开时通过 `since` 补拉。
- 来源设备名由服务端联查返回，客户端按 `sourceDeviceId == 本机 deviceId` 显示“本机”。

## 10. 端到端示例

### 10.1 生成配对码并配对

```bash
# 1. 管理端生成配对码
curl -sS -X POST https://clip.example.com/api/v1/admin/pairing-codes \
  -H "X-Admin-Token: $ADMIN_TOKEN"

# 2. 客户端配对
curl -sS https://clip.example.com/api/v1/pair \
  -H 'Content-Type: application/json' \
  -d '{"code":"123456","deviceName":"Pixel 8"}'
```

### 10.2 创建并同步

```bash
# 手机创建
curl -sS https://clip.example.com/api/v1/clips \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"id":"01J8Z6Z3K4M5N6P7Q8R9S0T1V2","content":"hello","createdAt":1730000000000}'

# 桌面增量拉取
curl -sS 'https://clip.example.com/api/v1/clips?since=0&limit=200' \
  -H "Authorization: Bearer $DESKTOP_TOKEN"
```

### 10.3 置顶、删除与分享

```bash
curl -sS -X PATCH https://clip.example.com/api/v1/clips/01J8Z6Z3K4M5N6P7Q8R9S0T1V2 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"pinned":true}'

curl -sS -X DELETE https://clip.example.com/api/v1/clips/01J8Z6Z3K4M5N6P7Q8R9S0T1V2 \
  -H "Authorization: Bearer $TOKEN"

curl -sS https://clip.example.com/api/v1/shares \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"clipId":"01J8Z6Z3K4M5N6P7Q8R9S0T1V2","ttlSeconds":86400,"oneTime":true}'
```

## 11. 与交接文档 v0.1 的差异

| 项 | 交接文档 v0.1 | v1.0.0 | 原因 |
| --- | --- | --- | --- |
| 增量游标 | `after` / `before` 使用 ULID | `since` 使用 `serverSeq` | 修复补传旧 ULID 与离线删除漏同步，见 ADR-0001 |
| 同步响应 | `items` + `nextCursor` | `items` + `nextSince` + `hasMore` + `resyncRequired` + `minAvailableSeq` + `serverMaxSeq` | 支持分页、全量重同步与清理边界 |
| 历史翻页 | `before` 拉取 | 不提供，客户端本地历史 | 本地库是唯一数据源，减少服务端接口 |
| 请求体上限 | 128 KiB | 512 KiB | 覆盖 64 KiB 正文的最坏 JSON 转义膨胀 |
| 正文超限状态码 | 413 | 400 `VALIDATION_ERROR`；请求体超限仍为 413 | 区分字段大小校验与整个请求体上限 |
| 创建幂等 | 未定义 | 同 ID 同内容 200，不同内容 409 | Outbox 重试不产生重复记录 |
| 删除幂等 | 未定义 | 已删除再次删除返回 200 | 超时重试安全 |
| WebSocket 事件 | 无 seq | 每条事件带 `seq`，广播含来源方 | 缺口检测与幂等应用 |
| WebSocket 补拉 | 未明确 | 不做重放，一律 REST `since` 补拉 | 只有一条补拉路径，避免双通道乱序 |
| 分享存储 | `clip_id` 引用 | 内容快照 + `token_hash` | 分享生命周期独立于剪贴记录，见 ADR-0002 |
| 分享令牌 | 明文主键 | 只存 SHA-256 哈希 | 降低数据库泄露时的链接风险 |
| 来源设备 | 只有 `source_device_id` | 响应增加 `sourceDeviceName` | 历史列表直接展示来源设备 |
| 错误码 | 仅示例 | 完整错误码表 | 客户端稳定分支与文案映射 |
| 限流 | 仅配对 5 次/分钟/IP | 配对、管理、设备、分享页、WS 分项限流 | 公网暴露面收敛 |
| 健康检查 | 仅状态 | 增加 `version` 与 `time` | 容器排障与版本确认 |
| 透明复制页 | MVP 界面清单 | P2（v1.0.0 不实现） | v1.0.0 没有后台通知，透明页无触发入口 |

## 12. 变更记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0.0 | 2026-11-13 | 初版：基于交接文档 v0.1、ADR-0001、ADR-0002 冻结 API 契约 |
