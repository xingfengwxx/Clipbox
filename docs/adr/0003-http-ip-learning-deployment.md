# 学习版采用 HTTP + IP 直连部署

**Status**: accepted

## Context

Clipbox v1.0.0 的原始设计以公网自用为前提：必须 HTTPS/WSS、Caddy 自动签发证书、客户端拒绝非 localhost 的 HTTP。项目发起人的目标是学习全栈开发与自用，远程服务器目前只有一个公网 IP，没有域名，也没有安装 Docker。引入域名、DNS、证书与 Caddy 会让部署与排障成本显著高于产品本身的学习价值。

## Decision

v1.0.0 学习版采用 HTTP + IP 直连：

- 服务端只运行一个应用容器，直接发布 `8080:8080`，不引入 Caddy、TLS 与 HSTS。
- 服务端 `PUBLIC_BASE_URL` 使用 `http://<SERVER_IP>:8080`，分享链接同样为 HTTP。
- 客户端允许任意 `http://` 与 `ws://` 地址，在配对页与设置页展示「未加密连接，仅用于学习」警示。
- Android 通过 `networkSecurityConfig` 放行明文流量；iOS 通过 ATS 例外放行 HTTP。
- 应用层安全全部保留：设备令牌与分享令牌只存 SHA-256、限流、HTML 转义、CSP、`nosniff`、`no-referrer`、`Cache-Control: no-store`、日志脱敏、请求体与正文上限。
- 包名统一为 `com.wangxingxing.clipbox`，服务端为 `com.wangxingxing.clipbox.server`，iOS Bundle ID 为 `com.wangxingxing.clipbox`。

## Considered Options

- HTTPS + Caddy + 域名（原设计）：安全与分享体验最好，但需要域名、DNS、80/443 可达与证书维护，超出当前学习目标。
- 自签证书 + 设备安装 CA：保留 TLS，但 Android/iOS 需要额外信任配置，分享页在他人浏览器中会告警，排障成本高。
- Tailscale / 内网穿透：可避开域名，但把网络依赖引入日常使用，且 iOS 分享扩展无法稳定复用本地代理。

## Consequences

- 设备令牌、剪贴正文与分享内容在公网上以明文传输；攻击者处于链路中间位置时可读取与篡改。该风险仅在个人学习与可信网络下接受，不得对外分发此构建作为安全产品。
- 原始文档中的 HTTPS/WSS、Caddy、HSTS、`PUBLIC_BASE_URL` 必须为 HTTPS 等条款改写为「学习版 HTTP + IP」，并通过本节与各文档的偏差说明保持自洽。
- 未来恢复 HTTPS 只需：部署 Caddy 或其它 TLS 终止、把 `PUBLIC_BASE_URL` 改为 `https://`、收紧客户端地址校验；数据模型与同步协议无需改动。
- 分享页的 CSP 与转义仍然是必须项，因为明文传输不改变 XSS 风险。
- 所有日志与任务卡必须保留「不记录正文、令牌、配对码、分享 token」的约束。
