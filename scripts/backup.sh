#!/usr/bin/env bash
# Clipbox 服务端 SQLite 备份脚本：由宿主机 cron 每日调用。
# 使用 SQLite 在线备份 API 生成一致性快照（WAL 库不得直接 cp），保留最近 7 份 / 7 天。
# 数据目录默认取脚本目录上级的 data/（compose 中挂载为容器 /data），可用 CLIPBOX_DATA_DIR 覆盖。
# 日志只记录时间、文件、大小与状态，不记录正文或令牌。

set -euo pipefail
umask 077

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${CLIPBOX_DATA_DIR:-$(dirname -- "$SCRIPT_DIR")/data}"
DB_FILE="$DATA_DIR/clipbox.db"
BACKUP_DIR="$DATA_DIR/backups"
LOG_FILE="$BACKUP_DIR/backup.log"

RETENTION_DAYS=7
RETENTION_COUNT=7

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" "$*" >> "$LOG_FILE"
}

fail() {
    log "ERROR $*"
    printf 'backup failed: %s\n' "$*" >&2
    exit 1
}

mkdir -p "$BACKUP_DIR" || {
    printf 'backup failed: cannot create %s\n' "$BACKUP_DIR" >&2
    exit 1
}
chmod 700 "$BACKUP_DIR"

[ -f "$DB_FILE" ] || fail "database not found: $DB_FILE"
command -v sqlite3 >/dev/null 2>&1 || fail "sqlite3 not found"

TARGET="$BACKUP_DIR/clipbox-$(date '+%Y%m%d-%H%M%S').db"
if [ -e "$TARGET" ]; then
    log "SKIP exists=$TARGET"
    exit 0
fi

TMP="$TARGET.tmp.$$"
cleanup() {
    rm -f "$TMP" "$TMP-wal" "$TMP-shm"
}
trap cleanup EXIT

# .timeout 让备份在容器写入繁忙时等待而不是立即失败；.backup 读取一致性快照。
sqlite3 "$DB_FILE" ".timeout 10000" ".backup '$TMP'" || fail "sqlite3 .backup failed"

INTEGRITY="$(sqlite3 "$TMP" 'PRAGMA integrity_check;' 2>&1)" || fail "integrity check failed: $INTEGRITY"
[ "$INTEGRITY" = "ok" ] || fail "integrity check returned: $INTEGRITY"

# 校验时 SQLite 会为 WAL 模式的备份文件生成空的 -wal / -shm，落盘前清掉，避免残留边车文件。
rm -f "$TMP-wal" "$TMP-shm"
chmod 600 "$TMP"
mv -f "$TMP" "$TARGET"
trap - EXIT

# 先按天数清理过期文件，再兜底只保留最新 7 份，同时满足架构「7 天」与 REQ-NFR-013「最近 7 份」。
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'clipbox-*.db' -mtime +"$RETENTION_DAYS" -delete 2>/dev/null || true
if ls -1t "$BACKUP_DIR"/clipbox-*.db >/dev/null 2>&1; then
    ls -1t "$BACKUP_DIR"/clipbox-*.db | tail -n +"$((RETENTION_COUNT + 1))" | while IFS= read -r stale; do
        rm -f "$stale"
    done
fi

SIZE_BYTES="$(wc -c < "$TARGET" | tr -d '[:space:]')"
log "OK backup=$TARGET size=$SIZE_BYTES"
printf '%s\n' "$TARGET"
