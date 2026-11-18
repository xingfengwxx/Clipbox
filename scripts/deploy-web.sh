#!/usr/bin/env bash
# 官网与应用部署脚本：把仓库工作区与下载包同步到 <user@host>:/root/clipbox，远端 docker compose 重建容器。
# 用法：scripts/deploy-web.sh <user@host>（脚本与仓库都不含服务器 IP）
# 约束：
#   - 远端 data/、.env、downloads/ 不参与 --delete，部署前另打回滚包（排除 data/）；
#   - 只同步构建所需内容，不传 .git、客户端工程、设计稿、文档与本地构建缓存；
#   - 远端已预置镜像与 Gradle 发行版时，rsync 后重新应用构建补丁（公网到官方源较慢）；
#   - 失败不自动覆盖远端目录，按输出提示用回滚包恢复。

set -euo pipefail

REMOTE="${1:-}"
REMOTE_DIR="${REMOTE_DIR:-/root/clipbox}"
ROLLBACK_DIR="${ROLLBACK_DIR:-/root/clipbox-rollbacks}"

if [ -z "$REMOTE" ]; then
    echo "usage: $0 <user@host>" >&2
    exit 1
fi

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
DOWNLOADS_DIR="$REPO_ROOT/downloads"
STAMP="$(date '+%Y%m%d-%H%M%S')"
ROLLBACK_PACKAGE="$ROLLBACK_DIR/clipbox-rollback-$STAMP.tar.gz"

log() { printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" "$*"; }
fail() { log "ERROR $*" >&2; exit 1; }

[ -d "$DOWNLOADS_DIR" ] || fail "缺少下载目录：$DOWNLOADS_DIR"
# 交付文件名含版本号，统一从版本校验脚本取版本，避免脚本与产物命名再次漂移。
VERSION="$("$REPO_ROOT/scripts/check-release-version.sh" --print-version)" || fail "版本校验失败"
for required in SHA256SUMS.txt "Theca-v${VERSION}-android-release.apk" "Theca-v${VERSION}-macos-arm64-release.dmg"; do
    [ -f "$DOWNLOADS_DIR/$required" ] || fail "缺少下载产物：$required"
done
command -v rsync >/dev/null 2>&1 || fail "未找到 rsync"
command -v ssh >/dev/null 2>&1 || fail "未找到 ssh"

log "1/5 远端前置检查与回滚包"
ssh "$REMOTE" bash -s -- "$REMOTE_DIR" "$ROLLBACK_DIR" "$ROLLBACK_PACKAGE" <<'REMOTE_SCRIPT'
set -euo pipefail
REMOTE_DIR="$1"
ROLLBACK_DIR="$2"
ROLLBACK_PACKAGE="$3"

test -d "$REMOTE_DIR" || { echo "缺少远端目录：$REMOTE_DIR" >&2; exit 1; }
test -f "$REMOTE_DIR/.env" || { echo "缺少远端 .env" >&2; exit 1; }
test -f "$REMOTE_DIR/data/clipbox.db" || { echo "缺少远端数据库" >&2; exit 1; }
ls "$REMOTE_DIR"/data/backups/clipbox-*.db >/dev/null 2>&1 || { echo "缺少远端数据库备份" >&2; exit 1; }

mkdir -p "$ROLLBACK_DIR"
tar -czf "$ROLLBACK_PACKAGE" \
    -C "$(dirname "$REMOTE_DIR")" \
    --exclude="$(basename "$REMOTE_DIR")/data" \
    "$(basename "$REMOTE_DIR")"
echo "ROLLBACK_PACKAGE=$ROLLBACK_PACKAGE"
echo "DISK_FREE=$(df -h / | awk 'NR==2 {print $4}')"
REMOTE_SCRIPT

log "2/5 同步工作区（保护 data/、.env、downloads/）"
# shellcheck disable=SC2034
# 排除项必须带根锚点：不带 / 的 data/ 会连 ClipboxWeb/src/data/ 一起排除，容器内构建因缺模块失败。
RSYNC_EXCLUDES=(
    --exclude='/.git/'
    --exclude='/.idea/'
    --exclude='/.vscode/'
    --exclude='/.qoder/'
    --exclude='/.qoder-office-docx-locks/'
    --exclude='/.codegraph/'
    --exclude='/.DS_Store'
    --exclude='/.env'
    --exclude='/data/'
    --exclude='/downloads/'
    --exclude='/dist/'
    --exclude='/design/'
    --exclude='/docs/'
    --exclude='/ClipboxClient/'
    --exclude='/ClipboxWeb/node_modules/'
    --exclude='/ClipboxWeb/dist/'
    --exclude='/ClipboxServer/build/'
    --exclude='/ClipboxServer/.gradle/'
    --exclude='/ClipboxServer/.kotlin/'
    # 远端预置的 Gradle 发行版 zip 不在仓库中，避免被 --delete 清掉。
    --exclude='/ClipboxServer/gradle/wrapper/gradle-9.3.0-bin.zip'
)
rsync -az --delete "${RSYNC_EXCLUDES[@]}" "$REPO_ROOT/" "$REMOTE:$REMOTE_DIR/"

log "3/5 同步下载包"
rsync -az --delete "$DOWNLOADS_DIR/" "$REMOTE:$REMOTE_DIR/downloads/"

log "4/5 应用远端构建补丁（镜像与本地 Gradle 发行版）"
ssh "$REMOTE" bash -s -- "$REMOTE_DIR" <<'REMOTE_SCRIPT'
set -euo pipefail
cd "$1/ClipboxServer"

# 远端预置了 gradle-9.3.0-bin.zip：容器内直接解压，避免回源 services.gradle.org 超时。
if [ -f gradle/wrapper/gradle-9.3.0-bin.zip ]; then
    if ! grep -q 'file:///src/gradle/wrapper/gradle-9.3.0-bin.zip' gradle/wrapper/gradle-wrapper.properties; then
        sed -i 's|^distributionUrl=.*|distributionUrl=file\:///src/gradle/wrapper/gradle-9.3.0-bin.zip|' \
            gradle/wrapper/gradle-wrapper.properties
    fi
fi
# 远端到 Maven Central / Plugin Portal 较慢，走阿里云镜像；仓库源文件保持原样。
if ! grep -q 'maven.aliyun.com/repository/public' build.gradle.kts; then
    sed -i 's|repositories {|repositories {\n    maven("https://maven.aliyun.com/repository/public")|' build.gradle.kts
fi
if ! grep -q 'pluginManagement' settings.gradle.kts; then
    sed -i '1i pluginManagement {\n    repositories {\n        maven("https://maven.aliyun.com/repository/gradle-plugin")\n        gradlePluginPortal()\n        mavenCentral()\n    }\n}\n' settings.gradle.kts
fi
echo "remote build config ready"
REMOTE_SCRIPT

log "5/5 远端重建容器并自检"
ssh "$REMOTE" bash -s -- "$REMOTE_DIR" <<'REMOTE_SCRIPT' \
    || fail "容器重建或健康检查失败；回滚包：$ROLLBACK_PACKAGE（远端执行 tar -xzf 覆盖 $REMOTE_DIR 后 docker compose up -d --build）"
set -euo pipefail
cd "$1"
docker compose up -d --build
docker compose ps
# JVM 从启动到可服务需要数秒，单次 curl 会把正常启动误判为失败，改为最多轮询 60s。
for attempt in $(seq 1 30); do
    if curl -fsS http://127.0.0.1:8080/healthz >/dev/null 2>&1; then
        echo "healthz ready after $((attempt * 2))s"
        exit 0
    fi
    sleep 2
done
echo "healthz 未在 60s 内就绪" >&2
docker compose logs --tail=50 >&2 || true
exit 1
REMOTE_SCRIPT

log "完成：上线物料位于 $REMOTE:$REMOTE_DIR；回滚包 $ROLLBACK_PACKAGE"
