# 字匣 Theca 官网功能需求

| 字段 | 内容 |
| --- | --- |
| 文档版本 | v1.0.0 |
| 更新日期 | 2026-11-18 |
| 工程路径 | `ClipboxWeb/` |
| 服务端 | `ClipboxServer/`（同容器托管静态资源） |
| 视觉基准 | `design/brand-spec.md`、`design/README.md`、`design/Clipbox 三端设计.html` |
| 部署基线 | 学习版 HTTP + IP 直连（`http://<SERVER_IP>:8080`），见 [ADR-0003](./adr/0003-http-ip-learning-deployment.md)、[ADR-0004](./adr/0004-web-static-hosting.md) |
| 状态 | 已确认，作为 M6 实现与验收基线 |

本文档定义官网（Theca Web）的内容、交互、多语言、下载与托管验收标准，不定义接口字段与客户端行为。产品行为以 [功能需求文档](./功能需求文档.md) 为准；官网不新增产品功能。

---

## 1. 目标与范围

### 1.1 目标

1. 让第一次访问的访客在 30 秒内理解：字匣是什么、解决什么问题、需要自己部署服务端。
2. 提供唯一可信的安装包下载入口，包含版本、大小与 SHA-256 校验值。
3. 用与三端一致的设计语言（纸面、发丝线、等宽读数、单一信号橙）建立品牌识别。
4. 由现有 Ktor 服务端同容器托管，不引入新服务器、域名、TLS 或第三方静态托管。

### 1.2 范围

- 单页静态站：Hero、核心能力、工作方式、安全与隐私、下载、自建服务端、FAQ、页脚。
- 简中（默认）/ 繁中 / English 三语。
- 下载矩阵：Android（release 签名 APK）、macOS（arm64 DMG）、Windows（x64 MSI）、Linux（x86_64 DEB）、iOS（模拟器构建说明）；桌面安装包均未签名。
- 自建服务端说明与参考文件（compose 片段、`.env` 示例）。
- Ktor 静态托管、缓存策略、CSP、Docker 构建链与远程部署。

### 1.3 非目标

不做账号、评论、博客、文档站、支付、PWA、SSR、搜索引擎优化专项、第三方统计、Cookie、A/B 测试、站内 CI 与全量 UI 自动化；发布流水线独立于站内验收（[ADR-0005](./adr/0005-v1-multiplatform-release.md)）。

---

## 2. 角色与场景

| 角色 | 场景 | 官网需要回答 |
| --- | --- | --- |
| 新访客 | 从分享页或朋友处听说 | 这是什么？安全吗？怎么用？ |
| Android 用户 | 想安装 | 下载哪个包？多大？如何校验？ |
| macOS 用户 | 想安装桌面端 | 是否支持 Apple Silicon？为什么打不开？ |
| iOS 用户 | 找安装包 | 现在有没有包？为什么没有？ |
| Windows / Linux 用户 | 想安装 | 下载哪个包？未签名怎么安装？ |
| 自建者 | 想部署服务端 | 需要什么环境？怎么启动？为什么是 HTTP？ |

---

## 3. 信息架构

单页 `index.html`，锚点导航，顺序固定：

| 区块 | 锚点 | 内容 |
| --- | --- | --- |
| 顶栏 | — | 字标、导航、语言切换、下载主按钮 |
| Hero | `#top` | 一句话定位、两条 CTA、等宽规格条、产品截图组合 |
| 核心能力 | `#features` | 跨设备中转、离线优先、只读分享、本地工具箱 |
| 工作方式 | `#how` | 1 部署服务端 → 2 六位码配对 → 3 发送即达（真实序列，允许编号） |
| 安全与隐私 | `#privacy` | 令牌哈希、日志脱敏、无第三方 SDK、学习版 HTTP 警示、「刻意不做」清单 |
| 下载 | `#download` | 五端可用性矩阵、大小、SHA-256、SHA256SUMS.txt |
| 自建服务端 | `#self-host` | 环境要求、步骤、compose 片段、`.env` 示例、警示 |
| FAQ | `#faq` | 账号、数据去向、HTTP、64 KiB、各端安装包 |
| 页脚 | — | 品牌、版本、未加密警示、语言、版权 |

导航项不超过 5 个；移动端收纳为可键盘操作的菜单，不引入第三方组件库。

---

## 4. 内容规范

1. 对外品牌只用「字匣」（简繁同字）与「Theca」；`Clipbox` 不出现在任何可见文案、title、meta 中。
2. 一句话：离线优先的跨设备文本中转与本地工具箱；Android、iPhone / iPad 与电脑之间传文本，不必发消息给自己，也不必登录网页。
3. 产品事实必须与 `docs/功能需求文档.md` 一致：64 KiB 上限、最近的 200 条 + 全部置顶、分享 1h/24h/7d/阅后即焚、5 个本地工具、无账号、无端到端加密。
4. 诚实披露：v1.0.0 学习版为 HTTP 明文；macOS DMG、Windows MSI 与 Linux DEB 均未签名；iOS 只有模拟器构建。
5. 页面不得硬编码服务器 IP；自建说明统一使用 `<SERVER_IP>` 占位。
6. 截图只能来自 `design/shots/` 与 `design/brand/shots/` 的真实设计产物，不使用生成图、stock 图或人物插画。
7. 文案语气：陈述事实、短句、主动语态；不使用「最强」「永久免费」等无法验证的营销词。

---

## 5. 功能需求

编号规则 `WEB-<域>-<三位序号>`；域取值 `CONTENT`（内容与品牌）、`I18N`（多语言）、`DL`（下载）、`SELF`（自建服务端）、`HOST`（托管与安全）、`NFR`（非功能）。每条均需可复现验收。

### 5.1 内容与品牌

#### WEB-CONTENT-001 首页结构完整

Given 访客打开 `http://<SERVER_IP>:8080/`，When 页面加载完成，Then 依次可见 Hero、核心能力、工作方式、安全与隐私、下载、自建服务端、FAQ、页脚，且导航锚点可跳转。

#### WEB-CONTENT-002 品牌口径

Given 任意语言与宽度，When 检查页面所有可见文本与 `<title>`、meta，Then 只出现「字匣」或「Theca」，不出现 `Clipbox`；字标图形使用 `design/brand/logo-symbol.svg` 的结构，信号橙只用于图形块与主操作。

#### WEB-CONTENT-003 产品截图

Given 首屏加载完成，When 查看 Hero，Then 至少展示一张桌面窗口与一张手机历史截图，均来自设计交付物；图片带 `alt` 与固定宽高，不因加载造成布局抖动。

#### WEB-CONTENT-004 数值单一来源

Given 需要展示版本、大小、SHA-256、TTL、64 KiB 等数值，When 检查代码，Then 数值来自 `src/data/downloads.ts` 或 locale 常量，不在多个组件中重复硬编码。

#### WEB-CONTENT-005 无服务器地址硬编码

Given 全量源码，When 搜索 IPv4 地址，Then 不存在硬编码服务器 IP；自建示例使用 `<SERVER_IP>`。

#### WEB-CONTENT-006 学习版警示

Given 访客浏览下载区与页脚，When 阅读说明，Then 能看到「未加密连接，仅用于学习」的明确提示。

### 5.2 多语言

#### WEB-I18N-001 三语切换

Given 页面已加载，When 点击「简 / 繁 / EN」，Then 可见文案、`<html lang>`、`<title>`、`meta[name=description]` 即时更新，不刷新页面。

#### WEB-I18N-002 语言解析顺序

Given 访问带 `?lang=en` 的地址，When 页面初始化，Then 使用英文；无参数时依次读取 localStorage、`navigator.language`；无法识别时回退简中。

#### WEB-I18N-003 语言持久化

Given 用户切换语言，When 刷新页面，Then 保持上次选择；URL 不新增路径或 hash。

#### WEB-I18N-004 文案集中

Given 任一组件，When 检查模板，Then 用户可见文案均通过 i18n key 获取，组件中不出现直接写死的中文或英文句子。

#### WEB-I18N-005 布局韧性

Given 390px 宽度，When 分别使用简中、繁中、英文，Then 无截断、无横向滚动；按钮与列表使用弹性布局。

### 5.3 下载

#### WEB-DL-001 五端矩阵

Given 访客进入下载区，When 查看列表，Then 看到 Android、iOS、macOS、Windows、Linux 五个平台条目，每条含状态（可下载 / 暂未提供）与一句说明；Windows / Linux 可下载，iOS 说明仅模拟器构建。

#### WEB-DL-002 Android 安装包

Given 平台为 Android，When 点击下载，Then 得到 `/downloads/Theca-v1.0.0-android-release.apk`（release 签名、debuggable=false），页面显示大小与 SHA-256，并注明 Android 8.0+。

#### WEB-DL-003 macOS 安装包

Given 平台为 macOS，When 点击下载，Then 得到 `/downloads/Theca-v1.0.0-macos-arm64-release.dmg`，页面注明 macOS 12+、Apple Silicon、未签名及「右键 → 打开」提示。

#### WEB-DL-004 iOS 不可用说明

Given 平台为 iOS，When 查看条目，Then 显示「暂未提供」与原因「仅模拟器构建」，且不存在下载按钮。

#### WEB-DL-005 校验文件

Given 下载区，When 访客需要校验，Then 可下载 `/downloads/SHA256SUMS.txt`，页面展示的 SHA-256 与该文件、实际文件三者一致。

#### WEB-DL-006 Range 与续传

Given 支持 Range 的客户端，When 请求 `Range: bytes=0-1023`，Then 返回 `206 Partial Content` 与正确的 `Content-Range`，并带 `Accept-Ranges: bytes`。

#### WEB-DL-007 下载区警示

Given 访客准备安装，When 阅读下载区，Then 能看到学习版定位与 HTTP 明文警示。

#### WEB-DL-008 Windows 未签名提示

Given 平台为 Windows，When 查看条目或首次运行 MSI，Then 能看到「未签名，SmartScreen 可能提示未知发布者；选择更多信息 → 仍要运行」的说明，并展示 SHA-256。

#### WEB-DL-009 Linux 安装包与校验

Given 平台为 Linux，When 点击下载，Then 得到 `/downloads/Theca-v1.0.0-linux-amd64-release.deb`；页面注明 amd64（x86_64）、`dpkg -i` 安装、未签名且不提供 apt 仓库，并展示 SHA-256。

### 5.4 自建服务端

#### WEB-SELF-001 板块内容

Given 访客进入自建区，When 阅读，Then 可获得：环境要求（Docker / Compose、2 核 2 GB、仅发布 8080）、四步部署命令、compose 片段、`.env` 关键项说明与示例文件下载。

#### WEB-SELF-002 无独立二进制

Given 自建区，When 查找服务端下载，Then 不存在独立服务端二进制；文案明确说明需使用仓库代码构建。

#### WEB-SELF-003 安全警示

Given 自建区，When 阅读，Then 明确提示不含 TLS、无需域名，令牌与会话以明文经过网络，仅用于个人学习。

### 5.5 托管与安全

#### WEB-HOST-001 路由边界

Given 服务端运行，When 请求 `/`、`/api/v1/clips`、`/s/<token>`、`/healthz`，Then `/` 返回官网 HTML，其余路径行为与 v1.0.0 现有实现完全一致。

#### WEB-HOST-002 公开可读

Given 未携带任何令牌，When 请求站点与下载文件，Then 返回 200，不需要认证；API 仍要求设备令牌。

#### WEB-HOST-003 缓存策略

Given 响应头，When 检查：`/assets/*` 为 `public, max-age=31536000, immutable`；站点 HTML 为 `no-cache`；`/api/*`、`/s/*`、`/healthz` 为 `no-store`。

#### WEB-HOST-004 安全响应头

Given 站点 HTML 响应，When 检查响应头，Then 含严格 CSP（`default-src 'self'`、无 `unsafe-inline`）、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`frame-ancestors 'none'`；分享页 CSP 保持原样。

#### WEB-HOST-005 目录缺失可启动

Given `WEB_DIR` 或 `DOWNLOAD_DIR` 不存在，When 服务端启动，Then 仍正常提供 API 与 `/healthz`，静态路径返回 404，不因静态目录缺失拒绝启动。

#### WEB-HOST-006 无第三方请求

Given 页面加载完成，When 查看网络请求，Then 除本机同源资源外无任何外部请求；无 Cookie、无统计、无第三方字体。

### 5.6 非功能

#### WEB-NFR-001 体积预算

Given 生产构建，When 统计，Then 首屏 JS <100 KB gzip、CSS <30 KB gzip、首屏图片 <300 KB，且不加载 Web 字体。

#### WEB-NFR-002 响应式与对比度

Given 1440 / 768 / 390 三种宽度与明暗两套主题，When 截图检查，Then 无横向溢出，正文对比度 ≥4.5:1，靠 tokens 保证。

#### WEB-NFR-003 无障碍

Given 键盘用户，When 使用 Tab 浏览，Then 可依次到达跳转链接、导航、语言切换、CTA 与下载链接，焦点环可见；`prefers-reduced-motion` 下无动效。

#### WEB-NFR-004 构建可重复

Given 干净环境，When 执行 `npm ci && npm run build`，Then 产出 `dist/` 且类型检查通过；除 npm registry 外不依赖私有服务。

#### WEB-NFR-005 验收方式

Given 任务完成，When 验收，Then 使用任务卡列出的构建命令、Ktor 测试与人工截图；站内验收不依赖 CI 或全量 UI 自动化，release-only 发布流水线不参与站内验收。

---

## 6. 验收矩阵

| 范围 | 命令 / 证据 | 覆盖需求 |
| --- | --- | --- |
| 构建与类型 | `cd ClipboxWeb && npm ci && npm run build` | NFR-004 |
| 视觉三语 | 1440/768/390 × 明暗 × 三语截图 | CONTENT-001/002/003、I18N-001…005、NFR-002/003 |
| 下载矩阵 | 浏览器点击 + `sha256sum` + `SHA256SUMS.txt`（四平台） | DL-001…009 |
| Range | `curl -H 'Range: bytes=0-1023'` 期望 206（四个下载） | DL-006 |
| Windows / Linux 安装包 | 页面状态 + HEAD + `dpkg-deb --info` | DL-001/008/009 |
| 服务端回归 | `./gradlew test`；本地 `run` 后 curl 四类路径 | HOST-001…006 |
| 远程 | `/`、`/healthz`、`/s/<token>`、`/downloads/*`（四个产物）、日志 grep | HOST-001…006、DL-002/003/006/008/009 |
| 自建说明 | 文案与片段目检、`.env` 示例下载 | SELF-001…003 |
| 安全头 | `curl -I` 对比三类路径 | HOST-003/004 |

---

## 7. 排除项

- 账号、评论、订阅、支付、博客、文档站、搜索、PWA、SSR、A/B 测试。
- iOS 真机包、服务端二进制分发。
- TLS、域名、Caddy、HSTS。
- 第三方统计、崩溃上报、Cookie、外部字体与 CDN。
- 站内 CI、PR 流水线、全量 UI 自动化；release-only 发布流水线不属于站内验收。

---

## 8. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-18 | 首版：三语单页、五端下载矩阵、自建服务端说明、Ktor 同容器托管与 Range 支持 |
| 2026-11-18 | 开放 Windows x64 MSI 与 Linux x86_64 DEB 下载；iOS 仅模拟器构建；新增未签名提示；站内验收与 release-only CI 解耦（ADR-0005） |
