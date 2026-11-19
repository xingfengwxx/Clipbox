#!/usr/bin/env bash
# 生成官网运行时下载清单 downloads.json：大小与 SHA-256 直接取自实际产物，避免页面数值与文件漂移。
# 用法：
#   scripts/generate-downloads-manifest.sh --dist <dir> --version <v> --output <file> [--notes-dir <dir>]
#   scripts/generate-downloads-manifest.sh --dist <dir> --version <v> --check <file> [--notes-dir <dir>]
# 约束：
#   - 产物命名必须与 release CI 一致（Theca-v<version>-<platform>-<arch>-release.<ext>）；
#   - 不依赖 jq，兼容 macOS（shasum）与 Linux（sha256sum）；
#   - iOS 恒为不可用，不包含下载文件；
#   - notes 为三语发布说明，zh-CN.md 必需，其余缺失时回退 zh-CN；单文件上限 64 KiB。

set -euo pipefail

DIST=""
VERSION=""
OUTPUT=""
CHECK=""
NOTES_DIR=""

usage() {
    cat <<'EOF'
usage: generate-downloads-manifest.sh --dist <dir> --version <v> (--output <file> | --check <file>) [--notes-dir <dir>]
EOF
}

fail() { echo "清单生成失败：$1" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dist) DIST="${2:-}"; shift 2 ;;
        --version) VERSION="${2:-}"; shift 2 ;;
        --output) OUTPUT="${2:-}"; shift 2 ;;
        --check) CHECK="${2:-}"; shift 2 ;;
        --notes-dir) NOTES_DIR="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; echo "未知参数：$1" >&2; exit 2 ;;
    esac
done

[[ -n "$DIST" && -n "$VERSION" ]] || { usage >&2; exit 2; }
[[ -n "$OUTPUT" || -n "$CHECK" ]] || { usage >&2; exit 2; }
[[ -d "$DIST" ]] || fail "目录不存在：$DIST"
# 版本号写入 JSON 前先限制字符集，避免生成非法 JSON 或注入。
[[ "$VERSION" =~ ^[0-9A-Za-z._-]+$ ]] || fail "版本号含非法字符：$VERSION"
NOTES_DIR="${NOTES_DIR:-release-notes/v$VERSION}"
[[ -d "$NOTES_DIR" ]] || fail "发布说明目录不存在：$NOTES_DIR"
[[ -f "$NOTES_DIR/zh-CN.md" ]] || fail "缺少简体发布说明：$NOTES_DIR/zh-CN.md"

file_size() { wc -c < "$1" | tr -d ' '; }

# 将纯文本转为 JSON 字符串内容（不含两侧引号）；CRLF 归一为 LF，其余换行、制表符与引号逐个转义。
json_escape_file() {
    awk '
    BEGIN { ORS = ""; first = 1 }
    {
        if (!first) printf "\\n"
        first = 0
        sub(/\r$/, "")
        gsub(/\\/, "\\\\")
        gsub(/"/, "\\\"")
        gsub(/\t/, "\\t")
        gsub(/\r/, "\\r")
        printf "%s", $0
    }
    ' "$1"
}

notes_value() {
    local lang="$1" path size
    path="$NOTES_DIR/$lang.md"
    [[ -f "$path" ]] || path="$NOTES_DIR/zh-CN.md"
    size="$(file_size "$path")"
    [[ "$size" -le 65536 ]] || fail "发布说明超过 64 KiB：$path"
    json_escape_file "$path"
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

file_for() {
    case "$1" in
        android) printf 'Theca-v%s-android-release.apk' "$VERSION" ;;
        macos) printf 'Theca-v%s-macos-arm64-release.dmg' "$VERSION" ;;
        windows) printf 'Theca-v%s-windows-amd64-release.msi' "$VERSION" ;;
        linux) printf 'Theca-v%s-linux-amd64-release.deb' "$VERSION" ;;
        *) fail "未知平台：$1" ;;
    esac
}

render() {
    local generated_at zh_cn zh_tw en
    generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    zh_cn="$(notes_value zh-CN)"
    zh_tw="$(notes_value zh-TW)"
    en="$(notes_value en)"
    printf '{\n'
    printf '  "schemaVersion": 2,\n'
    printf '  "version": "%s",\n' "$VERSION"
    printf '  "generatedAt": "%s",\n' "$generated_at"
    printf '  "notes": {\n'
    printf '    "zh-CN": "%s",\n' "$zh_cn"
    printf '    "zh-TW": "%s",\n' "$zh_tw"
    printf '    "en": "%s"\n' "$en"
    printf '  },\n'
    printf '  "platforms": [\n'
    local entry_count=0 platform file path
    for platform in android ios macos windows linux; do
        if [[ "$platform" == "ios" ]]; then
            entry_count=$((entry_count + 1))
            printf '    { "id": "ios", "available": false }'
        else
            file="$(file_for "$platform")"
            path="$DIST/$file"
            [[ -s "$path" ]] || fail "缺少产物：$path"
            entry_count=$((entry_count + 1))
            printf '    { "id": "%s", "available": true, "file": "%s", "sizeBytes": %s, "sha256": "%s" }' \
                "$platform" "$file" "$(file_size "$path")" "$(sha256_of "$path")"
        fi
        if [[ "$entry_count" -lt 5 ]]; then
            printf ','
        fi
        printf '\n'
    done
    printf '  ]\n'
    printf '}\n'
}

tmp_file=""
cleanup() { [[ -z "$tmp_file" ]] || rm -f "$tmp_file"; }
trap cleanup EXIT

if [[ -n "$OUTPUT" ]]; then
    mkdir -p "$(dirname "$OUTPUT")"
    tmp_file="$(mktemp "${OUTPUT}.XXXXXX")"
    render > "$tmp_file"
    mv "$tmp_file" "$OUTPUT"
    # mktemp 默认 600，容器以 uid 10001 读取下载目录，清单必须可读。
    chmod 644 "$OUTPUT"
    tmp_file=""
    echo "清单已生成：$OUTPUT"
fi

if [[ -n "$CHECK" ]]; then
    [[ -f "$CHECK" ]] || fail "待比对清单不存在：$CHECK"
    tmp_file="$(mktemp)"
    render > "$tmp_file"
    # generatedAt 每次生成都不同，不参与比对；用行首字段名精确过滤，避免误删含同名的发布说明行。
    if diff -u <(grep -v '^  "generatedAt":' "$CHECK") <(grep -v '^  "generatedAt":' "$tmp_file") >/dev/null; then
        echo "OK: $CHECK 与产物一致"
    else
        diff -u <(grep -v '^  "generatedAt":' "$CHECK") <(grep -v '^  "generatedAt":' "$tmp_file") >&2 || true
        fail "清单与产物不一致：$CHECK"
    fi
fi
