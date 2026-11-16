# 发布与验收任务卡（T-REL）

M5 目标：把已通过本地验收的代码部署到用户的远程服务器，产出 Android debug APK 与 macOS Desktop 包，完成 AC-01…AC-20 证据矩阵并打 `v1.0.0` tag。

前置：T-S-11、T-UI-20、T-IOS-09 全部完成。远程服务器只有公网 IP，无域名；部署仅 server 容器，HTTP + `8080`（[ADR-0003](../adr/0003-http-ip-learning-deployment.md)）。

---

## T-REL-01 远程服务器准备（用户动作卡）

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | 无（可与 M3/M4 交错） |
| 需求 | REQ-PLAT-006 |
| 预估 | 0.5 天 |

**目标**：服务器具备 Docker / Compose，`8080` 可从客户端网络访问。

**必读**：`docs/服务端技术架构.md` §11；`docs/adr/0003-http-ip-learning-deployment.md`。

**涉及路径**：远程服务器；不改仓库代码。

**用户执行**

```bash
# 以 root 或 sudo 用户登录服务器后（命令按发行版调整）
docker --version
docker compose version

# 若未安装 Docker，按官方文档安装 Docker Engine + Compose Plugin
# 例如 Debian / Ubuntu：
# curl -fsSL https://get.docker.com | sh
# systemctl enable --now docker

# 防火墙放行 8080（示例：ufw）
sudo ufw allow 8080/tcp
sudo ufw status

# 记录公网 IP
curl -sS https://api.ipify.org; echo
```

**Agent 复核**

```bash
nc -vz <SERVER_IP> 8080   # 部署前应失败；部署后应成功
```

预期：`docker` 与 `docker compose` 均有输出；`8080` 已放行；公网 IP 记录到 `docs/tasks/README.md` 或部署记录中（不要提交到公开仓库）。

**DoD**

- [ ] 服务器可拉取镜像 / 构建镜像；磁盘与内存满足 2 核 2GB 基线。
- [ ] `8080` 放行，其他业务端口不额外暴露。
- [ ] 部署所需的 SSH 访问方式由用户确认。

**禁止**

- 不要安装 Caddy、Nginx 或申请证书。
- 不要把服务器密码、私钥写入仓库。

---

## T-REL-02 Dockerfile 与 Compose 落地

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-01、T-S-11 |
| 需求 | REQ-PLAT-006/007 |
| 预估 | 0.5–1 天 |

**目标**：仓库内提供可构建的 `ClipboxServer/Dockerfile`、根 `docker-compose.yml` 与 `.env.example`。

**必读**：`docs/服务端技术架构.md` §11.1–§11.4。

**涉及路径**：`ClipboxServer/Dockerfile`、`ClipboxServer/.dockerignore`、`docker-compose.yml`、`.env.example`、`.gitignore`。

**实现要点**

- Dockerfile：多阶段构建（Gradle 构建 → JRE 运行）；非 root 用户；`JAVA_OPTS=-Xmx256m`；`EXPOSE 8080`；`HEALTHCHECK` 调 `/healthz`；运行镜像含 `curl` 或轻量检查工具。
- `docker-compose.yml`：仅 `clipbox` 服务；`ports: ["8080:8080"]`；`./data:/data`；`read_only: true`；`tmpfs: /tmp`；`mem_limit: 512m`；`restart: unless-stopped`；环境变量从 `.env` 读取。
- `.env.example`：`ADMIN_TOKEN`（≥32 字符占位）、`PUBLIC_BASE_URL=http://<SERVER_IP>:8080`；不含真实令牌。
- `.gitignore` 忽略 `.env`、`data/`、构建产物。
- 本地无 Docker 时，至少执行 Gradle 构建验证 JAR 可产出；Docker 构建在 T-REL-03 远程验证。

**验收**

```bash
cd ClipboxServer
./gradlew clean build
docker compose -f ../docker-compose.yml config   # 若本机已安装 Docker
```

预期：JAR 构建成功；compose 配置可解析且仅一个服务、发布 8080。

**DoD**

- [ ] 镜像内无 `.env`、数据库、令牌或构建缓存。
- [ ] 容器以非 root 运行。
- [ ] 健康检查路径与端口正确。

**禁止**

- 不要加入 Caddy / TLS / 域名配置。
- 不要把 `ADMIN_TOKEN` 写入 Dockerfile 或 compose 明文。

---

## T-REL-03 远程部署与连通性验证

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-02 |
| 需求 | REQ-PLAT-006、AC-19 相关项 |
| 预估 | 0.5–1 天 |

**目标**：服务端在远程服务器运行，HTTP 直连可用，配对与同步走公网。

**必读**：`docs/服务端技术架构.md` §11.2–§11.5、§12；`docs/api.md` §2、§6。

**涉及路径**：远程部署目录；仓库内仅修复部署脚本。

**实现要点**

- 将仓库同步到服务器（git clone 或 rsync，任选；不提交服务器信息）。
- 创建 `.env`：`ADMIN_TOKEN` 用 `openssl rand -base64 48` 生成；`PUBLIC_BASE_URL=http://<SERVER_IP>:8080`。
- `docker compose up -d --build`；查看 `docker compose ps`、`docker compose logs --tail=100`。
- 冒烟：`curl /healthz`；管理接口生成配对码；`curl /api/v1/pair`；创建一条记录；增量拉取；分享创建并在浏览器打开。
- 检查日志不包含正文、令牌、配对码、分享 token；容器内存 <512MB。
- 检查端口：外部仅 `8080` 可访问。

**验收**

```bash
curl -sS http://<SERVER_IP>:8080/healthz
curl -sS -X POST http://<SERVER_IP>:8080/api/v1/admin/pairing-codes \
  -H "X-Admin-Token: $ADMIN_TOKEN"
curl -sS http://<SERVER_IP>:8080/s/<token>
docker stats --no-stream
docker compose logs --tail=100 | grep -Ei 'token|content|code' || true
```

预期：`/healthz` 200；配对码可生成；分享页可打开；日志 grep 无敏感信息；内存占用符合预算。

**DoD**

- [ ] 公网 IP + 8080 可完成配对与同步。
- [ ] 重启容器后数据仍在（`./data` 落盘）。
- [ ] 服务器时间正确（TLS 不涉及，但时间影响排序与令牌过期）。

**禁止**

- 不要在公网暴露管理接口给未知来源；必要时用防火墙限制。
- 不要把 `.env` 或日志原文贴进公开 PR / 文档。

---

## T-REL-04 备份与恢复演练

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-03 |
| 需求 | REQ-NFR-013、REQ-PLAT-006 |
| 预估 | 0.5 天 |

**目标**：每日备份可自动运行，恢复流程经过一次真实验证。

**必读**：`docs/服务端技术架构.md` §11.5。

**涉及路径**：服务器 cron；仓库内 `scripts/backup.sh`（新增，幂等）。

**实现要点**

- `scripts/backup.sh`：`sqlite3 /data/clipbox.db ".backup /data/backups/clipbox-<timestamp>.db"`；权限 600；保留 7 天；失败非零退出。
- 宿主机 cron 每日执行；记录日志到 `/data/backups/backup.log`（不含正文）。
- 恢复演练：停止容器 → 备份当前库 → 用某份备份替换 → 启动 → `/healthz` 通过 → 客户端增量同步/重同步成功。
- 恢复后 `serverSeq` 继续单调；旧游标来自未来应触发 `resyncRequired`（客户端行为已验证）。
- 将演练步骤写入 `docs/tasks/backup-restore.md`。

**验收**

```bash
ssh <server> 'ls -l /data/backups && sqlite3 /data/clipbox.db "PRAGMA integrity_check;"'
```

预期：备份文件存在且权限 600；完整性检查 `ok`；恢复演练后数据可读、服务可用。

**DoD**

- [ ] 备份文件不进入镜像或仓库。
- [ ] 保留策略生效（最多 7 天）。
- [ ] 演练结果有记录。

**禁止**

- 不要用 `cp` 复制正在写入的 SQLite 文件作为备份。
- 不要跳过恢复演练只验证备份生成。

---

## T-REL-05 Android APK 与 macOS Desktop 打包

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-UI-20、T-IOS-09 |
| 需求 | REQ-PLAT-001/002、交付范围 |
| 预估 | 0.5–1 天 |

**目标**：产出可安装的 Android debug APK、macOS Desktop 包与 iOS 模拟器构建记录。

**必读**：`docs/tasks/README.md` §1 交付物；`docs/客户端技术架构.md` §4。

**涉及路径**：`dist/`（构建产物，加入 `.gitignore`）、`ClipboxClient/README.md`。

**实现要点**

- Android：`./gradlew :composeApp:assembleDebug`；APK 路径记录；在 AVD 与真机各安装一次，完成配对与发送。
- Desktop：`./gradlew :composeApp:packageDmg`（若任务名不同，使用 `:composeApp:createDistributable` 并记录实际命令）；在 macOS 上启动，确认窗口、剪贴板、分享页。
- iOS：执行 T-IOS-09 的模拟器构建命令，记录成功输出；不导出真机签名包。
- 产物复制到 `dist/` 并记录 SHA-256；`dist/` 不提交。
- 不配置 release 签名、不混淆、不缩小；学习版用 debug 构建即可，但在 README 说明。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:packageDmg
shasum -a 256 composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

预期：APK 与 DMG 生成；APK 可安装并启动；DMG 可挂载运行。

**DoD**

- [ ] 产物版本号为 1.0.0。
- [ ] 构建命令写入 README。
- [ ] 产物哈希记录在验收文档。

**禁止**

- 不要为打包引入签名证书或 CI。
- 不要承诺 Windows / Linux 包。

---

## T-REL-06 AC-01…AC-20 验收矩阵

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-03、T-REL-04、T-REL-05 |
| 需求 | 全部 AC |
| 预估 | 1 天 |

**目标**：逐条验证功能需求文档的 AC-01…AC-20，每条有证据与结论。

**必读**：`docs/功能需求文档.md` §5 验收场景；`docs/tasks/README.md` M1–M4 Gate。

**涉及路径**：新增 `docs/tasks/acceptance-matrix.md`（只写证据与结论，不改需求文档）。

**实现要点**

- 从需求文档原样抄录 AC 编号、场景与判定标准；不得改写验收标准。
- 每条记录：验证环境（远程 / 本地、设备型号）、步骤、证据（截图路径 / curl 输出 / 测试用例名）、结论（通过 / 失败 / 已知限制）。
- 覆盖三端：Android、Desktop、iOS 模拟器；分享页用桌面与移动浏览器各一次。
- 失败项必须转 bug 卡并修复，或在文档中明确列为 v1.0.0 已知限制并由用户确认。
- 汇总每条对应的自动化测试用例，便于回归。

**验收**

```bash
cd ClipboxServer && ./gradlew test
cd ClipboxClient && ./gradlew :shared:allTests
```

预期：`acceptance-matrix.md` 20 条全部有结论；无「未验证」。

**DoD**

- [ ] 证据可复查（路径存在、命令可重跑）。
- [ ] 失败项有闭环。
- [ ] 不修改需求文档来降低标准。

**禁止**

- 不要用「手工测试过」代替证据。
- 不要跳过 iOS 或分享页场景。

---

## T-REL-07 安全与隐私复查

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-06 |
| 需求 | REQ-NFR-001…007、REQ-SET-011 |
| 预估 | 0.5–1 天 |

**目标**：按学习版安全边界做一次完整复查，确认没有超出 ADR-0003 的隐性风险。

**必读**：`docs/adr/0003-http-ip-learning-deployment.md`；`docs/功能需求文档.md` NFR 域；`docs/README.md` 安全与隐私。

**涉及路径**：产出 `docs/tasks/security-review.md`；必要时修复代码。

**实现要点**

- 令牌：服务端只存 SHA-256；客户端只存 DataStore；URL / 日志 / 分享链接无令牌。
- 日志：release 默认零应用日志；诊断开启才含 body/token；服务端日志无正文、令牌、配对码、分享 token；grep 验证。
- 分享页：XSS 载荷转义；CSP / nosniff / no-referrer / no-store 生效；无外部资源。
- 限流：配对、管理、设备、分享页、WS 生效；429 带 `Retry-After`。
- 传输：确认客户端对 `http://` 有警示；文档与「关于」页披露明文风险；未误加 HSTS / 强制 HTTPS。
- 应用权限：Android 无多余权限；iOS 无后台模式、无推送、无通知。
- 仓库：无 `.env`、令牌、私钥、数据库、备份；`.gitignore` 覆盖产物。
- 依赖：无第三方日志 / 崩溃 / 分析 SDK。
- 结论：列出「已接受风险」和「必须修复项」；修复项完成后才能进入 T-REL-08。

**验收**

```bash
git grep -nE 'ADMIN_TOKEN=[^<]|Bearer [A-Za-z0-9_-]{20,}|BEGIN (RSA|OPENSSH) PRIVATE KEY' -- . ':!docs/tasks/security-review.md' || true
cd ClipboxServer && ./gradlew test
cd ClipboxClient && ./gradlew :shared:allTests
```

预期：无真实密钥；测试全绿；复查表全部有结论。

**DoD**

- [ ] 每个 NFR 条目有「通过 / 接受风险 / 修复」结论。
- [ ] 接受风险项与 ADR-0003 一致。
- [ ] 修复项已闭环。

**禁止**

- 不要以「学习项目」为由跳过 XSS、令牌哈希与日志检查。
- 不要在文档中粘贴真实令牌或公网 IP 以外的敏感信息。

---

## T-REL-08 文档收尾与 v1.0.0 标签

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-07 |
| 需求 | 全部 |
| 预估 | 0.5 天 |

**目标**：仓库状态与文档一致，打 `v1.0.0` tag，形成可回溯的交付点。

**必读**：`docs/README.md`；`docs/tasks/README.md`；`docs/Clipbox 项目交接文档.md` 变更记录。

**涉及路径**：`README.md`、`docs/README.md`、`docs/tasks/README.md`、`docs/Clipbox 项目交接文档.md`。

**实现要点**

- 更新项目状态：从「代码工程待实现」改为「v1.0.0 已交付」，列出构建与部署方式。
- `docs/tasks/README.md` 状态表全部标为已完成；记录最终提交。
- 交接文档追加变更记录：HTTP + IP、包名、范围、交付物。
- 确认工作区干净；`git status` 无未跟踪的敏感文件。
- 打 annotated tag：`git tag -a v1.0.0 -m "Clipbox v1.0.0"`。
- 是否 push 由用户确认；未确认前只保留本地 tag。

**验收**

```bash
git status
git log --oneline -10
git tag -n
```

预期：工作区干净；提交历史一卡一提交；`v1.0.0` tag 指向最终提交。

**DoD**

- [ ] 文档状态与实际交付一致。
- [ ] 验收矩阵、安全复查、E2E 记录均已提交。
- [ ] tag 存在且说明清晰。

**禁止**

- 不要在未确认时 `git push --tags`。
- 不要为打 tag 而跳过未完成的验收项。
