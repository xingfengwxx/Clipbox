#!/usr/bin/env bash
# 校验 Android、Desktop、iOS、官网四处版本一致；可选传入发布 tag（如 v1.0.0）做二次校验。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT_DIR/ClipboxClient/composeApp/build.gradle.kts"
IOS_PROJECT="$ROOT_DIR/ClipboxClient/iosApp/iosApp.xcodeproj/project.pbxproj"
WEB_DATA="$ROOT_DIR/ClipboxWeb/src/data/downloads.ts"

print_only=false
release_tag=""

for arg in "$@"; do
    case "$arg" in
        --print-version)
            print_only=true
            ;;
        v*)
            release_tag="$arg"
            ;;
        *)
            echo "未知参数：$arg" >&2
            exit 2
            ;;
    esac
done

android_version="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$BUILD_FILE")"
desktop_version="$(sed -nE 's/^[[:space:]]*packageVersion[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$BUILD_FILE")"
ios_versions="$(sed -nE 's/^[[:space:]]*MARKETING_VERSION[[:space:]]*=[[:space:]]*([^;]+);.*/\1/p' "$IOS_PROJECT" | tr -d '[:blank:]' | sort -u)"
web_version="$(sed -nE "s/^export const VERSION = '([^']+)'.*/\1/p" "$WEB_DATA")"

fail() {
    echo "版本校验失败：$1" >&2
    exit 1
}

[[ -n "$android_version" ]] || fail "未找到 Android versionName"
[[ -n "$desktop_version" ]] || fail "未找到 Desktop packageVersion"
[[ -n "$ios_versions" ]] || fail "未找到 iOS MARKETING_VERSION"
[[ -n "$web_version" ]] || fail "未找到官网 VERSION"

if [[ "$(printf '%s\n' "$ios_versions" | wc -l | tr -d ' ')" != "1" ]]; then
    fail "iOS MARKETING_VERSION 存在多个值：$ios_versions"
fi

if [[ "$android_version" != "$desktop_version" || "$android_version" != "$ios_versions" || "$android_version" != "$web_version" ]]; then
    fail "版本不一致：android=$android_version desktop=$desktop_version ios=$ios_versions web=$web_version"
fi

if [[ -n "$release_tag" && "$release_tag" != "v$web_version" ]]; then
    fail "tag $release_tag 与版本 $web_version 不一致（期望 v$web_version）"
fi

if [[ "$print_only" == true ]]; then
    printf '%s\n' "$web_version"
    exit 0
fi

echo "android=$android_version"
echo "desktop=$desktop_version"
echo "ios=$ios_versions"
echo "web=$web_version"
if [[ -n "$release_tag" ]]; then
    echo "tag=$release_tag"
fi
echo "OK: $web_version"
