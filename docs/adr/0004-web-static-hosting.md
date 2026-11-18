# 官网由同一 Ktor 容器托管

**Status**: accepted

**Date**: 2026-11-18

## Context

字匣 Theca v1.0.0 需要一个官网：介绍产品、说明自建服务端模型、提供 Android / macOS 安装包下载。现有部署只有一个应用容器（Ktor + SQLite），直接发布 `8080`，没有域名、TLS 或反向代理（ADR-0003）。另起一个静态托管（对象存储、GitHub Pages、Nginx 容器）会引入第二个部署目标、第二套发布流程与额外的网络依赖；而后端静态托管本身没有技术障碍。

## Decision

官网由现有 Ktor 服务端同容器托管：

- 站点用 Vite + Vue 3 + TypeScript + vue-i18n 构建为纯静态文件，Docker 的 node 阶段产出 `dist/`，复制进运行镜像 `/app/web`。
- Ktor 通过 `staticFiles("/", WEB_DIR)` 在 `/` 提供站点，`WEB_DIR` 默认 `./web`、容器内为 `/app/web`；目录不存在时跳过注册，不阻塞启动。
- 安装包放在宿主机 `./downloads`，以只读卷挂载到 `/app/downloads`，由 `staticFiles("/downloads", ...)` 提供；不进入镜像。
- 安装 `ktor-server-partial-content`，使下载支持 HTTP Range / 断点续传。
- 安全头按路径细化：站点 `/assets/*` 长缓存、HTML `no-cache`、下载 1 天；API、分享页与 `/healthz` 保持 `no-store`；站点加严格 CSP。
- Docker build context 从 `ClipboxServer/` 改为仓库根，`docker-compose.yml` 显式指定 `dockerfile: ClipboxServer/Dockerfile`。

## Considered Options

- **站点与下载都挂载宿主机目录**：镜像更小、站点更新无需重建镜像；但远端部署要额外维护静态文件同步，且站点版本与镜像版本容易漂移。
- **站点与下载全打进镜像**：部署最简单；但约 100 MB 的安装包会让镜像臃肿，每次更新安装包都要重建镜像。
- **独立静态托管（对象存储 / GitHub Pages / 独立 Nginx）**：CDN 与缓存更好；但需要第二个部署目标、域名或额外账号，违背学习版「一个容器、一个端口」的基线。

## Consequences

- 官网与 API、分享页共用一个来源与端口，跨域、Cookie、CSP 与缓存策略只需在一个服务端实现。
- 站点更新随镜像重建；安装包更新只需替换宿主机 `downloads/` 并重新挂载，无需重建镜像。
- 服务器带宽直接决定下载体验：3 Mbps 下 83 MB DMG 约需 4 分钟，Range 支持中断续传；仍建议下载后校验 SHA-256。
- 新增 `ktor-server-static-content` 与 `ktor-server-partial-content` 两个依赖，需随 Ktor 版本同步升级。
- 远端镜像构建需要 Node 运行时镜像与 npm 依赖；2 核 2 GB 服务器首次构建较慢，但不改变运行时体积与端口暴露。
- 单容器仍是单点：容器故障时官网、API 与分享页同时不可用；学习版接受该风险。
