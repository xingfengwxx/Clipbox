# 字匣 Theca 官网技术架构 v1.0.0

| 字段 | 内容 |
| --- | --- |
| 文档版本 | v1.0.0 |
| 更新日期 | 2026-11-18 |
| 工程路径 | `ClipboxWeb/` |
| 托管方 | `ClipboxServer/`（Ktor + Netty，同容器） |
| 需求基线 | [web-功能需求.md](./web-功能需求.md) |
| 视觉基准 | [design/brand-spec.md](../design/brand-spec.md) |
| 决策记录 | [ADR-0004](./adr/0004-web-static-hosting.md)、[ADR-0003](./adr/0003-http-ip-learning-deployment.md) |

本文档描述官网的工程结构、渲染与多语言策略、下载契约、Ktor 静态托管方式、构建与部署链路。官网不改变产品接口与客户端行为。

---

## 1. 目标与范围

1. 用 Vite + Vue 3 + TypeScript + vue-i18n 产出纯静态单页站，无 SSR、无服务端渲染、无运行时后端依赖。
2. 站点构建产物随服务端镜像发布，由 Ktor 在同一 `8080` 端口提供；下载包通过只读卷挂载，不进镜像。
3. 下载支持 HTTP Range / 断点续传，便于在 3 Mbps 服务器带宽下完成 83 MB DMG 的下载。
4. 站点零第三方请求、零 Cookie、零统计，符合项目隐私约束。

## 2. 约束

- 学习版无域名、无 TLS、无 Caddy，站点与 API 同源同端口（ADR-0003）。
- 服务端全局响应头当前为 `nosniff` + `no-referrer` + `Cache-Control: no-store`（`ClipboxServer/.../plugins/Security.kt`）；官网需要按路径细化缓存，但不得削弱 API 与分享页的 `no-store`。
- 页面不得硬编码服务器 IP、令牌或示例域名；自建说明使用 `<SERVER_IP>`。
- 不引入 CI、全量 UI 自动化、外部字体、CDN、分析 SDK（AGENTS §3.19/§3.20）。
- 站点文案不得承诺产品未实现的能力（端到端加密、后台通知、Windows/Linux 包等）。

## 3. 技术栈与版本

| 层 | 选择 | 版本 | 说明 |
| --- | --- | --- | --- |
| 构建 | Vite | 8.3.8 | `base: "/"`，产物 `dist/` |
| 框架 | Vue | 3.5.45 | Composition API + `<script setup>` |
| 语言 | TypeScript | 随模板锁定 | `vue-tsc --noEmit` 作为构建前置 |
| 多语言 | vue-i18n | 11.4.22 | Composition 模式（`legacy: false`） |
| 样式 | 原生 CSS + CSS 变量 | — | 无 Tailwind / UI 框架 |
| 图片 | 设计交付 PNG → WebP | `cwebp` | 多尺寸 srcset |
| 服务端 | Ktor 静态内容 + Range | 3.4.3 | `ktor-server-static-content`、`ktor-server-partial-content` |

依赖在 `package-lock.json` 中锁定；新增运行时依赖需先确认（AGENTS §3.19）。

## 4. 工程结构

```
ClipboxWeb/
├── index.html                  # lang=zh-CN 初始值，运行时同步
├── package.json                # name=clipbox-web，private
├── vite.config.ts
├── tsconfig.json / tsconfig.node.json
├── public/
│   ├── favicon.svg             # 来自 app-icon-rounded.svg
│   ├── icon-192.png / apple-touch-icon.png
│   └── robots.txt              # Allow: /
├── src/
│   ├── main.ts                 # createApp + i18n 挂载
│   ├── App.vue                 # 单页区块编排
│   ├── styles/tokens.css       # 明暗两套品牌 tokens
│   ├── styles/base.css         # reset、排版、栅格、焦点
│   ├── i18n/index.ts           # 语言解析、切换、meta 同步
│   ├── i18n/locales/           # zh-CN.ts / zh-TW.ts / en.ts
│   ├── data/downloads.ts       # 版本、文件名、大小、SHA、平台状态
│   ├── components/
│   │   ├── BrandMark.vue       # 内联图形 + 文本字标
│   │   ├── SiteHeader.vue      # 导航、语言切换、下载按钮
│   │   ├── HeroSection.vue     # 标题、CTA、规格条、截图组合
│   │   ├── FeatureGrid.vue     # 四项能力，发丝线网格
│   │   ├── HowItWorks.vue      # 三步序列
│   │   ├── PrivacySection.vue  # 安全事实与「刻意不做」
│   │   ├── DownloadSection.vue # 五端矩阵 + SHA
│   │   ├── SelfHostSection.vue # 部署步骤 + compose 片段
│   │   ├── FaqSection.vue
│   │   └── SiteFooter.vue
│   └── assets/shots/*.webp     # 由 design/shots 与 brand/shots 转换
└── README.md
```

## 5. 设计系统落地

- tokens 直接取 `design/brand-spec.md`：`bg/panel/panel2/ink/ink2/ink3/line/accent/on-accent/ok/warn/danger`，通过 `prefers-color-scheme` 切换；不新增颜色、不做主题开关。
- 字体只用系统栈；元数据（版本、大小、SHA、字节数、状态）统一等宽 12px，是品牌签名，不用于装饰性标签。
- 排版：Hero `clamp(40px, 6.5vw, 66px)` / 650 / `-0.035em`；区块标题 31px/650；正文 16px/1.7；行宽 ≤68ch。
- 结构用 1px 发丝线，不用卡片阴影与色块；仅产品截图允许一处设备框投影。
- 编号只用于「工作方式」的真实三步序列。
- 首屏「中转带」是唯一记忆点：桌面窗口 + 手机截图由发丝线与等宽状态连接；其余区域保持安静。
- 截图来自 `brand/shots/desktop-theca-window.png`、`design/shots/screen-s01-history.png`、`design/shots/share-ok-mobile.png`，转 WebP 并生成 1x/2x 宽度，首屏预加载、其余懒加载。

## 6. 多语言

- 语言集合：`zh-CN`（默认）、`zh-TW`、`en`。
- 解析顺序：`?lang=` 合法值 → `localStorage["theca.lang"]` → `navigator.language`（`zh-TW/zh-HK/zh-MO` → `zh-TW`；`en-*` → `en`；其余 → `zh-CN`）。
- 切换副作用：更新 vue-i18n locale、`document.documentElement.lang`、`document.title`、`meta[name=description]`、localStorage；不改 URL、不刷新。
- 文案全部在 locale 文件；`downloads.ts` 只存与语言无关的数值与文件名，说明文本由 locale 组装。
- 繁中直接用「字匣」，英文用「Theca」。

## 7. 内容与下载契约

### 7.1 下载目录（仓库根，gitignored）

| 文件 | 来源 | 说明 |
| --- | --- | --- |
| `Theca-v1.0.0-android-release.apk` | `ClipboxClient/composeApp/build/outputs/apk/release/composeApp-release.apk` | release 签名，debuggable=false |
| `Theca-v1.0.0-macos-arm64-release.dmg` | `ClipboxClient/composeApp/build/compose/binaries/main/dmg/Theca-1.0.0.dmg` | macOS 12+，arm64，未签名 |
| `SHA256SUMS.txt` | `sha256sum` 生成 | 与页面展示一致 |
| `theca-docker-compose.example.yml` | 由根 `docker-compose.yml` 复制 | 自建参考 |
| `theca.env.example` | 由根 `.env.example` 复制 | 自建参考 |

- 站点 URL：`/downloads/<file>`；不提供独立服务端二进制。
- `src/data/downloads.ts` 记录每个文件的大小与 SHA-256，构建前人工核对；不得写占位值。
- 平台状态：Android / macOS 可用；iOS、Windows、Linux 标注原因，不渲染下载按钮。

### 7.2 页面不变量

- 无硬编码 IP：自建片段使用 `<SERVER_IP>`。
- 所有外部链接仅限同源相对路径；不引入第三方资源。
- 页脚固定：品牌、`v1.0.0`、`未加密连接，仅用于学习`。

## 8. 服务端静态托管

### 8.1 配置

`AppConfig` 新增：

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `WEB_DIR` | `./web` | 站点 `dist/` 所在目录 |
| `DOWNLOAD_DIR` | `./downloads` | 下载包挂载目录 |

目录不存在时不报错：跳过注册并写一条启动日志，保证测试夹具与未装站点的部署仍可启动。

### 8.2 依赖与插件

- `io.ktor:ktor-server-static-content`：`staticFiles` 路由。
- `io.ktor:ktor-server-partial-content`：`install(PartialContent)`，为静态文件响应 `Range` / `206` / `Content-Range`。
- 插件安装顺序沿用 `Application.module` 现有管线，`PartialContent` 在路由之前安装。

### 8.3 路由顺序

```kotlin
routing {
    healthRoutes(...)
    pairingRoutes(...)
    clipRoutes(...)
    shareRoutes(...)
    publicShareRoutes(...)
    syncRoutes(...)
    if (Files.isDirectory(config.downloadDir)) staticFiles("/downloads", config.downloadDir.toFile())
    if (Files.isDirectory(config.webDir)) staticFiles("/", config.webDir.toFile()) { default("index.html") }
    notFoundRoute()
}
```

- 下载路由必须先于根站点路由注册。
- API、分享页、健康检查均先于静态路由注册，语义不变。
- 未知路径由静态处理器或 `notFoundRoute` 返回 404；不添加 SPA fallback（站点无客户端路由）。

### 8.4 缓存与安全头（`plugins/Security.kt`）

| 路径 | Cache-Control | 其他 |
| --- | --- | --- |
| `/assets/*` | `public, max-age=31536000, immutable` | 站点 CSP、nosniff、no-referrer |
| 站点 HTML / 其他静态文件 | `no-cache` | 站点 CSP、nosniff、no-referrer |
| `/downloads/*` | `public, max-age=86400` | nosniff、no-referrer |
| `/api/*`、`/s/*`、`/healthz` | `no-store` | 原有头与分享页 CSP 不变 |

站点 CSP：

```
default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:;
font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

前端约束：不使用内联 `<script>` / `<style>` / `:style` 绑定，确保严格 CSP 可用。

### 8.5 测试

新增 Ktor 测试（临时目录）覆盖：

1. `GET /` → 200 + `text/html`。
2. `GET /downloads/<file>` → 200 + 正确字节。
3. `GET /downloads/<file>` 带 `Range: bytes=0-15` → 206 + `Content-Range` + `Accept-Ranges`。
4. 未知 `/api/v1/unknown` → 统一 JSON 404。
5. `/s/<token>` → 分享页 CSP 与 `no-store` 未被站点策略覆盖。
6. 目录缺失时启动成功，`/healthz` 200。

## 9. 构建链

### 9.1 本地

```bash
cd ClipboxWeb
npm ci
npm run dev        # 开发预览
npm run build      # vue-tsc --noEmit && vite build
npm run preview
```

服务端联调：

```bash
cd ClipboxServer
WEB_DIR=../ClipboxWeb/dist DOWNLOAD_DIR=../downloads ./gradlew run
```

### 9.2 Docker

`ClipboxServer/Dockerfile` 变为三阶段：

```dockerfile
FROM node:24-alpine AS web
WORKDIR /web
COPY ClipboxWeb/package.json ClipboxWeb/package-lock.json ./
RUN npm ci
COPY ClipboxWeb/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY ClipboxServer/ ./
RUN ./gradlew :shadowJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
...
COPY --from=build /src/build/libs/clipbox-server.jar app.jar
COPY --from=web /web/dist /app/web
ENV WEB_DIR=/app/web DOWNLOAD_DIR=/app/downloads
```

- 构建上下文改为仓库根：`docker-compose.yml` 使用 `build: { context: ., dockerfile: ClipboxServer/Dockerfile }`。
- 根 `.dockerignore` 排除 `.git`、`ClipboxClient`、`design`、`dist`、`downloads`、`data`、`**/node_modules`、`**/build`、`**/.gradle`。
- 下载目录通过 `./downloads:/app/downloads:ro` 只读挂载，不进镜像。

## 10. 部署与回滚

- 部署脚本：`scripts/deploy-web.sh <user@host>`，不含 IP。
- 步骤：远端数据备份检查 → rsync 工作区（保护 `data/`、`.env`、`downloads/`）→ rsync `downloads/` → `ssh '<host>' 'cd /root/clipbox && docker compose up -d --build'`。
- 回滚：部署前在远端对 `/root/clipbox`（排除 `data/`）打 tar 包；失败时恢复目录并重建容器；数据库使用每日备份。
- 部署后复验：`/`、`/healthz`、`/s/<token>`、配对/增量同步、`/downloads/*` 与 Range、日志无令牌与正文。

## 11. 安全与隐私

- 站点无 Cookie、无 localStorage 之外的状态；仅存语言偏好。
- 无第三方请求、无外部字体、无分析 SDK、无崩溃上报。
- CSP 严格，禁止内联脚本与样式；`frame-ancestors 'none'`。
- 下载文件公开可读；分享页的令牌与访问控制不受影响。
- 日志不记录正文、令牌、配对码、分享 token（沿用服务端约束）。
- 已知风险：release 签名材料位于仓库中（用户决定保留），仅适用于学习版；官网不展示该信息。

## 12. 性能与无障碍

- 预算：首屏 JS <100 KB gzip、CSS <30 KB gzip、首屏图 <300 KB；无 Web 字体。
- 图片：WebP + `srcset`/`sizes` + 固定宽高；首屏 `fetchpriority="high"`，其余 `loading="lazy"`。
- 无障碍：跳转链接、语义化 `<header>/<main>/<section>/<footer>`、可见 `:focus-visible`、语言按钮 `aria-pressed`、`prefers-reduced-motion` 关闭过渡。
- 对比度沿用 tokens（正文 ink/bg ≥4.5:1）。

## 13. 测试与验收

| 层 | 方式 |
| --- | --- |
| 类型 / 构建 | `npm run build`（含 `vue-tsc`） |
| 视觉 | `npm run preview` + 浏览器 1440/768/390 × 明暗 × 三语截图 |
| 服务端 | `./gradlew test`（含静态站点、Range、头策略） |
| 集成 | 本地 `./gradlew run` + curl 四类路径 |
| 容器 | `docker compose build && up` + curl |
| 远程 | `scripts/deploy-web.sh` + curl + 截图 |

不做 Playwright、不做 CI、不做全量 UI 自动化。

## 14. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 远端 2 核 2 GB 构建压力 | 首次构建慢或 OOM | 依赖层缓存、串行重试；必要时扩 swap；不悄悄改为挂载方案 |
| 3 Mbps 带宽 | DMG 下载约 4 分钟 | Range/续传 + SHA 校验提示 |
| 全局 `no-store` 改写 | API/分享页缓存语义被破坏 | 路径白名单 + 测试覆盖三类路径 |
| CSP 误用内联样式 | 页面样式失效 | 组件禁用内联 style，验收检查控制台 |
| 静态目录缺失 | 启动失败 | 跳过注册 + 启动日志 |
| 签名材料入库 | 学习版可被伪造升级 | 用户已接受；记录在案，不扩大影响面 |

## 15. 与现有文档的关系

- 产品行为与验收：`docs/功能需求文档.md`
- 接口契约：`docs/api.md`（官网不新增接口）
- 服务端实现：`docs/服务端技术架构.md`（静态托管增量见本文 §8、ADR-0004）
- 视觉与品牌：`design/brand-spec.md`
- 执行协议：`AGENTS.md`

## 16. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-18 | 首版：三语单页、同容器托管、Range 支持、Docker 三阶段构建与 rsync 部署 |
