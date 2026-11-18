# ClipboxWeb · 字匣 Theca 官网

字匣（Theca）的单页静态官网：产品简介、功能介绍、下载矩阵与自建服务端说明。三语（简中 / 繁中 / English），明暗跟随系统。构建产物由 `ClipboxServer` 同一容器托管（[ADR-0004](../docs/adr/0004-web-static-hosting.md)）。

## 命令

```bash
npm ci        # 安装锁定依赖
npm run dev   # 本地开发 http://localhost:5173
npm run build # vue-tsc 类型检查 + vite build，产物在 dist/
npm run preview # 预览构建产物
```

依赖锁定：Vue 3.5.45、vue-i18n 11.4.22、Vite 8.3.8、TypeScript 5.9.3、vue-tsc 3.3.14。不引入路由库、CSS 框架、图标库、Web 字体、分析 SDK。

## 结构

- `src/styles/tokens.css`：品牌色板与间距，数值与 `design/brand-spec.md` 一致。
- `src/styles/base.css`：排版、发丝线、焦点环、`prefers-reduced-motion`。
- `src/i18n/`：`?lang` → localStorage → 浏览器语言的解析顺序；三份 locale 键集合必须一致。
- `src/App.vue`：页面骨架；后续区块组件在 `src/components/`。

## 与 Ktor 联调

网站由服务端静态托管，下载文件位于仓库根 `downloads/`（不入库）：

```bash
cd ../ClipboxServer
WEB_DIR=../ClipboxWeb/dist DOWNLOAD_DIR=../downloads ./gradlew run
# http://localhost:8080/
```

## 约束

- 不使用内联 `<script>` / `<style>` / `:style`，保证严格 CSP 可用。
- 不硬编码服务器 IP，统一使用 `<SERVER_IP>` 占位。
- 不引入外部 CDN、字体或统计脚本；页面零外部网络请求。
