# Clipbox 项目交接文档

| 字段     | 内容                                                 |
| -------- | ---------------------------------------------------- |
| 文档版本 | v0.1                                                 |
| 更新日期 | 2026-11-13                                           |
| 当前状态 | 立项与设计完成，尚未开始编码                         |
| 项目代号 | clipbox（最终名称待定）                              |
| 包名占位 | `dev.qoder.clipbox`                                  |
| 目标平台 | Android + Desktop（Compose Multiplatform）；iOS 后置 |
| 服务器   | 已有：2 核 CPU / 2GB 内存 / 3Mbps 带宽               |

一句话定位：**离线优先的跨设备文本中转 + 开发者本地工具箱，服务器只承担小数据同步。**

> 后续开发以本文档为基线；范围或方案有变更时，追加到文末「变更记录」。

---

## 1. 背景与目标

- 发起人：Android 开发工程师，熟悉 Kotlin / Compose。
- 目的：产出日常自用的跨端效率工具，同时作为 Compose Multiplatform + Ktor 全栈练手项目。
- 服务器约束：2 核 2G，3Mbps（约 375KB/s 总出口）。据此确定产品边界：**只传文本，不传图片 / 音视频 / 大文件**。核心功能尽量放本地，服务器只承担设备配对、文本中转、历史同步和一次性分享页。

## 2. 产品范围

### 2.1 P0（MVP，必须）

1. **设备配对**：服务端签发 6 位配对码（10 分钟有效、一次性），客户端输入后获得设备 token。
2. **文本发送**
   - Android：系统分享（`ACTION_SEND` text/plain）+ 文本选中菜单（`ACTION_PROCESS_TEXT`）+ App 内输入框。
   - Desktop：窗口内输入框发送；可选监听系统剪贴板自动发送（默认关闭）。
3. **文本接收**
   - Desktop：WebSocket 实时接收，自动写入系统剪贴板（可开关），同时保留历史。
   - Android：App 打开 / 前台时同步并展示最新文本，一键复制（P0 不做后台推送，原因见 §10.3）。
4. **历史记录**：双端展示最近 200 条（服务端保留 500 条），支持搜索、置顶、软删除。
5. **服务端限额**：单条文本 ≤ 64KB（UTF-8），请求体上限 128KB，超限返回 413；置顶记录不参与自动清理。

### 2.2 P1

- **一次性分享链接**：长文本生成随机短链，浏览器打开只读页面（默认 24 小时过期，支持阅后即焚）。
- **片段收藏夹**：标题 + 正文 + 标签，双端同步（冲突用 LWW，见 §9）。
- **工具箱首批 5 个工具（纯本地）**：JSON 格式化 / 压缩 / 校验、Base64、URL 编解码、时间戳转换、正则测试。
- **Android 后台通知复制**：FCM 或前台常驻服务二选一（见 §10.3）。

### 2.3 P2

- iOS 端（需要 Mac 构建环境）。
- 桌面全局快捷键 / 托盘快速发送。
- 工具箱扩展：JWT 解析、SHA/MD5 哈希、UUID/ULID 生成、颜色转换、二维码等。
- 设备管理与 token 撤销、端到端加密评估（见 §11）。

### 2.4 非目标

- 图片、文件、音视频传输或云盘能力（3Mbps 带宽不支持）。
- 多用户注册、开放平台、社交分享（单用户 + 多设备即可）。
- Android 全自动后台剪贴板监听（系统限制，见 §10.1）。
- 服务端 AI 能力（2GB 内存不支持有意义的本地推理）。

### 2.5 MVP 界面清单

- **历史（首页）**：发送输入框 + 记录列表（内容预览、来源设备、时间、复制 / 置顶 / 删除）+ 搜索。
- **工具箱**：工具入口网格。
- **设置**：服务器地址、设备配对、自动粘贴开关、主题。
- Android 透明复制页：无界面，处理通知「复制」动作。
- Desktop：同历史页布局，窗口发送；托盘为 P1。

## 3. 核心用户流程

1. **配对（一次性）**：服务端管理接口生成配对码 → 手机 / 桌面输入 → 换取设备 token（本地持久化）。
2. **手机 → 桌面**：选中文本 → 分享 / 选中菜单 → Clipbox → 上传 → 桌面 WebSocket 实时收到 → 写入剪贴板。
3. **桌面 → 手机**：发送 → 手机打开 App 即见最新文本，一键复制。
4. **长文本分享**：发送时选择「生成链接」→ 得到 URL → 任意浏览器打开只读页。
5. **管理**：双端可搜索、置顶、删除历史；离线操作进入 outbox，联网后自动重放。

## 4. 总体架构

```
┌────────────────┐        HTTPS / WSS        ┌───────────────────────┐
│ Android (CMP)  │ ────────────────────────▶ │  Ktor Server (Netty)  │
├────────────────┤                           │  SQLite (WAL)         │
│ Desktop (CMP)  │ ◀──────────────────────── │  文本 / 历史 / 分享页  │
└────────────────┘                           └───────────────────────┘
        │                                                │
        └────────────── 同一份 shared 模块 ───────────────┘
              （模型 / API client / 同步引擎 / Room）
```

- 客户端离线优先：本地库为唯一数据源，服务端是同步中枢。
- 服务端单实例容器化部署，SQLite 落盘。

## 5. 技术栈

| 层         | 选型                                   | 说明                                   |
| ---------- | -------------------------------------- | -------------------------------------- |
| UI         | Compose Multiplatform                  | Android + Desktop 共享页面与 ViewModel |
| 客户端网络 | Ktor Client                            | Android 用 OkHttp 引擎，Desktop 用 CIO |
| 本地存储   | Room KMP（SQLite）+ DataStore          | 历史、outbox、设置；token 存 DataStore |
| 服务端     | Ktor Server（Netty）+ Exposed + SQLite | 单 JAR，`-Xmx256m`，常驻约 250MB       |
| 实时通道   | WebSocket（Ktor 双端支持）             | 桌面常连；Android 前台连接             |
| 部署       | Docker Compose + Caddy                 | Caddy 自动签发 HTTPS                   |
| 构建       | Gradle Version Catalog                 | 工程以 JetBrains KMP 模板初始化        |

客户端最低要求：Android 8.0+（API 26）；Desktop 支持 Windows / macOS / Linux。

## 6. 工程结构

```
clipbox/
├── composeApp/                  # Compose Multiplatform UI
│   └── src/
│       ├── commonMain/          # 页面、ViewModel、导航、主题
│       ├── androidMain/         # Share 入口、透明复制 Activity、剪贴板
│       └── desktopMain/         # AWT 剪贴板监听/写入、窗口、托盘
├── shared/                      # 纯 Kotlin 共享层
│   └── src/commonMain/          # 模型、Ktor client、Room、同步引擎(outbox/cursor)
├── server/                      # 独立 Gradle build：Ktor + SQLite
│   └── src/main/kotlin/         # routes / db / auth / share-page
├── docs/
│   ├── HANDOFF.md               # 本文档
│   └── api.md                   # 详细 API 契约（编码阶段补充）
├── docker-compose.yml
├── Caddyfile
└── README.md                    # 快速启动说明（编码阶段补充）
```

单人不拆 feature module，避免过度设计；`shared` 内部按包组织（model / network / db / sync）。

## 7. 数据模型

### 7.1 服务端 SQLite Schema

```sql
CREATE TABLE devices (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  token_hash    TEXT NOT NULL UNIQUE,   -- SHA-256(token)
  created_at    INTEGER NOT NULL,       -- epoch millis
  last_seen_at  INTEGER
);

CREATE TABLE clips (
  id                TEXT PRIMARY KEY,   -- ULID，客户端生成
  content           TEXT NOT NULL,
  source_device_id  TEXT,
  pinned            INTEGER NOT NULL DEFAULT 0,
  created_at        INTEGER NOT NULL,
  deleted_at        INTEGER            -- 软删除，供同步使用
);

CREATE TABLE snippets (                 -- P1
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL,
  content     TEXT NOT NULL,
  tags        TEXT NOT NULL DEFAULT '',
  updated_at  INTEGER NOT NULL,
  deleted_at  INTEGER
);

CREATE TABLE pairing_codes (
  code        TEXT PRIMARY KEY,         -- 6 位数字
  expires_at  INTEGER NOT NULL,
  used_at     INTEGER
);

CREATE TABLE shares (                   -- P1
  token       TEXT PRIMARY KEY,         -- 随机串（≥128 bit）
  clip_id     TEXT NOT NULL,
  expires_at  INTEGER NOT NULL,
  one_time    INTEGER NOT NULL DEFAULT 0,
  opened_at   INTEGER
);

CREATE INDEX idx_clips_created ON clips(created_at);
```

### 7.2 客户端本地表（Room）

- `clips`：同服务端字段 + `sync_state`（synced / pending / delete_pending）。
- `outbox`：操作队列（POST / PATCH / DELETE + payload + retry_count + next_retry_at）。
- `meta`：同步游标（最后拉取到的 ULID）等。

## 8. API 契约（v1 草案）

认证：除 pairing、分享页、healthz 外，均需 `Authorization: Bearer <device-token>`。

| 方法   | 路径                                  | 认证            | 说明                                                         |
| ------ | ------------------------------------- | --------------- | ------------------------------------------------------------ |
| POST   | `/api/v1/pair`                        | 无              | `{code, deviceName}` → `{deviceId, token}`（token 仅返回一次） |
| GET    | `/api/v1/clips?after=&before=&limit=` | Bearer          | `after` 增量拉取（升序），`before` 历史翻页                  |
| POST   | `/api/v1/clips`                       | Bearer          | `{id, content, createdAt}` → 201                             |
| PATCH  | `/api/v1/clips/{id}`                  | Bearer          | `{pinned}`                                                   |
| DELETE | `/api/v1/clips/{id}`                  | Bearer          | 软删除                                                       |
| WS     | `/api/v1/sync`                        | 首帧认证        | 服务端推送事件                                               |
| POST   | `/api/v1/shares`                      | Bearer          | P1；`{clipId, ttlSeconds, oneTime}` → `{url, expiresAt}`     |
| GET    | `/s/{token}`                          | 无              | P1；只读 HTML 页面                                           |
| POST   | `/api/v1/admin/pairing-codes`         | `X-Admin-Token` | 生成配对码                                                   |
| GET    | `/healthz`                            | 无              | 健康检查                                                     |

请求 / 响应示例：

```
POST /api/v1/clips
{"id":"01J8Z...","content":"hello","createdAt":1730000000000}
→ 201 {"id":"01J8Z...","createdAt":1730000000000}

GET /api/v1/clips?after=01J8Z...&limit=50
→ 200 {"items":[...],"nextCursor":"01J9..."|null}
```

WebSocket 事件（客户端上传一律走 HTTP，保证重试语义）：

```json
{"type":"auth","token":"..."}
{"type":"auth.ok"}
{"type":"clip.created","data":{"id":"01J8Z...","content":"hello","createdAt":1730000000000}}
{"type":"clip.updated","data":{"id":"01J8Z...","pinned":1}}
{"type":"clip.deleted","data":{"id":"01J8Z..."}}
```

错误格式：`{"error":{"code":"PAYLOAD_TOO_LARGE","message":"..."}}`。

## 9. 同步与离线策略

- **主键**：客户端生成 ULID，字典序即时间序，天然支持游标与离线创建。
- **写入路径**：先写本地库（pending）→ 入 outbox → 异步 POST → 成功后标记 synced。
- **重试**：指数退避 1s → 2s → 4s … 上限 60s，outbox 持久化，重启不丢。
- **实时**：桌面常连 WebSocket；重连后用 `after` 游标补拉，消息不丢不重（按 id upsert）。
- **删除 / 置顶**：本地先改 + 入队，失败重试；服务端软删除并随同步下发。
- **冲突**：clips 为追加型数据，几乎无冲突；snippets 用 `updated_at` LWW。
- **清理**：服务端保留最近 500 条（置顶除外，超 30 天且非置顶可清）；客户端只保留 200 条。
- **防循环**：桌面自动写入剪贴板后，记录内容哈希并抑制回传（见 §10.4）。

## 10. 平台关键实现细节

### 10.1 Android 剪贴板限制（重要）

Android 10 起，应用只有在获得焦点（前台）时才能读取剪贴板；后台写入同样受限。因此：

- **不做**全自动后台剪贴板同步。
- **发送入口**：系统分享 `ACTION_SEND`（text/plain）+ 文本选中菜单 `ACTION_PROCESS_TEXT` + App 内输入框。
- **接收**：通知「复制」按钮 → `PendingIntent` → 打开透明 Activity（`@android:style/Theme.Translucent.NoTitleBar`）→ `setPrimaryClip` → Toast → `finish()`。这是同类工具的标准做法。
- **同步时机**：App 进入前台建立 WebSocket；`onResume` 用游标补拉。

Manifest 参考：

```xml
<activity android:name=".ShareReceiverActivity" android:exported="true">
  <intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
  </intent-filter>
  <intent-filter>
    <action android:name="android.intent.action.PROCESS_TEXT" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
  </intent-filter>
</activity>
```

### 10.2 Desktop 剪贴板

- 使用 AWT `Toolkit.getDefaultToolkit().systemClipboard` 读写。
- 监听用 500ms 轮询 + 内容哈希比对兜底（Linux/Wayland 下 `FlavorListener` 事件不可靠）。
- 自动发送开关：复制后 debounce 800ms 再上传，避免连续复制产生大量记录。
- 收到远端文本时自动写入（可关闭），写入后弹系统通知（P1）。

### 10.3 推送通道决策（Android 后台通知）

| 方案            | 实时性     | 依赖 / 代价                         | 结论                     |
| --------------- | ---------- | ----------------------------------- | ------------------------ |
| 仅打开 App 同步 | 无后台通知 | 零依赖，最简单                      | **P0 采用**              |
| FCM             | 高，省电   | 依赖 Google 服务，国内可用性差      | P1 备选                  |
| 前台服务常连 WS | 高         | 耗电、 OEM 可能杀进程，需引导白名单 | P1 备选（不依赖 Google） |

P0 的定位是：桌面端负责实时粘贴，手机端打开即同步。后台通知按需在 P1 补充。

### 10.4 防回环

记录 `lastAppliedHash`（最后写入剪贴板的内容）与 `lastSentHash`（最后上传的内容）。写入 / 监听时若哈希命中且时间窗在 2 秒内则跳过，避免「自动粘贴 → 自动上传」无限循环。

## 11. 安全设计

- 全站 HTTPS / WSS（Caddy 自动证书），禁用明文端口访问业务接口。
- 配对码：6 位数字、10 分钟有效、单次使用；失败限速（如 5 次/分钟/IP）；启动时若无可用配对码，自动生成并打印到容器日志，便于自助配对。
- 设备 token：32 字节随机数，服务端只存 SHA-256 哈希；客户端存 DataStore。
- 分享页：token ≥128 bit 随机；默认 24h 过期；HTML 输出严格转义 + CSP（`default-src 'none'; style-src 'unsafe-inline'`）+ `X-Content-Type-Options: nosniff`；不加载外部资源、不执行 JS。
- 管理接口：仅 `X-Admin-Token` 可访问，建议 Caddy 层限制 admin 路径来源 IP。
- 服务端日志不记录文本内容；备份文件权限 600。
- **加密边界说明**：当前设计为传输加密 + 服务端明文存储（自用单用户）。若未来做端到端加密，服务器只存密文，分享网页需要配套解密方案，建议 P2 单独评估。

## 12. 部署与运维

`docker-compose.yml`（示意）：

```yaml
services:
  clipbox:
    build: ./server
    environment:
      - ADMIN_TOKEN=${ADMIN_TOKEN}
      - DATA_DIR=/data
      - MAX_CLIP_BYTES=65536
      - JAVA_OPTS=-Xmx256m
    volumes:
      - ./data:/data
    restart: unless-stopped

  caddy:
    image: caddy:2
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    restart: unless-stopped
```

`Caddyfile`（示意）：

```
clip.example.com {
    reverse_proxy clipbox:8080
}
```

- 备份：宿主机 cron 每日 `sqlite3 /data/clipbox.db ".backup ..."`，保留 7 天；数据量很小，备份成本可忽略。
- 升级：`docker compose up -d --build`；SQLite 迁移用版本化 SQL 脚本。
- 资源上限：JVM `-Xmx256m`，容器总占用不超过约 400MB，2GB 服务器留有充足余量。

## 13. 开发环境与快速开始

- JDK 17+（建议 21）、Android Studio 最新稳定版（安装 KMP 插件）、Docker + Compose。
- 用 JetBrains KMP 模板生成 `composeApp`（Android + Desktop 目标），按 §6 增加 `shared` 与 `server`。
- 本地联调：`server` 直接 `./gradlew run`，客户端设置页填 `http://localhost:8080`（正式环境必须 HTTPS）。
- iOS 目标暂不创建，后续在 Mac 上通过模板添加，`shared` 无需改动。

## 14. 里程碑计划（业余时间约 4 周）

| 周      | 交付物                                                   | 验收                           |
| ------- | -------------------------------------------------------- | ------------------------------ |
| 第 1 周 | 工程骨架；服务端 P0（配对、clips CRUD、WS、Docker）      | curl 跑通全流程                |
| 第 2 周 | `shared` 同步引擎；双端 UI 基础；手机分享 → 桌面粘贴闭环 | 真实设备上文本 5 秒内到达桌面  |
| 第 3 周 | 历史搜索 / 置顶 / 删除；分享链接；设置页                 | 分享页浏览器可读、XSS 转义正确 |
| 第 4 周 | 工具箱 5 个工具；推送通道（可选）；测试与打磨            | 通过 §15 验收清单              |

## 15. 验收清单（Definition of Done）

- [ ] 完成配对后，手机分享文本，桌面 ≤5s 出现在系统剪贴板。
- [ ] 桌面发送文本，手机打开 App ≤3s 可见并可一键复制。
- [ ] 断网时发送进入 outbox，恢复后自动补发且不重复。
- [ ] WebSocket 断开重连后自动补拉，消息不丢不重。
- [ ] >64KB 文本被拒绝且提示明确。
- [ ] 分享页对 `<script>alert(1)</script>` 原样展示（转义生效）。
- [ ] 重启服务端数据完整；重启客户端历史完整。
- [ ] 桌面自动粘贴开启时，不产生写入→上传回环。
- [ ] 资源观察：服务端常驻内存 <400MB，日常同步带宽占用可忽略。

## 16. 风险与应对

| 风险                               | 影响                 | 应对                                         |
| ---------------------------------- | -------------------- | -------------------------------------------- |
| Linux/Wayland 剪贴板监听差异       | 桌面自动发送失灵     | 500ms 轮询 + 哈希去重兜底                    |
| OEM 后台限制（Android）            | 后台通知不可靠       | P0 不依赖常驻；P1 引导加白名单或走 FCM       |
| Room KMP / Ktor / Compose 版本适配 | 构建不稳定           | 以模板锁定版本，不追新，升级单独验证         |
| 范围膨胀                           | 无法按时出 MVP       | 工具箱严格本地、零权限；按周表推进           |
| 单点服务无高可用                   | 宕机期间无法跨端同步 | 每日备份；客户端离线队列保证恢复后同步       |
| 正则测试卡死 UI                    | 体验问题             | 限制输入 100KB，计算放 `Dispatchers.Default` |

## 17. 待定事项

- [ ] 应用名与包名（当前占位：clipbox / `dev.qoder.clipbox`）。
- [ ] 域名（Caddy 签发证书需要）。
- [ ] 是否做端到端加密（建议 P2 评估）。
- [ ] iOS 排期（需 Mac）。
- [ ] Android 推送通道选型（FCM vs 前台服务）。
- [ ] 工具箱是否加入正则模板库等增强功能。

## 附录 A：工具箱清单

**首批（P1，全部纯本地）**

1. JSON 格式化 / 压缩 / 校验
2. Base64 编解码（标准 / URL-safe）
3. URL 编解码
4. 时间戳 ↔ 日期（本地时区 / UTC）
5. 正则测试（匹配高亮、分组展示；输入上限 100KB）

**后续候选**：JWT 解析、SHA/MD5 哈希、UUID/ULID 生成、Cron 表达式解析、颜色转换、二维码生成 / 识别。

## 附录 B：资源占用估算

- 服务端：JVM 约 250MB 常驻（`-Xmx256m`），SQLite <10MB（500 条文本规模）。
- 带宽：单条 64KB 上传在 3Mbps 下约 0.2s（理想值）；日常单条几 KB，每天 100 条约 0.5MB，带宽余量充足。
- 存储：每条记录平均 1KB 量级，长期使用无压力。

## 变更记录

| 版本 | 日期       | 变更                                                 |
| ---- | ---------- | ---------------------------------------------------- |
| v0.1 | 2026-11-13 | 初稿：确定产品范围、架构、API 草案、里程碑与验收标准 |