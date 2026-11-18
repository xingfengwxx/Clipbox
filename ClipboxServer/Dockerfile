# 站点阶段：用同一份 lockfile 在容器内构建官网，产物与本地 npm run build 一致。
FROM node:24-alpine AS web
WORKDIR /web
COPY ClipboxWeb/package.json ClipboxWeb/package-lock.json ./
RUN npm ci
COPY ClipboxWeb/ ./
RUN npm run build

# 构建阶段：用 Gradle Wrapper 产出单文件可执行 JAR（Shadow :shadowJar）。
# 固定 jammy 后缀避免裸 tag 随上游发行版切换而漂移。
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY ClipboxServer/ ./
RUN ./gradlew :shadowJar --no-daemon

# 运行阶段只保留 JRE、JAR 与站点静态文件；源码、Gradle 缓存、.env 与数据库被根 .dockerignore 挡在上下文外。
FROM eclipse-temurin:21-jre-jammy

# 基础 JRE 不预装 curl，健康检查需要；安装后清理 apt 缓存，避免落入镜像。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 clipbox \
    && useradd --system --uid 10001 --gid clipbox clipbox \
    && mkdir -p /data && chown clipbox:clipbox /data

WORKDIR /app
COPY --from=build /src/build/libs/clipbox-server.jar app.jar
COPY --from=web /web/dist /app/web

USER clipbox
ENV JAVA_OPTS="-Xmx256m"
# 站点进镜像；下载包由 compose 以只读卷挂载，不打进镜像。
ENV WEB_DIR=/app/web DOWNLOAD_DIR=/app/downloads
EXPOSE 8080

# 根文件系统只读时，JVM hsperfdata 与 SQLite 临时文件写 /tmp（compose 的 tmpfs）。
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1

# exec 让 java 成为 PID 1，docker stop 的 SIGTERM 能触发 Ktor 优雅关闭。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
