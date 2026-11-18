# BUG-001：墓碑清理后全量分页无法完成

- 状态：已修复（方案 A）
- 发现于：T-UI-20 本地端到端验收（M3 Gate）
- 修复于：2026-11-18
- 关联需求：AC-11、SYNC-011…015
- 关联卡：T-S-04（增量查询）、T-S-08（清理与墓碑）、T-CORE-08（游标与重同步）

## 现象

服务端执行保留策略与墓碑清理后，新设备或需全量重同步的客户端按文档从 `since=0` 分页拉取，第一页正常返回，但后续页请求会被服务端判定为 `resyncRequired`，无法按 `hasMore=false` 完成全量同步。

## 复现

环境：隔离服务端 `PORT=18080 DATA_DIR=/tmp/clipbox-ac11`，不接触主数据。

1. 准备 501 条活跃非置顶记录并触发保留策略：软删除 1 条，写入新 seq 505，活跃 500 条。
2. 清理 2 个超过 30 天的墓碑，`sync_floor` 推进到 504，`minAvailableSeq=505`。
3. `GET /api/v1/clips?since=0&limit=200` → 200，`items` 为 seq 2–201，`hasMore=true`，`nextSince=201`。
4. `GET /api/v1/clips?since=201&limit=200` → 200，`resyncRequired=true`，`items=[]`。
5. `GET /api/v1/clips?since=401&limit=200` → 同样 `resyncRequired=true`。

证据：`/tmp/e2e-ac11-p2.json`、`/tmp/e2e-ac11-p3.json`、`/tmp/e2e-ac11-server*.log`。

## 预期

按 `docs/api.md` §3.1：`since=0` 为全量同步，不以 `resyncRequired` 拒绝；客户端按页拉取直到 `hasMore=false`。按 `docs/服务端技术架构.md` §6.4：`since=0` 返回全部活跃记录与保留期墓碑。

## 根因

`ClipService` 的统一判定为 `resyncRequired = (since > 0 && since < syncFloor) || since > serverMaxSeq`。全量分页的续页同样携带 `since>0`，服务端无状态，无法区分「全量分页续页」与「增量游标」，于是把合法的续页请求当成过期游标。

冻结文档同时要求「`since=0` 分页到 `hasMore=false`」与「`since < sync_floor` 必须重同步」，但没有定义两者如何共存，属于接口契约空洞，而非单纯编码错误。

## 影响

- 墓碑清理后，只要 `sync_floor` 高于最早活跃记录的序号，新设备或重同步客户端就无法完成全量拉取。
- 客户端 `SyncEngine.resyncLocked()` 会在全量分页中把再次出现的 `resyncRequired` 判为服务端违约，最终以 `INVALID_RESPONSE` 失败。
- 日常增量同步不受影响；现有 `RetentionTest` 未覆盖 `sync_floor>0` 的多页全量场景。

## 候选方案

| 方案 | 内容 | 代价 |
| --- | --- | --- |
| A（推荐） | 为全量同步增加显式语义，例如 `full=true` 或全量续页令牌，使服务端能区分续页与增量游标 | 修改 `api.md`，客户端与服务端同步改；需文档所有者批准 |
| B | 全量同步始终从 `sync_floor` 之后开始，首页不返回已清理区间之前的活跃记录 | 会丢失 `sync_floor` 之前仍活跃的历史，违反「历史保留」预期，不推荐 |
| C | 调整 `sync_floor` 语义，仅在增量请求时校验下限 | 可能让客户端在增量同步中漏掉已清理区间，需重新证明正确性 |

## 验收标准（方案确定后）

- `since=0` 分页在 `sync_floor>0` 时能拉完所有活跃记录并以 `hasMore=false` 结束。
- 增量 `since < sync_floor` 仍返回 `resyncRequired=true`，重同步后游标不倒退。
- 新增集成测试覆盖「501 条 + 软删 + 墓碑清理 + 多页全量拉取」。
- 客户端 `SyncEngine` 全量分页不再因合法续页触发 `INVALID_RESPONSE`。

## 约束

- 本卡不修改已冻结的 `docs/api.md` / 架构文档；方案 A 需用户与文档所有者确认后实施。
- 修复前不得通过放宽客户端校验或忽略 `resyncRequired` 来掩盖问题。

## 修复记录（方案 A）

用户确认采用方案 A，并授权同步修订契约、架构、需求与验收记录；首次配对（`lastSeq=0`）的分页与强制重同步统一携带 `full=true`。

协议：`GET /api/v1/clips` 新增可选参数 `full`（默认 `false`，只接受字面量 `true` / `false`）。`full=true` 时跳过 `since < sync_floor` 判定，仍保留 `since > serverMaxSeq` 重置保护；增量请求不得携带。

实现：

- 服务端 `ClipService.sync(since, limit, full)`：`resyncRequired = (!full && since > 0 && since < syncFloor) || since > serverMaxSeq`；`ClipRoutes` 严格解析 `full`，非法取值与非法 `since`/`limit` 一并返回 400 `VALIDATION_ERROR`。
- 客户端 `ApiService.getClips(..., full)` 仅在全量时附带 `full=true`；`SyncEngine.pullLocked()` 在起始 `lastSeq=0` 时整轮分页携带 `full=true`，`resyncLocked()` 所有页携带 `full=true`，增量请求省略。
- 文档同步修订：`api.md` §3.1/§4.3/§9.4、服务端架构 §6.4/§9.3/§13、客户端架构 §6.3.2/§6.5.4/§6.5.5/§13、功能需求 REQ-SYNC-005/008 与 AC-11、GLOSSARY。

测试证据（2026-11-18）：

- `ClipboxServer ./gradlew test --rerun`：BUILD SUCCESSFUL。
- 新增集成用例 `SyncFlowIntegrationTest.full sync completes after retention purge with non zero sync floor`：501 条活跃 + 2 个过期墓碑 → 清理后 `sync_floor=503`；`since=201` 不带 `full` 返回 `resyncRequired=true`；`full=true` 续页拉完 501 条（500 活跃 + 1 墓碑）并以 `hasMore=false` 结束；`since=999999&full=true` 仍重同步。
- `ClipQueryTest` 新增 floor 跨页与 `full` 越界用例，并覆盖 `full=1`、`full=`、`full=TRUE` → 400。
- `ClipboxClient ./gradlew :shared:desktopTest` 与 `:shared:allTests`：BUILD SUCCESSFUL；`SyncEnginePullTest` 覆盖首次配对全量分页逐页 `full=true`、增量不带 `full`；`SyncEngineResyncTest.fullResyncPagesCarryFullTrueAndCompleteAcrossFloor` 覆盖重同步跨 floor 续页。

提交：`BUG-001: 为全量同步新增 full 参数，修复墓碑清理后分页中断`（未 push）。
