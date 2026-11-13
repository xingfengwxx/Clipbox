# 用服务端变更序列替代 ULID 游标

**Status**: accepted

## Context

交接文档 §8/§9 以客户端生成的 ULID 作为增量同步游标（`after=<ULID>`）。该方案在两种场景会漏同步：设备离线创建较早 ULID 的记录后补传，其他设备的游标已越过该 ULID；老记录的删除/置顶不改变 ULID，离线设备无法通过 `after` 获得墓碑。ULID 仍适合作为主键与展示排序，但不适合作为变更游标。

## Decision

服务端为每条剪贴记录维护单调递增的 `server_seq`；创建、置顶、删除都推进序号。客户端以 `since=<seq>` 拉取全部变更，WebSocket 事件携带 seq；客户端持久化 `lastSeq`。墓碑保留 30 天；客户端游标早于服务端最小可用序号时，响应返回 `resyncRequired`，客户端保留待发队列并全量重拉。ULID 继续作为记录主键和展示排序依据。

## Considered Options

- 保留 ULID 游标 + 独立墓碑接口：仍无法覆盖补传的旧 ULID，需要定期全量对账。
- 每次启动全量拉取最近 500 条：实现简单，但放弃增量语义，流量与延迟随数据量增长。

## Consequences

- `GET /api/v1/clips` 从 `after` 改为 `since`，响应增加 `nextSince`、`hasMore`、`resyncRequired`、`minAvailableSeq`、`serverMaxSeq`。
- 所有写操作（创建/置顶/删除）都必须在同一事务内推进 `server_seq`。
- 客户端 `meta` 表保存 `lastSeq`；WebSocket 仅用于实时通知，断线补拉一律走 REST。
- 这是对交接文档 v0.1 API 草案的有意偏离，四份 v1.0.0 文档需注明差异。
