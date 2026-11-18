# T-REL-04 备份与恢复演练记录

- 执行日期：2026-11-18
- 需求：REQ-NFR-013、REQ-PLAT-006
- 环境：Ubuntu 24.04 / Docker Compose / 时区 Asia/Shanghai；数据库 SQLite（WAL），单实例
- 结论：每日 03:17 自动备份已上线并实测触发；保留 7 份、权限 600（目录 700）；恢复演练完整通过（停服 → 安全备份 → 换库 → 启动 → 完整性 / 增量 / 重同步语义验证），并已回滚到演练前状态。

## 1. 路径映射

任务卡中的 `/data` 是容器内路径；compose 将宿主机 `./data` 绑定到容器 `/data`。本机部署目录为 `/root/clipbox`，因此：

| 用途 | 容器内 | 宿主机 |
| --- | --- | --- |
| 数据库 | `/data/clipbox.db` | `/root/clipbox/data/clipbox.db` |
| 备份目录 | `/data/backups/` | `/root/clipbox/data/backups/` |
| 备份日志 | `/data/backups/backup.log` | `/root/clipbox/data/backups/backup.log` |
| 恢复回滚目录 | — | `/root/clipbox/data/rollback-<timestamp>/` |

验收命令按宿主机路径执行，等价于任务卡命令；未创建 `/data` 符号链接。

## 2. 备份脚本

仓库路径：`scripts/backup.sh`；服务器部署路径：`/root/clipbox/scripts/backup.sh`（755）。

行为：

- 数据目录默认取脚本目录上级 `data/`，可用 `CLIPBOX_DATA_DIR` 覆盖（本地测试）；
- 使用 `sqlite3 "$DB" ".timeout 10000" ".backup '<tmp>'"` 生成 WAL 一致性快照，不 `cp` 正在写入的库；
- 备份先写临时文件，校验 `PRAGMA integrity_check` 为 `ok`、权限 600 后再原子改名；失败写日志并以非零退出；
- 目录 700、文件 600、日志 600；日志只记录时间、文件路径、大小与状态；
- 同名目标已存在时记录 `SKIP` 并成功退出（幂等）；
- 保留策略：先删除超过 7 天的备份，再兜底只保留最新 7 份 `clipbox-*.db`（同时满足架构「7 天」与 REQ-NFR-013「最近 7 份」）。

本地验证（macOS，sqlite3 3.54.0）：

| 检查 | 结果 |
| --- | --- |
| `bash -n scripts/backup.sh` | 语法通过 |
| 临时 WAL 库备份 | 目录 700、文件 600、`integrity_check=ok`，备份内含测试数据 |
| 备份日志泄漏检查 | 日志无正文、无令牌 |
| 同一秒重跑 | 记录 `SKIP exists=...`，不产生新文件 |
| 造 12 份备份后运行 | 收敛为 7 份 |
| 数据目录缺库 | 退出码 1，日志记录 `ERROR database not found` |

## 3. 服务器部署

1. 宿主机安装 SQLite CLI（用户确认后执行）：`apt-get install -y sqlite3`，版本 `3.45.1 2024-01-30`。
2. 上传脚本并授权：`/root/clipbox/scripts/backup.sh`（`-rwxr-xr-x root:root`）。
3. 首次手动备份生成基线：`clipbox-20261118-111230.db`，完整性 `ok`。
4. `/etc/cron.d/clipbox-backup`（root:root 644）：

```cron
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
MAILTO=""
17 3 * * * root umask 077; mkdir -p /root/clipbox/data/backups && chmod 700 /root/clipbox/data/backups && /root/clipbox/scripts/backup.sh >> /root/clipbox/data/backups/backup.log 2>&1
```

5. 自动调度实测：临时改为每分钟，cron 于 11:13:01 真实触发并生成 `clipbox-20261118-111301.db`；`journalctl -u cron` 记录 `CRON[...] (root) CMD (...backup.sh...)`。随后改回每日 `17 3 * * *`。
6. 最小环境验证：`env -i SHELL=/bin/bash PATH=... /root/clipbox/scripts/backup.sh` 成功生成备份，证明不依赖登录环境。
7. 保留策略实测：加入 8 份近期假备份与 1 份 10 天前假备份，运行脚本后由 11 份收敛到 7 份，过期文件被删除；假文件已清理。

## 4. 恢复演练

演练设备：`DR-DRILL`（配对接口创建；服务端无删除设备接口，演练后保留）。令牌仅存于服务器 root 专属临时文件，演练结束已删除。

计数口径：`devices, 活跃 clips, 墓碑 clips, shares, meta.server_seq`。

| 阶段 | 时间 | 操作 | 结果 |
| --- | --- | --- | --- |
| 演练前 | 11:14 | 记录计数 | `3,1,0,1,1` |
| 标记数据 | 11:15:44 | 配对 D1；创建标记剪贴 M（`serverSeq=2`）与分享 S（TTL 60s） | 分享页 200；计数 `4,2,0,2,2`；前置备份 `clipbox-20261118-111544.db` |
| 停服备份 | 11:16:38 | `docker compose stop clipbox`；脚本生成安全备份 | `clipbox-20261118-111638.db`，完整性 `ok` |
| 换库启动 | 11:16:38 | 旧 `clipbox.db`/`-wal`/`-shm` 移入 `rollback-20261118-111638/`；`install -o 10001 -g 10001 -m 600` 恢复前置备份；启动容器 | `/healthz=200`（约 2s）；实库完整性 `ok`；M 行数为 0；计数 `4,1,0,1,1` |
| 同步验证 | 11:17 | D1 全量同步；携带未来游标；访问分享页 | 全量：`items=1, hasMore=false, resync=false, serverMaxSeq=1`；`since=2`：`resyncRequired=true, items=0, nextSince=2`（增量与 `full=true` 均如此）；分享页 404 |
| 恢复后写入 | 11:17 | 新建 N | `serverSeq=2`（= 恢复后最大值 1 + 1）；`since=1` 返回 N 且 `resync=false`；`since=2` 为空；计数 `4,2,0,1,2` |
| 回滚 | 11:23 | 再次停服，将在线库移入 `rollback-20261118-112303-b/`，恢复安全备份并启动 | `/healthz=200`；完整性 `ok`；M 恢复（活跃行数 1）；计数 `4,2,0,2,2` |
| 清理 | 11:23 | D1 删除标记 M | `serverSeq=3`；计数 `4,1,1,2,3`；`/healthz=200` |

客户端重同步行为由共享层测试覆盖：`ClipboxClient/shared/src/databaseTest/kotlin/com/wangxingxing/clipbox/sync/SyncEngineResyncTest.kt` 的 `cursorAheadOfServerMaxSeqTriggersResync`（服务端恢复旧备份、游标来自“未来”时自动重同步）；服务端接口语义与 `docs/api.md` §3.1 一致。

## 5. 验收

任务卡命令的宿主机等价（`<SERVER_IP>` 为部署服务器公网 IP）：

```bash
ssh root@<SERVER_IP> 'ls -l /root/clipbox/data/backups && sqlite3 /root/clipbox/data/clipbox.db "PRAGMA integrity_check;"'
```

实际结果：

- 备份目录 700，5 份备份与 `backup.log` 均为 600；
- `PRAGMA integrity_check` 返回 `ok`；
- 恢复演练后 `/healthz` 返回 200，容器 `healthy`；
- 日志敏感关键字检查（`token|content|PRIVATE`）无命中。

## 6. 风险、偏差与遗留

- 演练设备 `DR-DRILL` 无法通过接口删除，保留在设备表中；不影响同步。
- 演练标记 M 的删除留下 1 条墓碑（`serverSeq=3`）；演练分享 S 已过期，行由分享清理任务按宽限期回收。
- 服务器保留两个 root 700 回滚目录：`rollback-20261118-111638/`（换库前库）与 `rollback-20261118-112303-b/`（回滚前库），确认无需追溯后可手动删除。
- 演练期间 `serverSeq` 短暂回退再前进；期间无真实客户端写入，最终已恢复到演练前状态。若有客户端在线，会按契约触发一次 `resyncRequired`。
- 宿主机新增 `sqlite3` 包（3.45.1）；如不再需要可 `apt-get remove sqlite3`（备份任务依赖它，不建议移除）。
- 备份文件位于宿主机绑定目录，已被 `.gitignore`（`/data/`）与 `.dockerignore`（`data/`）排除，不进入仓库与镜像；本次未重建镜像。
- 未修改任何冻结的需求、接口与架构文档。
