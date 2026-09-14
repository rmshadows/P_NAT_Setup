#!/usr/bin/env bash
# 可执行 JAR（mvn package；对方机器需要能跑该 JAR 的 JDK）
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

need mvn
mkdir -p "$DIST"

if [[ "${SKIP_JAR:-}" == 1 && -f "$FAT_JAR" ]]; then
  echo "跳过 Maven（已有 $FAT_JAR）"
  exit 0
fi

echo "==> Maven package"
# shellcheck disable=SC2086
mvn -q -DskipTests ${MAVEN_EXTRA_ARGS} package

if [[ ! -f "$MAVEN_JAR" ]]; then
  echo "Maven 没有打出 $MAVEN_JAR" >&2
  echo "请检查 app.conf 的 MAVEN_JAR（应对应 pom 的 finalName / artifact）" >&2
  exit 1
fi
cp -f "$MAVEN_JAR" "$FAT_JAR"
echo "OK  $FAT_JAR"
echo "运行：java -jar \"$FAT_JAR\""
