# Clipbox Server

服务端（Ktor Netty）。当前已实现启动配置校验、SQLite 迁移与仓储、`/healthz`、配对码生成与设备配对。

## 版本基线

- JDK 21
- Gradle 9.3.0（wrapper；本机生成时使用系统 Gradle 9.6.0）
- Kotlin 2.3.21 / Ktor 3.4.3 / Logback 1.5.38 / JUnit Jupiter 5.14.4

## 启动

```bash
cd ClipboxServer
ADMIN_TOKEN=$(printf 'a%.0s' {1..32}) \
PUBLIC_BASE_URL=http://localhost:8080 \
./gradlew run
```

```bash
curl -sS http://127.0.0.1:8080/healthz
# {"status":"ok","version":"1.0.0","time":1730000000000}
```

学习版为 HTTP 直连，不启用 TLS（ADR-0003）；分享链接前缀由 `PUBLIC_BASE_URL` 决定。

## 配置项

| 环境变量 | 默认 | 必填 | 说明 |
| --- | --- | --- | --- |
| `PORT` | `8080` | 否 | 监听端口，1–65535 |
| `DATA_DIR` | `./data` | 否 | 数据目录，启动时自动创建并校验可写。容器部署由 compose 显式注入 `/data`（架构文档 §11.4 的 `/data` 为容器视角，本机默认取 `./data`） |
| `ADMIN_TOKEN` | 无 | 是 | 管理令牌，至少 32 个字符 |
| `PUBLIC_BASE_URL` | 无 | 是 | 分享链接基地址，合法的 `http://` / `https://` 绝对地址，含主机、无尾斜杠 |

任一配置缺失或非法时进程立即退出并打印原因，不打印令牌明文。

## 测试

```bash
./gradlew test
```

测试报告：`build/reports/tests/test/index.html`。

## 健康检查

`GET /healthz` 无需认证，返回 `200`：

```json
{"status": "ok", "version": "1.0.0", "time": 1730000000000}
```

## 配对

启动时若不存在「未使用且未过期」的配对码，会生成一个并打印一次启动日志：

```text
[PAIRING-CODE] 启动自动生成配对码：123456（10 分钟内有效，一次性使用）
```

管理端随时补发（管理令牌错误返回 401）：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/admin/pairing-codes \
  -H "X-Admin-Token: $ADMIN_TOKEN"
# {"code":"654321","expiresAt":1730000600000}
```

客户端用码换取设备令牌，令牌只在该响应出现一次：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/v1/pair \
  -H 'Content-Type: application/json' \
  -d '{"code":"654321","deviceName":"Pixel 8"}'
# {"deviceId":"01J8...","token":"<43 字符>","deviceName":"Pixel 8"}
```

配对码 600 秒有效、一次性；服务端只保存设备令牌的 SHA-256 摘要。
