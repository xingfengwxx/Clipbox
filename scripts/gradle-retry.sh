#!/usr/bin/env bash
# 在工程根目录执行 Gradle 构建，失败时整体重试一次（默认间隔 60 秒）。
# 背景：release CI 跨境拉取 Google Maven / Plugin Portal 偶发不可达，曾两次导致 KSP 插件解析失败，
# 靠人工 rerun 才能继续；重试只针对整步失败，不掩盖编译错误（第二次仍失败则原样退出非零）。
set -uo pipefail

if [ "$#" -eq 0 ]; then
  echo "用法：$0 <Gradle 任务或参数>..." >&2
  exit 2
fi

attempts="${GRADLE_RETRY_ATTEMPTS:-2}"
delay="${GRADLE_RETRY_DELAY:-60}"

for attempt in $(seq 1 "$attempts"); do
  if ./gradlew "$@" --no-daemon; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ]; then
    echo "Gradle 构建失败（第 ${attempt}/${attempts} 次），${delay} 秒后重试" >&2
    sleep "$delay"
  fi
done

exit 1
