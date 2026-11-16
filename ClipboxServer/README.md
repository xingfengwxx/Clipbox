# Clipbox Server

服务端（Ktor Netty）。当前为 T-S-01 骨架：启动配置校验 + `/healthz`，不含配对、数据库与业务路由。

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
