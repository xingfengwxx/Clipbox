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
| `Theca-1.0.0-android.apk` | 16,030,091 | 15.3 MB | `a8a3324bf8e0aff61d4aef6017ba5ede372c70d856a752868f7cd3a15ddb7f55` |
| `Theca-1.0.0-macos-arm64.dmg` | 83,276,402 | 79.4 MB | `75b91ae0d07c9fab4e26a0cec338cbc36879cbb19233aaa6c0f3943bff90fd47` |

`SHA256SUMS.txt` 由 `shasum -a 256` 生成，`shasum -a 256 -c SHA256SUMS.txt` 两项均为 `OK`。DMG 哈希与 T-REL-05 记录一致，说明产物未变更。

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
