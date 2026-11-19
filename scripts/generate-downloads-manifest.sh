#!/usr/bin/env bash
# 生成官网运行时下载清单 downloads.json：大小与 SHA-256 直接取自实际产物，避免页面数值与文件漂移。
# 用法：
#   scripts/generate-downloads-manifest.sh --dist <dir> --version <v> --output <file>
#   scripts/generate-downloads-manifest.sh --dist <dir> --version <v> --check <file>
# 约束：
#   - 产物命名必须与 release CI 一致（Theca-v<version>-<platform>-<arch>-release.<ext>）；
#   - 不依赖 jq，兼容 macOS（shasum）与 Linux（sha256sum）；
#   - iOS 恒为不可用，不包含下载文件。

set -euo pipefail

DIST=""
VERSION=""
OUTPUT=""
CHECK=""

usage() {
    cat <<'EOF'
usage: generate-downloads-manifest.sh --dist <dir> --version <v> (--output <file> | --check <file>)
EOF
}

fail() { echo "清单生成失败：$1" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dist) DIST="${2:-}"; shift 2 ;;
        --version) VERSION="${2:-}"; shift 2 ;;
        --output) OUTPUT="${2:-}"; shift 2 ;;
        --check) CHECK="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; echo "未知参数：$1" >&2; exit 2 ;;
    esac
done

[[ -n "$DIST" && -n "$VERSION" ]] || { usage >&2; exit 2; }
[[ -n "$OUTPUT" || -n "$CHECK" ]] || { usage >&2; exit 2; }
[[ -d "$DIST" ]] || fail "目录不存在：$DIST"
# 版本号写入 JSON 前先限制字符集，避免生成非法 JSON 或注入。
[[ "$VERSION" =~ ^[0-9A-Za-z._-]+$ ]] || fail "版本号含非法字符：$VERSION"

file_size() { wc -c < "$1" | tr -d ' '; }

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
    local generated_at
    generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf '{\n'
    printf '  "schemaVersion": 1,\n'
    printf '  "version": "%s",\n' "$VERSION"
    printf '  "generatedAt": "%s",\n' "$generated_at"
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
    # generatedAt 每次生成都不同，不参与比对。
    if diff -u <(grep -v '"generatedAt"' "$CHECK") <(grep -v '"generatedAt"' "$tmp_file") >/dev/null; then
        echo "OK: $CHECK 与产物一致"
    else
        diff -u <(grep -v '"generatedAt"' "$CHECK") <(grep -v '"generatedAt"' "$tmp_file") >&2 || true
        fail "清单与产物不一致：$CHECK"
    fi
fi
