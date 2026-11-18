# 发布与验收任务卡（T-REL）

M5 目标：把已通过本地验收的代码部署到用户的远程服务器，产出 Android release APK、macOS arm64 DMG、Windows x64 MSI 与 Linux x86_64 DEB，完成 AC-01…AC-20 证据矩阵并打 `v1.0.0` tag。桌面安装包均未签名；Windows / Linux 不做安装验收（[ADR-0005](../adr/0005-v1-multiplatform-release.md)）。

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
| 需求 | REQ-PLAT-001/002/005、交付范围 |
| 预估 | 0.5–1 天 |

**目标**：本卡产出首轮 Android debug APK、macOS Desktop 包与 iOS 模拟器构建记录；对外 release APK 与 Windows / Linux 产物由 T-REL-10/11 扩展。

**必读**：`docs/tasks/README.md` §1 交付物；`docs/客户端技术架构.md` §4；[ADR-0005](../adr/0005-v1-multiplatform-release.md)。

**涉及路径**：`dist/`（构建产物，加入 `.gitignore`）、`ClipboxClient/README.md`。

**实现要点**

- Android：`./gradlew :composeApp:assembleDebug`；APK 路径记录；在 AVD 与真机各安装一次，完成配对与发送。
- Desktop：`./gradlew :composeApp:packageDmg`（若任务名不同，使用 `:composeApp:createDistributable` 并记录实际命令）；在 macOS 上启动，确认窗口、剪贴板、分享页。
- iOS：执行 T-IOS-09 的模拟器构建命令，记录成功输出；不导出真机签名包。
- 产物复制到 `dist/` 并记录 SHA-256；`dist/` 不提交。
- 本卡首轮产物使用 debug 构建且不配置签名；对外 release 签名与 Windows / Linux 打包由 T-REL-10/11 接管，并在 README 说明。
- 2026-11-18 范围扩展：T-REL-05 记录保留为首轮证据，不追溯重做；v1.0.0 对外分发以 T-REL-11 的 release 产物为准（ADR-0005）。

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

- 本卡不要引入签名证书或 CI；签名与 release CI 由 T-REL-10/11 接管。
- 不要在本卡伪造 Windows / Linux 产物。

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
| 前置 | T-REL-11 |
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
- tag 标记代码交付点；官网下载 SHA 数据提交与远程部署可在 tag 之后（ADR-0005），以 tag 触发的 CI 产物为唯一哈希口径。
- 打 annotated tag：`git tag -a v1.0.0 -m "Clipbox v1.0.0"`。
- 是否 push 由用户确认；未确认前只保留本地 tag。

**验收**

```bash
git status
git log --oneline -10
git tag -n
```

预期：工作区干净；提交历史一卡一提交；`v1.0.0` tag 指向最终代码提交；官网数据提交可在 tag 之后。

**DoD**

- [ ] 文档状态与实际交付一致。
- [ ] 验收矩阵、安全复查、E2E 记录均已提交。
- [ ] tag 存在且说明清晰。

**禁止**

- 不要在未确认时 `git push --tags`。
- 不要为打 tag 而跳过未完成的验收项。

---

## T-REL-09 发布范围、ADR 与冻结文档同步

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-07 |
| 需求 | REQ-PLAT-005、交付范围 |
| 预估 | 0.5 天 |

**目标**：把 Windows / Linux 并入 v1.0.0、iOS 真机延后、release-only CI 与未签名策略写成 ADR，并同步所有受影响的冻结文档与术语。

**必读**：`AGENTS.md`、[ADR-0005](../adr/0005-v1-multiplatform-release.md)、`docs/tasks/README.md`、`docs/web-功能需求.md`、`docs/tasks/security-review.md`。

**涉及路径**：`docs/adr/0005-v1-multiplatform-release.md`、`AGENTS.md`、`docs/tasks/README.md`、`docs/tasks/05-release.md`、`docs/tasks/04-ios.md`、`docs/tasks/06-web.md`、`docs/web-功能需求.md`、`docs/tasks/security-review.md`、`docs/GLOSSARY.md`。

**实现要点**

- ADR-0005 记录范围扩展、CI 例外、未签名、无安装验收、公开 keystore 接受风险。
- `AGENTS.md` §1、§3 第 20 条、§4、§7、§9 同步；README 交付物、M5/M6 Gate、依赖图、派单顺序与状态表同步。
- `05-release.md` 增加 T-REL-10/11/12 卡；`06-web.md` 增加 T-WEB-10；`04-ios.md` 增加 T-IOS-10。
- `docs/web-功能需求.md` 修订 WEB-DL-004，新增 Windows / Linux 未签名提示；`security-review.md` F4 补充 public 仓库与公开分发事实。
- `docs/GLOSSARY.md` 增加发布与分发术语。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
ls docs/adr/0005-v1-multiplatform-release.md
grep -n "MSI" AGENTS.md docs/tasks/README.md docs/tasks/05-release.md docs/web-功能需求.md
grep -n "release-only" AGENTS.md docs/tasks/README.md
grep -nE "T-REL-(09|10|11|12)" docs/tasks/README.md docs/tasks/05-release.md
grep -n "T-WEB-10" docs/tasks/README.md docs/tasks/06-web.md
grep -n "T-IOS-10" docs/tasks/README.md docs/tasks/04-ios.md
grep -n "发布产物" docs/GLOSSARY.md
git diff --check
```

预期：ADR 与卡片存在；四份文档均出现新平台与 CI 口径；术语表有发布条目；无空白错误。

**DoD**

- [ ] ADR 格式与 0001–0004 一致，含 Considered Options 与 Consequences。
- [ ] 未改任何代码、构建配置或资产。
- [ ] README 依赖图与状态表不再停留在「T-REL-08 全员待开始」。
- [ ] 一卡一提交：`T-REL-09: <一句话>`。

**禁止**

- 不要修改 `docs/api.md` 与两份技术架构文档。
- 不要把未确认的签名、公证或 iOS 真机方案写成既定事实。

---

## T-REL-10 桌面三平台打包配置与图标

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-09 |
| 需求 | REQ-PLAT-004/005 |
| 预估 | 1 天 |

**目标**：让 Compose Desktop 同时注册 DMG / MSI / DEB 打包任务，补齐 Windows、Linux、macOS 图标；本机回归 macOS DMG。

**必读**：[ADR-0005](../adr/0005-v1-multiplatform-release.md)、`docs/客户端技术架构.md` §4、`docs/tasks/release-artifacts.md`。

**涉及路径**：`ClipboxClient/composeApp/build.gradle.kts`、`ClipboxClient/composeApp/packaging/`、`ClipboxClient/README.md`。

**实现要点**

- `targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)`；保留 `modules("jdk.unsupported")`。
- `macOS { bundleID; iconFile }`；`windows { iconFile; menuGroup; perUserInstall=true; upgradeUuid=<固定 GUID> }`；`linux { packageName="theca"; iconFile; debMaintainer; appCategory="utils"; appRelease="1" }`。
- 图标从 `design/brand/exports/` 生成：`Theca.ico`（16–256）、`Theca.png`（512）、`Theca.icns`；生成工具只在本机使用，不作为项目依赖。
- 不签名、不公证；在 README 写明 SmartScreen / Gatekeeper / DEB 无签名的提示。
- 本机只回归 `packageDmg`；MSI/DEB 由 T-REL-11 在对应 runner 上构建。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:tasks --all | grep -E "package(Msi|Deb|Dmg)"
./gradlew :composeApp:packageDmg
ls -l composeApp/packaging/Theca.ico composeApp/packaging/Theca.png composeApp/packaging/Theca.icns
```

预期：三个打包任务均已注册；DMG 可生成并启动；三种图标存在且尺寸正确。

**DoD**

- [ ] `packageDmg` 回归通过，版本仍为 1.0.0。
- [ ] 图标来源与品牌规范一致，无外部素材。
- [ ] README 写明三平台打包命令与未签名提示。
- [ ] 一卡一提交：`T-REL-10: <一句话>`。

**禁止**

- 不要在 macOS 上伪造或提交 MSI / DEB 产物。
- 不要引入签名证书、公证账号或新的打包插件。
- 不要改动同步、UI 与接口代码。

---

## T-REL-11 Release CI 与版本一致性检查

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-10 |
| 需求 | REQ-PLAT-005、交付范围 |
| 预估 | 1 天 |

**目标**：用 release-only GitHub Actions 构建四平台产物，校验版本一致，并上传 GitHub Release；官网与服务器仍人工搬运。

**必读**：[ADR-0005](../adr/0005-v1-multiplatform-release.md)、`AGENTS.md` §3 第 20 条、`docs/tasks/web-artifacts.md`。

**涉及路径**：`.github/workflows/release.yml`（新增）、`scripts/check-release-version.sh`（新增）、`ClipboxClient/README.md`、`README.md`。

**实现要点**

- 触发仅 `push tags: v*` 与 `workflow_dispatch`；`permissions: contents: write`；不配置 PR / 主分支触发。
- Job：`test-server`（ubuntu）、`test-client`（macos-15）、`android`、`linux`、`windows`、`macos`、`release`；构建前跑版本检查。
- Runner 固定 `ubuntu-24.04`、`windows-2022`、`macos-15`；只使用 GitHub 官方 actions。
- Windows 确认 WiX 3.14，缺失时用官方安装方式补齐；Linux 用 `dpkg-deb --info` 校验。
- 产物命名：`Theca-v<version>-<platform>[-<arch>]-release.<ext>`，本版即 `Theca-v1.0.0-{android,macos-arm64,windows-amd64,linux-amd64}-release.{apk,dmg,msi,deb}`；生成 `SHA256SUMS.txt`。
- 不读取 GitHub Secrets；Android 签名直接使用仓库内 `ClipboxClient/signing/`（用户接受风险 F4）。
- `workflow_dispatch` 先做 dry run，不创建 Release；tag 构建才执行 `gh release create`。
- 推送 workflow 与 tag 前必须取得用户明确授权。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
scripts/check-release-version.sh
# 用户授权推送 workflow 后：
gh workflow run release.yml
gh run list --workflow release.yml --limit 1
# dry run 通过、用户授权打 tag 后：
git tag -a v1.0.0 -m "Clipbox v1.0.0"
git push origin v1.0.0
```

预期：版本检查通过；dry run 六个构建 job 全绿；tag run 的 Release 含四个产物与 `SHA256SUMS.txt`。

**DoD**

- [ ] 无 PR / 常驻 CI；无 Secrets；无第三方 action。
- [ ] 四个产物命名、大小、SHA 与 Release 一致。
- [ ] 版本检查覆盖 Android、Desktop、iOS、官网四处。
- [ ] 一卡一提交：`T-REL-11: <一句话>`。

**禁止**

- 不要把服务器 SSH 凭据、`.env` 或部署脚本放进 CI。
- 不要为让 CI 通过而关闭测试或放宽版本校验。
- 不要使用 `--no-verify` 绕过提交检查。

---

## T-REL-12 发布产物台账与已知限制

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5/M6 |
| 前置 | T-REL-08（tag CI 产物已生成） |
| 需求 | 全部 AC、交付范围 |
| 预估 | 0.5 天 |

**目标**：把 tag CI 的四个正式产物、大小、SHA、CI run 与未安装验收限制写入台账，供官网与部署使用。

**必读**：`docs/tasks/release-artifacts.md`、`docs/tasks/acceptance-matrix.md`、[ADR-0005](../adr/0005-v1-multiplatform-release.md)。

**涉及路径**：`docs/tasks/release-artifacts.md`、`docs/tasks/acceptance-matrix.md`。

**实现要点**

- 记录四个产物的文件名、字节数、SHA-256、构建 job 与 CI run 链接。
- Android 记录 `apksigner` 与 `debuggable=false` 证据；macOS / Windows / Linux 记录未签名事实。
- Windows / Linux 明确写「未做安装与运行验收」，列为 v1.0.0 已知限制；不得写成已通过。
- iOS 记录仅模拟器构建，无真机签名与 IPA。
- 官网 `downloads.ts` 的数值以本台账为唯一来源。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
ls -l docs/tasks/release-artifacts.md docs/tasks/acceptance-matrix.md
grep -nE "windows-x64|linux-x86_64|未做安装|SHA-256" docs/tasks/release-artifacts.md docs/tasks/acceptance-matrix.md
```

预期：四平台产物与限制均有记录；无「未验证」但被标为通过的条目。

**DoD**

- [ ] 台账数值来自 tag CI，不是本地临时构建。
- [ ] Windows / Linux 限制明确、可复查。
- [ ] 一卡一提交：`T-REL-12: <一句话>`。

**禁止**

- 不要把本地 debug APK / 旧 DMG 的哈希写进正式台账。
- 不要把未安装验收描述成「已验证安装」。

---

## T-REL-13 产物命名规范化与交付同步

| 字段 | 值 |
| --- | --- |
| 里程碑 | M5 |
| 前置 | T-REL-11 |
| 需求 | REQ-PLAT-005、交付范围 |
| 预估 | 0.25 天 |

**目标**：对外产物统一命名为 `Theca-v<version>-<platform>[-<arch>]-<debug|release>.<ext>`，官网下载数据、部署校验与台账同步；不改页面布局、不新增打包时间、不拆 Android ABI。

**必读**：[ADR-0005](../adr/0005-v1-multiplatform-release.md)、`docs/web-架构.md` §7、`docs/tasks/web-artifacts.md`。

**涉及路径**：`.github/workflows/release.yml`、`scripts/deploy-web.sh`、`ClipboxWeb/src/data/downloads.ts`、`README.md`、`ClipboxClient/README.md`、`docs/` 相关文档、`downloads/`（运行期，不入库）。

**实现要点**

- 四平台 release 产物统一 `v` 前缀、平台小写、架构 `arm64`/`amd64`、末尾 `-release`；Android 为 universal 单包，不加架构段。
- Gradle 内部产物名保持不变，CI staging 与本地交付时改名；本地 debug 交付示例 `Theca-v1.0.0-android-debug.apk`。
- `downloads/` 重命名 APK、替换 T-REL-10 之后的含图标 DMG，重建 `SHA256SUMS.txt`；官网数值以实测为准。
- `scripts/deploy-web.sh` 用 `scripts/check-release-version.sh --print-version` 推导版本，不再硬编码产物名。
- 不写打包时间；不改页面布局、组件与 i18n 文案。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
scripts/check-release-version.sh
ls downloads/Theca-v1.0.0-android-release.apk downloads/Theca-v1.0.0-macos-arm64-release.dmg
(cd downloads && shasum -a 256 -c SHA256SUMS.txt)
grep -n "Theca-v1.0.0" ClipboxWeb/src/data/downloads.ts
ruby -ryaml -e 'YAML.load_file(".github/workflows/release.yml"); puts "yaml ok"'
(cd ClipboxWeb && npm run build)
```

预期：版本校验通过；四平台命名在 CI、官网、部署脚本与文档一致；下载文件、`SHA256SUMS.txt` 与 `downloads.ts` 三方哈希一致。

**DoD**

- [ ] 四平台 CI 命名与官网、部署脚本、文档一致。
- [ ] `downloads/` 与新 DMG 实测值一致；旧名文件已清理。
- [ ] 不 push、不打 tag；远程部署待用户授权。
- [ ] 一卡一提交：`T-REL-13: <一句话>`。

**禁止**

- 不要为命名改动 Gradle 内部产物名或新增打包时间字段。
- 不要拆分 Android ABI，不要改动官网页面布局。
- 不要在没有用户授权时推送、打 tag 或部署远端。
