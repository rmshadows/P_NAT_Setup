#!/usr/bin/env bash
# 开发启动：定位包根、导出 PNAT_*、mvn javafx:run（对照 run.bat）
set -euo pipefail

JRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$JRA_DIR"

PKG_ROOT="$(cd "$JRA_DIR/.." && pwd)"
if [[ ! -f "$PKG_ROOT/conf/app.conf" && -f "$JRA_DIR/../../conf/app.conf" ]]; then
  PKG_ROOT="$(cd "$JRA_DIR/../.." && pwd)"
fi

export PNAT_ROOT="$PKG_ROOT"
export PNAT_CONF="$PKG_ROOT/conf"
export PNAT_RES="$PKG_ROOT/res"

echo "JRA dir:   $JRA_DIR"
echo "PKG root:  $PKG_ROOT"
echo

if ! command -v java >/dev/null 2>&1; then
  echo "找不到 java。请安装 JDK 17+ 并加入 PATH。" >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "找不到 mvn。请安装 Maven 并加入 PATH。" >&2
  exit 1
fi

echo "Starting: mvn -DskipTests javafx:run"
echo "Ctrl+C 结束 JRA。"
echo
exec mvn -DskipTests javafx:run
