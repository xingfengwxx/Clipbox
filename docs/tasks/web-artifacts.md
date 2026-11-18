# 官网下载产物记录（T-WEB-05）

- 日期：2026-11-18
- 范围：官网 `/downloads` 目录的 release 签名 APK、macOS DMG 与 `SHA256SUMS.txt`
- 产物目录：仓库根 `downloads/`（`.gitignore` 忽略，不提交）；macOS 包源文件来自 `dist/Theca-1.0.0.dmg`（T-REL-05）
- 截图：`~/Documents/ClipboxWeb/shots/t-web-05-download-1440-light.png`、`t-web-05-download-390-light.png`

## 1. 构建命令与结果

| 命令 | 结果 |
| --- | --- |
| `cd ClipboxClient && ./gradlew :composeApp:assembleRelease` | BUILD SUCCESSFUL in 15s；`composeApp/build/outputs/apk/release/composeApp-release.apk` |
| `apksigner verify --print-certs composeApp-release.apk` | 通过，V2 签名（见 §2） |
| `aapt2 dump badging composeApp-release.apk \| grep application-debuggable` | 无输出（debuggable=false） |
| `aapt2 dump badging` | `package name='com.wangxingxing.clipbox' versionCode='1' versionName='1.0.0'`，`minSdkVersion:'26'`，`targetSdkVersion:'36'` |

## 2. 签名信息（apksigner --print-certs）

```text
V2 Signer: certificate DN: C=86, ST=Hubei, L=Wuhan, CN=WangXingxing
V2 Signer: certificate SHA-256 digest: 2427728020d994ff17228ecae9eb669fce0db9bab1be1d4dee05a5e6c041fc86
V2 Signer: certificate SHA-1 digest: 1dbd0c40a6da5dba335646932d28449719b6c8dd
V2 Signer: certificate MD5 digest: dd18a7d5e68c83203556a850af56de81
```

签名材料为 `ClipboxClient/signing/`（已在版本库中，用户确认保留现状；仅适用于学习版，密钥安全风险记录在计划中）。

## 3. 产物与 SHA-256

| 文件（`downloads/`） | 字节数 | 页面显示 | SHA-256 |
| --- | --- | --- | --- |
| `Theca-v1.0.0-android-release.apk` | 16,030,091 | 15.3 MB | `a8a3324bf8e0aff61d4aef6017ba5ede372c70d856a752868f7cd3a15ddb7f55` |
| `Theca-v1.0.0-macos-arm64-release.dmg` | 83,019,219 | 79.2 MB | `9a840108438b9f19b828f4d5cb38df0d6803780e8dc34710b59db6ea138167d3` |

2026-11-18 起产物统一命名为 `Theca-v<version>-<platform>[-<arch>]-<debug|release>.<ext>`。APK 字节与哈希未变；DMG 换用 T-REL-10 之后的含图标构建，大小与哈希由 `83,276,402 / 75b91ae0…` 更新为本表值。

`SHA256SUMS.txt` 由 `shasum -a 256` 生成，`shasum -a 256 -c SHA256SUMS.txt` 两项均为 `OK`。APK 哈希与首版一致；DMG 已更新为 T-REL-10 之后的含图标构建。

## 4. 页面契约

- `ClipboxWeb/src/data/downloads.ts` 中的版本、字节数与 SHA-256 全部来自本文件实测值；重新打包必须同步更新。
- 下载链接使用 `/downloads/<file>` 相对根路径，由 Ktor 静态托管（T-WEB-08）提供，Range 支持在 T-WEB-08 验收。
- iOS / Windows / Linux 只渲染说明，不渲染下载按钮。

## 5. 自建示例文件（T-WEB-06）

| 文件（`downloads/`） | 来源 | 说明 |
| --- | --- | --- |
| `theca-docker-compose.example.yml` | 与仓库 `docker-compose.yml` 目标形态一致（T-WEB-08 落地） | 含 `read_only`、`tmpfs` 的 `/tmp:exec`、日志轮转与 `./downloads` 只读挂载；Ruby YAML 解析通过 |
| `theca.env.example` | 与仓库 `.env.example` 同内容 | 只有占位令牌与 `<SERVER_IP>`，无真实值 |

`downloads/` 不入库，示例文件随部署由 rsync 上传；站点中的 compose 片段与这两份示例保持同一内容。

## 6. 已知限制

1. **APK 为学习版 release 包**：`isMinifyEnabled=false`，未开启代码压缩；签名密钥在仓库内公开，仅用于学习分发。
2. **DMG 未签名**：macOS 12+ / Apple Silicon，首次打开需右键 → 打开；无公证。
3. **本地预览需手动挂载下载目录**：`npm run preview` 只服务 `dist/`，本卡验证时临时复制到 `dist/downloads/`；正式托管由 Ktor 的 `DOWNLOAD_DIR` 提供。

## 7. 远程部署与验收（T-WEB-09）

- 日期：2026-11-18
- 目标：`root@117.72.121.6:/root/clipbox`（脚本与仓库均不含 IP，运行时传入）
- 命令：`scripts/deploy-web.sh root@117.72.121.6`
- 结果：脚本 exit 0；`healthz ready after 4s`；容器 `clipbox-clipbox-1` 状态 `Up (healthy)`
- 回滚包：`/root/clipbox-rollbacks/clipbox-rollback-20261118-151847.tar.gz`（131M，排除 `data/`）；回滚方式为远端 `tar -xzf <包> -C /root` 覆盖目录后 `docker compose up -d --build`

### 7.1 两次失败与根因

| 次序 | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 第 1 次 | `vue-tsc` 报 `TS2307: Cannot find module '../data/downloads'` | `rsync --exclude='data/'` 无根锚点，把 `ClipboxWeb/src/data/` 一并排除 | 全部顶层排除项加 `/` 根锚定（`/data/`、`/downloads/`、`/dist/` …） |
| 第 2 次 | 同一报错且 `COPY ClipboxWeb/` 层命中旧缓存 | 根 `.dockerignore` 的 `data` 与 `**/data` 把 `ClipboxWeb/src/data` 排除出 Docker 构建上下文 | `.dockerignore` 改为根锚定并删除 `**/data`；用真实构建上下文预检 `downloads.ts` 可进入镜像 |
| 第 3 次 | 构建与容器启动均成功，脚本却以 exit 1 结束 | 重建后立即单次 `curl /healthz`，JVM 尚未可服务（`health: starting`）导致连接重置 | 第 5 步改为最多 30 次 × 2s 健康轮询，失败时输出 `docker compose logs --tail=50` |
| 第 4 次 | 全链路成功 | — | 全部构建层缓存命中，`healthz ready after 4s`，脚本 exit 0 |

### 7.2 线上验收输出（2026-11-18 15:19–15:25）

| 检查 | 结果 |
| --- | --- |
| `GET /` | 200 `text/html`；`Cache-Control: no-cache`；站点 CSP 与 `nosniff` 存在；引用 `/assets/index-Cl78U3Gu.js`、`/assets/index-BEuv2pRX.css` |
| `GET /healthz` | `{"status":"ok","version":"1.0.0",...}` |
| `GET /assets/index-Cl78U3Gu.js` | 200；`Cache-Control: public, max-age=31536000, immutable` |
| `HEAD /api/v1/clips` | 401 + `no-store` |
| `GET /s/invalid-token` | 404；分享页 CSP 与 `no-store` 未被站点策略覆盖 |
| `Range: bytes=0-1023` APK | 206；`Content-Range: bytes 0-1023/16030091`；`Accept-Ranges: bytes` |
| `Range: bytes=0-1023` DMG | 206；`Content-Range: bytes 0-1023/83276402` |
| 未知 API / 未知站点路径 | 404 `{"error":{"code":"NOT_FOUND",...}}` |
| APK 经 HTTP 流式下载后 SHA-256 | `a8a3324b…`，与本地 `SHA256SUMS.txt` 及远端文件一致 |
| DMG 远端文件 SHA-256 | `75b91ae0…`，与本地一致 |
| `GET /api/v1/clips?since=0` | 401（认证链路正常） |
| `POST /api/v1/pair` 无效码 | 400 `INVALID_CODE`（路由与库查询正常） |
| `POST /api/v1/pair` 560 KiB 请求体 | 413 `PAYLOAD_TOO_LARGE` |
| 日志（tail 300） | 无 `Authorization` / `Bearer` / `token=` 值、无 43 字符 base64url 串；唯一 `pairing` 命中为保留任务统计 `pairingCodesDeleted=0` |
| 资源 | 容器 108 MiB / 512 MiB，CPU 0.12%；磁盘可用 22G |
| 数据 | `data/clipbox.db`（73728 B）与 `data/backups/` 备份均保留 |

### 7.3 偏差与说明

- **`.dockerignore` 修复归属**：根因在 T-WEB-08 引入的排除规则，但只有远端真实构建才会暴露，故随本卡提交，并在此登记。
- **分享页验证方式**：为避免向生产库写入测试数据，用无效 token 验证路由与安全头，未新建真实分享记录；有效分享页行为此前已由 T-S 集成测试覆盖。
- **配对/增量同步验证方式**：未新建测试设备（无设备撤销接口），用 401 + `INVALID_CODE` + 413 验证认证、路由与限额链路；数据库与备份完好。
