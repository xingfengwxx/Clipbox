# 发布流水线自动上线官网下载区

**Status**: accepted

**Date**: 2026-11-19

## Context

v1.0.0 的发布产物一直由 `scripts/deploy-web.sh` 从开发机人工搬运到服务器，ADR-0005 因此冻结了「服务器下载目录仍由人工搬运，不把服务器凭据放进 CI」。人工流程带来两个可观测问题：

1. 2026-11-18 的 T-REL-15 重建了 `Theca-v1.0.0-*-release.*` 产物，但服务器仍是旧的 `Theca-1.0.0-*` 文件名与旧哈希，页面链接与实际文件不一致（线上 404）。
2. 页面 `downloads.ts` 的大小与 SHA 需要人工在 tag 后回填，CI 产物与官网数据天然存在漂移窗口，WEB-DL-005「页面、SHA256SUMS.txt、实际文件三者一致」只能靠人工纪律维持。

用户于 2026-11-19 确认：release CI 构建完成后应自动把产物放到官网供下载，并支持自动触发。该决定与 ADR-0005 的搬运条款冲突，故以本 ADR 部分取代。

## Decision

- `.github/workflows/release.yml` 新增 `deploy-site` job：`v*` tag 推送时在测试与四平台构建、GitHub Release 之后自动执行；`workflow_dispatch` 增加布尔输入 `deploy_to_site`（默认 false），用于无 tag 演练。
- CI 只发布下载区，不重建站点容器：上传四平台产物、`SHA256SUMS.txt` 与 `downloads.json` 到服务器下载目录；站点源码仍由 `deploy-web.sh` 人工部署。
- 服务器新增受限部署账号 `deploy`：只能写 `/srv/clipbox/downloads`，无 sudo、无端口转发；下载目录从 `/root/clipbox/downloads` 迁出，`docker-compose.yml` 用 `${DOWNLOAD_HOST_DIR:-./downloads}` 兼容本地开发。
- 凭据只存 GitHub Actions Secrets（`DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_SSH_KEY`、`DEPLOY_KNOWN_HOSTS`），不进入仓库、日志或产物；SSH 固定 known_hosts，禁止 `StrictHostKeyChecking=no`。
- `downloads.json` 由 CI 从实际产物生成（schemaVersion、version、generatedAt、五平台可用性与 file/sizeBytes/sha256），页面运行时读取并以此展示版本、大小与 SHA；内置 `downloads.ts` 数据仅作加载失败回退。
- 上传顺序固定为 产物 → `SHA256SUMS.txt` → `downloads.json`（清单最后作为唯一切换点）；部署或校验失败时 job 变红、线上保留上一版清单，`downloads.json.prev` 用于恢复。
- 下载区唯一写入者是 CI；`deploy-web.sh` 不再同步 `downloads/`。历史版本产物保留（旧直链仍可用），清单与 `SHA256SUMS.txt` 只描述当前 tag 版本。
- 自建示例文件（`theca-docker-compose.example.yml`、`theca.env.example`）不随 CI 同步，变更需手工维护。

## Considered Options

- **保持人工搬运（现状）**：不需要服务器凭据进 CI，但产物、页面数据与线上文件三者持续漂移，且每次发版都需要人工回填哈希与部署。
- **服务器定时拉取 GitHub Release**：CI 不持有服务器凭据，但触发不再是「构建完成后」，需新增常驻定时任务与 GitHub Token 管理，延迟与失败面更大。
- **root 账号 + rrsync 受限密钥**：无需迁移目录，但部署身份仍是 root，爆炸半径大于独立账号；校验只能退化为公网 HEAD。
- **CI 修改 `downloads.ts` 并重建站点容器**：与现有部署模型冲突（远端不是 git 仓库、构建依赖远端补丁），每次发版都要整套重建，慢且易漂移。
- **CI 增加部署 Approvals 环境**：可人工把关，但违背「构建完成后自动上线」的目标；如后续需要再以新 ADR 引入。

## Consequences

- ADR-0005 的「服务器下载目录人工搬运 / 不把服务器凭据放进 CI」条款被本 ADR 取代；四平台产物矩阵、未签名策略与 release-only CI 边界不变。
- 新增 `scripts/generate-downloads-manifest.sh`；`release.yml` 增加部署 job；`deploy-web.sh` 移除下载同步；`Security.kt` 对清单与校验文件返回 `no-store`。
- 服务器目录布局变化需要一次性迁移；旧 `/root/clipbox/downloads` 保留为备份。
- 自动上线依赖 GitHub-hosted runner 到服务器 22 端口的连通性；不通时部署 job 失败但 GitHub Release 仍保留，需人工补部署。
- 部署私钥需要可轮换：撤销服务器 `authorized_keys` 对应条目并更新 Secret 即可，无需回滚产物。
- 部署账号权限最小化：只写下载目录，不能读取数据库、环境变量或触发容器操作。
