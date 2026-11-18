# 官网任务卡（T-WEB）

M6 目标：交付字匣 Theca 官网 v1.0.0 —— 三语单页站、五端下载矩阵（Windows / Linux 可下载、iOS 说明）、自建服务端说明，由现有 Ktor 服务端同容器托管，并完成远程部署与验收。

前置：M5 已产出 `dist/composeApp-debug.apk`、`dist/Theca-1.0.0.dmg`；release 签名已配置（commit `64a3bea`）。学习版仍为 HTTP + IP 直连（[ADR-0003](../adr/0003-http-ip-learning-deployment.md)），官网托管决策见 [ADR-0004](../adr/0004-web-static-hosting.md)。

需求基线：[web-功能需求.md](../web-功能需求.md)；实现细节：[web-架构.md](../web-架构.md)；视觉基线：`design/brand-spec.md`。

---

## T-WEB-01 文档与治理落位

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | 无 |
| 需求 | WEB-CONTENT-001、WEB-NFR-005 |
| 预估 | 0.5 天 |

**目标**：把官网的需求、架构、任务卡与托管决策写入仓库，并登记到统一派单体系；本卡不写业务代码。

**必读**：`AGENTS.md`、`docs/tasks/README.md`、`docs/web-功能需求.md`、`docs/web-架构.md`、`design/brand-spec.md`。

**涉及路径**：`docs/web-功能需求.md`、`docs/web-架构.md`、`docs/tasks/06-web.md`、`docs/adr/0004-web-static-hosting.md`、`docs/tasks/README.md`、`AGENTS.md`。

**实现要点**

- 需求文档使用 `WEB-<域>-<序号>` 编号与 Given/When/Then 验收；域为 CONTENT / I18N / DL / SELF / HOST / NFR。
- 架构文档覆盖：技术栈、目录结构、tokens、i18n、下载契约、Ktor 静态托管、缓存与 CSP、Docker 三阶段、rsync 部署、风险。
- ADR-0004 记录「官网同容器托管、站点进镜像、下载挂载」的上下文、决策、备选与后果。
- `docs/tasks/README.md` 增加 M6 里程碑、`06-web.md` 索引、依赖图与状态表行（初始「待开始」）。
- `AGENTS.md` §4 增加官网路径与 npm 命令；不改 §3 硬不变量，不改冻结的产品/接口/架构文档。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
ls docs/web-功能需求.md docs/web-架构.md docs/tasks/06-web.md docs/adr/0004-web-static-hosting.md
grep -n "M6" docs/tasks/README.md
grep -n "ClipboxWeb" AGENTS.md
```

预期：文件存在；README 有 M6 与 T-WEB 索引；AGENTS 含 ClipboxWeb 路径与命令。

**DoD**

- [ ] 需求编号可被任务卡引用，验收矩阵覆盖全部域。
- [ ] ADR 格式与 0001–0003 一致，含 Considered Options 与 Consequences。
- [ ] 未修改冻结文档与代码。
- [ ] 一卡一提交：`T-WEB-01: <一句话>`。

**禁止**

- 不要把未确认的决策写进需求。
- 不要改动 `docs/功能需求文档.md`、`docs/api.md` 与两份技术架构文档。

---

## T-WEB-02 工程骨架与设计令牌

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-01 |
| 需求 | WEB-CONTENT-002、WEB-NFR-001/004 |
| 预估 | 1 天 |

**目标**：初始化 `ClipboxWeb/` 工程，落地品牌 tokens 与基础排版，产出可构建的三语空壳页面。

**必读**：`docs/web-架构.md` §3–§6、`design/brand-spec.md` §字体与色彩、`AGENTS.md` §8 文件头要求。

**涉及路径**：`ClipboxWeb/`（`package.json`、`package-lock.json`、`vite.config.ts`、`tsconfig*.json`、`index.html`、`src/main.ts`、`src/App.vue`、`src/styles/`、`src/i18n/index.ts`、三份最小 locale）、`ClipboxWeb/README.md`。

**实现要点**

- Vite + Vue 3 + TS + vue-i18n（Composition 模式）；`npm run build` = `vue-tsc --noEmit && vite build`。
- `tokens.css` 完整照抄 brand-spec 的明暗 tokens，不新增颜色；`base.css` 定义字体栈、间距、发丝线、焦点环、`prefers-reduced-motion`。
- 空壳含 Header / Hero / Footer 三块与三语切换入口；不使用内联 style。
- favicon 使用 `design/brand/app-icon-rounded.svg` 的副本；`public/` 放 192 图标与 robots.txt。
- README 写明 `npm ci / dev / build / preview` 与服务端联调方式。

**验收**

```bash
cd ClipboxWeb
npm ci
npm run build
npm run preview
# 浏览器打开，切换三语不报错，明暗跟随系统
```

预期：构建与类型检查通过；空壳页面明暗两套 tokens 生效；无外部网络请求。

**DoD**

- [ ] `package-lock.json` 已提交，依赖仅限计划清单。
- [ ] 无硬编码可见文案（空壳阶段允许 key 占位，但三份 locale 均存在）。
- [ ] 构建无警告级错误；控制台无报错。
- [ ] 一卡一提交：`T-WEB-02: <一句话>`。

**禁止**

- 不引入路由库、CSS 框架、图标库、Web 字体、分析 SDK。
- 不添加 CI 配置。

---

## T-WEB-03 三语文案与语言切换

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-02 |
| 需求 | WEB-I18N-001…005、WEB-CONTENT-002/004/006 |
| 预估 | 1 天 |

**目标**：补齐三套完整文案与语言解析/持久化，切换时同步 `<html lang>`、title、description。

**必读**：`docs/web-功能需求.md` §5.2、`docs/web-架构.md` §6、`design/README.md`（Hero 文案与产品事实）。

**涉及路径**：`ClipboxWeb/src/i18n/`、`ClipboxWeb/src/components/SiteHeader.vue`、`ClipboxWeb/src/App.vue`。

**实现要点**

- 语言解析：`?lang` → localStorage → `navigator.language`，非法值回退 `zh-CN`；localStorage key 固定 `theca.lang`。
- 文案覆盖：导航、Hero、能力、工作方式、隐私、下载、自建、FAQ、页脚、错误/不可用说明。
- title 与 meta description 进 locale；切换时更新，不刷新页面。
- Hero 中文沿用设计稿「文本中转，像把一张纸条递过去。」；繁中/英文新写，语义一致。
- 390px 下英文与繁中不截断、不横向溢出。

**验收**

```bash
cd ClipboxWeb && npm run build && npm run preview
# 浏览器：?lang=en / ?lang=zh-TW / 默认；刷新保持；检查 <html lang> 与 title
# 截图 390px 三语，对比无溢出
```

预期：三语全部可读；刷新保持上次语言；无直接写死文案。

**DoD**

- [ ] 三份 locale 键集合一致（可用脚本或人工比对）。
- [ ] 语言按钮有 `aria-pressed` 与键盘可达。
- [ ] 一卡一提交：`T-WEB-03: <一句话>`。

**禁止**

- 不用 URL 路径或 hash 承载语言。
- 不引入机器翻译接口或外部语言包。

---

## T-WEB-04 首页主体区块

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-03 |
| 需求 | WEB-CONTENT-001/003/004/006、WEB-NFR-002/003 |
| 预估 | 1 天 |

**目标**：实现 Hero、四项能力、三步工作方式、安全与隐私、FAQ、页脚与截图资产。

**必读**：`docs/web-架构.md` §5、`design/brand-spec.md`、`design/README.md` §屏幕地图。

**涉及路径**：`ClipboxWeb/src/components/`（除下载与自建）、`ClipboxWeb/src/assets/shots/`、`ClipboxWeb/public/`。

**实现要点**

- Hero：标题、副标题、两条 CTA（下载 / 看功能）、等宽规格条、桌面 + 手机截图组合；发丝线连接与 `已送达 · 2 秒前 · 1.2 KiB` 状态是唯一记忆点。
- 能力四项与隐私清单使用发丝线网格，不用阴影卡片；「工作方式」使用真实三步编号。
- 安全区必须包含：无账号、令牌只存哈希、日志脱敏、无第三方 SDK、HTTP 学习版警示、「刻意不做」。
- 截图用 `cwebp` 转多尺寸 WebP，写死宽高，首屏预加载、其余懒加载，`alt` 说明内容。
- 页脚固定版本与未加密警示；无外部链接。

**验收**

```bash
cd ClipboxWeb && npm run build && npm run preview
# 截图：1440 / 768 / 390 × 明暗 × 三语
# 检查无横向溢出、焦点可见、reduced-motion 下无动画
```

预期：所有区块齐全；图片加载不抖动；控制台无报错、无外部请求。

**DoD**

- [ ] 截图证据保存在 `~/Documents/ClipboxWeb/`（不入库）。
- [ ] 页面文案与 `web-功能需求.md` 一致，不夸大能力。
- [ ] 一卡一提交：`T-WEB-04: <一句话>`。

**禁止**

- 不使用生成图、stock 图、装饰性插画、渐变或卡片阴影。
- 不添加滚动入场动画或轮播。

---

## T-WEB-05 下载区块与安装包产物

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-04 |
| 需求 | WEB-DL-001…005/007、WEB-CONTENT-004/005 |
| 预估 | 1 天 |

**目标**：产出 release 签名 APK，生成下载目录与校验文件，实现五端可用性矩阵。

**必读**：`docs/web-架构.md` §7、`docs/tasks/release-artifacts.md`、`AGENTS.md` §3 第 8/19 条。

**涉及路径**：`ClipboxClient/composeApp/build.gradle.kts`（仅在必要时）、`downloads/`（运行期，gitignored）、`ClipboxWeb/src/data/downloads.ts`、`ClipboxWeb/src/components/DownloadSection.vue`、`docs/tasks/web-artifacts.md`。

**实现要点**

- 构建：`./gradlew :composeApp:assembleRelease`；用 SDK 37 `apksigner verify --print-certs` 验证签名，记录 debuggable=false。
- 产物复制并重命名：`Theca-1.0.0-android.apk`、`Theca-1.0.0-macos-arm64.dmg`；生成 `SHA256SUMS.txt`。
- 页面展示版本、大小、SHA-256（等宽、可换行）、平台要求与不可用原因；链接指向 `/downloads/<file>`。
- 本卡只开放 Android 与 macOS；iOS 不提供下载按钮，Windows / Linux 由 T-WEB-10 开放。
- 新建 `docs/tasks/web-artifacts.md` 记录命令、大小、SHA、签名输出与截图路径。

**验收**

```bash
cd ClipboxClient
./gradlew :composeApp:assembleRelease
"$HOME/Library/Android/sdk/build-tools/37.0.0/apksigner" verify --print-certs \
  composeApp/build/outputs/apk/release/composeApp-release.apk
cd ..
mkdir -p downloads
cp ClipboxClient/composeApp/build/outputs/apk/release/composeApp-release.apk downloads/Theca-1.0.0-android.apk
cp dist/Theca-1.0.0.dmg downloads/Theca-1.0.0-macos-arm64.dmg
cd downloads && shasum -a 256 Theca-1.0.0-android.apk Theca-1.0.0-macos-arm64.dmg > SHA256SUMS.txt && cat SHA256SUMS.txt
```

预期：签名验证通过；页面 SHA 与文件一致；下载目录不入库。

**DoD**

- [ ] APK 为 release 构建，非 debug。
- [ ] `downloads.ts` 的数值全部来自实测，无占位。
- [ ] `web-artifacts.md` 含命令与真实输出。
- [ ] 一卡一提交：`T-WEB-05: <一句话>`。

**禁止**

- 不发布未经签名的 APK。
- 不把 APK / DMG / SHA 文件提交进仓库。

---

## T-WEB-06 自建服务端区块

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-05 |
| 需求 | WEB-SELF-001…003、WEB-CONTENT-005/006 |
| 预估 | 0.5 天 |

**目标**：讲清「自己部署服务端」的模型、环境要求、步骤与参考文件，并给出明文警示。

**必读**：`docs/web-功能需求.md` §5.4、`docker-compose.yml`、`.env.example`、`docs/adr/0003-http-ip-learning-deployment.md`。

**涉及路径**：`ClipboxWeb/src/components/SelfHostSection.vue`、`downloads/theca-docker-compose.example.yml`、`downloads/theca.env.example`、locale 文件。

**实现要点**

- 步骤：准备 Docker/Compose → 配置 `.env` → `docker compose up -d --build` → `curl /healthz`。
- 环境要求：2 核 2 GB、仅发布 8080、SQLite 落盘 `./data`。
- 片段与实际 `docker-compose.yml` 一致（含 `read_only`、`tmpfs`、日志轮转、下载挂载）；使用 `<SERVER_IP>` 占位。
- 明确「不含 TLS、无域名、令牌明文传输、仅用于个人学习」。
- 提供示例文件下载；不提供服务端二进制。

**验收**

```bash
cd /Users/wangxingxing/AndroidStudioProjects/MyProjects/Clipbox
ls downloads/theca-docker-compose.example.yml downloads/theca.env.example
grep -rn "117\.72\.121\.6" ClipboxWeb/src || echo "no hardcoded IP"
```

预期：示例文件存在；源码无硬编码 IP；浏览器中片段可读、可复制。

**DoD**

- [ ] 片段与仓库实际 compose 不矛盾。
- [ ] `.env` 示例不含真实令牌。
- [ ] 一卡一提交：`T-WEB-06: <一句话>`。

**禁止**

- 不写死服务器 IP 或示例域名。
- 不提供独立服务端二进制或一键脚本下载。

---

## T-WEB-07 响应式、暗色、无障碍与性能打磨

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-04、T-WEB-06 |
| 需求 | WEB-NFR-001…004、WEB-I18N-005 |
| 预估 | 1 天 |

**目标**：在三种宽度、两套主题、三种语言下完成视觉与可用性打磨，并核对性能预算。

**必读**：`design/brand-spec.md` §色彩/字号/间距、`docs/web-架构.md` §12。

**涉及路径**：`ClipboxWeb/src/styles/`、`ClipboxWeb/src/components/`。

**实现要点**

- 断点：≥1120 双列 Hero，768–1119 收窄，<768 单列；导航折叠不使用第三方组件。
- 焦点环、跳转链接、`aria-pressed`、`prefers-reduced-motion` 全部到位。
- 图片 `srcset/sizes`、懒加载、固定宽高；首屏图预加载。
- 核对 gzip 体积与对比度；清理未使用样式。

**验收**

```bash
cd ClipboxWeb
npm run build
npx vite preview --port 4173
# 截图：1440/768/390 × 明暗 × 三语；键盘 Tab 全链路；系统开启减少动态效果
du -sh dist/assets
```

预期：无横向溢出；焦点可见；预算达标；无控制台错误。

**DoD**

- [ ] 无障碍与响应式截图齐全。
- [ ] 无内联样式（CSP 兼容）。
- [ ] 一卡一提交：`T-WEB-07: <一句话>`。

**禁止**

- 不为对齐引入 UI 框架。
- 不使用外部字体或图标 CDN。

---

## T-WEB-08 Ktor 静态托管与 Docker 集成

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-07 |
| 需求 | WEB-HOST-001…006、WEB-DL-006 |
| 预估 | 1 天 |

**目标**：让 Ktor 在 `/` 提供官网、`/downloads` 提供安装包并支持 Range，同时不改变 API 与分享页行为。

**必读**：`docs/web-架构.md` §8–§9、`docs/服务端技术架构.md` §5/§11、`ClipboxServer/.../Application.kt`、`plugins/Security.kt`。

**涉及路径**：`ClipboxServer/gradle/libs.versions.toml`、`ClipboxServer/build.gradle.kts`、`ClipboxServer/src/main/kotlin/.../Application.kt`、`.../config/AppConfig.kt`、`.../plugins/Security.kt`、`ClipboxServer/src/test/kotlin/`（新增静态站点与头测试、更新 `AppConfigTest`）、`ClipboxServer/Dockerfile`、`docker-compose.yml`、根 `.dockerignore`（新增）、`ClipboxServer/.dockerignore`（删除）、`.gitignore`。

**实现要点**

- 依赖：`ktor-server-static-content`、`ktor-server-partial-content`，版本随 ktor 3.4.3。
- `AppConfig` 增 `WEB_DIR` / `DOWNLOAD_DIR`（默认 `./web` / `./downloads`），缺失目录不报错。
- `install(PartialContent)` 早于路由；`/downloads` 先于 `/` 注册；目录缺失跳过并记录启动日志。
- `Security.kt` 按路径设置缓存与 CSP：`/assets/*` immutable、站点 HTML `no-cache`、`/downloads/*` 1 天、API/分享/健康检查保持 `no-store`；分享页 CSP 不变。
- Docker 三阶段（node → gradle → jre），`COPY --from=web /web/dist /app/web`；compose context 改仓库根并挂载 `./downloads:/app/downloads:ro`。
- 根 `.dockerignore` 排除构建与敏感目录；`.gitignore` 增 `downloads/` 与前端产物。
- 测试覆盖：站点 200、下载 200、Range 206、未知 API 404、分享页头不变、目录缺失可启动。

**验收**

```bash
cd ClipboxServer
./gradlew test
cd ..
WEB_DIR=ClipboxWeb/dist DOWNLOAD_DIR=downloads ./ClipboxServer/gradlew -p ClipboxServer run &
curl -sS localhost:8080/ | head
curl -sSI localhost:8080/assets/ | head
curl -sS -D- -o /dev/null -H 'Range: bytes=0-1023' \
  localhost:8080/downloads/Theca-1.0.0-android.apk | head -20
docker compose build && docker compose up -d
curl -sS localhost:8080/healthz
```

预期：测试全绿；`/` 返回 HTML；Range 返回 206 + `Content-Range`；API 与分享页头不变；容器健康。

**DoD**

- [ ] 新增依赖在同一次提交中说明理由。
- [ ] 镜像不含 `.env`、数据库、APK/DMG。
- [ ] 静态目录缺失时服务端可启动。
- [ ] 一卡一提交：`T-WEB-08: <一句话>`。

**禁止**

- 不引入 Caddy/TLS/域名。
- 不把下载目录打进镜像。
- 不削弱 API/分享页的 `no-store` 与 CSP。

---

## T-WEB-09 远程部署与验收

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-WEB-08 |
| 需求 | WEB-HOST-001…006、WEB-DL-002/003/006/008/009、WEB-NFR-005 |
| 预估 | 0.5 天 |

**目标**：用 rsync 把代码与下载包同步到 `/root/clipbox`，远端重建容器并完成线上验收。

**执行记录**：2026-11-18 首轮部署完成（`d2ad070`），范围仅 APK / DMG。按本卡更新后的四平台验收，MSI / DEB 需在 T-WEB-10 产出后补同步与验证，故状态保持「进行中」。

**必读**：`docs/web-架构.md` §10、`docs/tasks/05-release.md` T-REL-03/04、`scripts/backup.sh`。

**涉及路径**：`scripts/deploy-web.sh`（新增）、`docs/tasks/web-artifacts.md`（追加部署记录）、`docs/tasks/README.md`（状态更新）、服务器 `downloads/`（运行期，不入库）。

**实现要点**

- 脚本参数 `<user@host>`，不含 IP；部署前确认远端 `/root/clipbox`、`.env`、`data/` 与备份存在。
- rsync 保护 `data/`、`.env`、`downloads/`；单独同步 `downloads/`；远端 `docker compose up -d --build`。
- 部署前对远端目录（排除 `data/`）打 tar 包以便回滚。
- 线上验证：`/`、`/healthz`、`/s/<token>`、配对/增量同步、`/downloads/*` 与 Range、SHA、日志 grep。
- 记录部署命令、时间、结果与截图路径；状态表更新为「已完成」。

**验收**

```bash
scripts/deploy-web.sh root@<SERVER_IP>
curl -sS http://<SERVER_IP>:8080/ | head
curl -sS http://<SERVER_IP>:8080/healthz
curl -sS -D- -o /dev/null -H 'Range: bytes=0-1023' \
  http://<SERVER_IP>:8080/downloads/Theca-v1.0.0-android-release.apk | head -20
curl -sS -D- -o /dev/null -H 'Range: bytes=0-1023' \
  http://<SERVER_IP>:8080/downloads/Theca-v1.0.0-macos-arm64-release.dmg | head -20
curl -sS -D- -o /dev/null -H 'Range: bytes=0-1023' \
  http://<SERVER_IP>:8080/downloads/Theca-v1.0.0-windows-amd64-release.msi | head -20
curl -sS -D- -o /dev/null -H 'Range: bytes=0-1023' \
  http://<SERVER_IP>:8080/downloads/Theca-v1.0.0-linux-amd64-release.deb | head -20
curl -sS http://<SERVER_IP>:8080/downloads/SHA256SUMS.txt
ssh root@<SERVER_IP> 'cd /root/clipbox && docker compose logs --tail=100 | grep -Ei "token|content|code" || true'
```

预期：官网可访问；四个下载均返回 206；下载 SHA 与页面一致；分享页与 API 正常；日志无敏感信息。

**DoD**

- [ ] 部署前后数据未丢失（`/healthz`、历史记录可读）。
- [ ] 回滚包存在且路径记录在案。
- [ ] 证据写入 `web-artifacts.md`。
- [ ] 一卡一提交：`T-WEB-09: <一句话>`。

**禁止**

- 不 push 仓库、不打 tag、不改远程防火墙。
- 不跳过备份直接重建容器。
- 不把 `.env`、令牌或日志原文写入仓库。

---

## T-WEB-10 Windows / Linux 下载开放与未签名提示

| 字段 | 值 |
| --- | --- |
| 里程碑 | M6 |
| 前置 | T-REL-12 |
| 需求 | WEB-DL-001/003/004/005/007/008/009、WEB-I18N-001/004/005 |
| 预估 | 1 天 |

**目标**：把 Windows amd64 MSI 与 Linux amd64 DEB 接入官网下载矩阵，填正式 CI 实测值，并用三语披露未签名风险；iOS 继续显示「暂未提供」。

**必读**：`docs/web-功能需求.md` §5.3、[ADR-0005](../adr/0005-v1-multiplatform-release.md)、`docs/tasks/release-artifacts.md`、`docs/tasks/web-artifacts.md`。

**涉及路径**：`ClipboxWeb/src/data/downloads.ts`、`ClipboxWeb/src/components/DownloadSection.vue`、`ClipboxWeb/src/i18n/locales/zh-CN.ts`、`zh-TW.ts`、`en.ts`、`docs/tasks/web-artifacts.md`。

**实现要点**

- `downloads.ts` 使用 tag CI 产物：`Theca-v1.0.0-windows-amd64-release.msi`、`Theca-v1.0.0-linux-amd64-release.deb`；大小与 SHA-256 来自 T-REL-12 台账，不填占位。
- Windows / Linux `available: true`；iOS 保持 `false`，说明「仅模拟器构建」。
- 按钮文案扩为 APK / DMG / MSI / DEB；Windows 说明 SmartScreen「更多信息 → 仍要运行」；Linux 说明 amd64（x86_64）、`dpkg -i` 与无 apt 仓库。
- macOS 继续保留未签名与「右键 → 打开」；下载区与页脚保留 HTTP 明文警示。
- FAQ q5/a5 改为「Windows / Linux 已提供；iOS 仅模拟器构建」；三份 locale 键集合保持一致。
- 390px 下英文与繁中按钮、SHA 文本不截断、不横向溢出。

**验收**

```bash
cd ClipboxWeb
npm run build
grep -nE "windows-amd64|linux-amd64" src/data/downloads.ts
grep -rnE "SmartScreen|dpkg|msiButton|debButton" src/i18n/locales
npm run preview
# 390px × 三语截图；四平台状态与下载链接可点击
```

预期：构建通过；Windows / Linux 显示可下载且数值与台账一致；iOS 只有说明；三语无溢出。

**DoD**

- [ ] 页面、`SHA256SUMS.txt` 与实际文件三者 SHA 一致。
- [ ] 未签名的 Windows / macOS 与无签名的 Linux 均有明确提示。
- [ ] 无硬编码服务器 IP、无外部链接、无新增依赖。
- [ ] 完成后触发 T-WEB-09 补验收：同步 MSI / DEB 并验证四平台下载。
- [ ] 一卡一提交：`T-WEB-10: <一句话>`。

**禁止**

- 不要为 Windows / Linux 生成无效按钮或占位哈希。
- 不要把未签名安装包描述为「已验证安全」。
- 不要改动 T-WEB-05 已记录的 Android / macOS 首轮产物口径；以 tag CI 产物为准更新。
