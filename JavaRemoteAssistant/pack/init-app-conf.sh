#!/usr/bin/env bash
# 扫描项目根（本文件夹上一级），生成 app.conf
# 用法：./init-app-conf.sh [--force]
set -euo pipefail

PACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$PACK_DIR/.." && pwd)"
CONF="$PACK_DIR/app.conf"
FORCE=0
[[ "${1:-}" == "--force" || "${1:-}" == "-f" ]] && FORCE=1

# shellcheck source=detect.sh
source "$PACK_DIR/detect.sh"

if [[ -f "$CONF" && "$FORCE" -ne 1 ]]; then
  echo "已有 $CONF" >&2
  echo "覆盖请加 --force；或直接编辑后跑 ./pack.sh" >&2
  exit 1
fi

if [[ ! -f "$ROOT/pom.xml" ]]; then
  echo "项目根没有 pom.xml：$ROOT" >&2
  echo "请把本文件夹放到 Maven 项目根下（与 pom.xml 同级的上一级）" >&2
  exit 1
fi

echo "==> 探测 $ROOT"

APP_NAME="$(detect_app_name || true)"
MAIN_CLASS="$(detect_main_class || true)"
MAVEN_JAR="$(detect_maven_jar || true)"
VENDOR="$(detect_vendor || true)"
APP_DESCRIPTION="$(detect_description || true)"
VERSION="$(detect_version || true)"
VERSION_JAVA="$(detect_version_java || true)"
ICON_PNG="$(detect_icon_png || true)"
ICON_ICO="$(detect_icon_ico || true)"
ICON_ICNS="$(detect_icon_icns || true)"
JP_MODULES="$(detect_jp_modules || true)"
MAVEN_EXTRA_ARGS="$(detect_maven_extra_args || true)"

: "${APP_NAME:=MyApp}"
: "${MAIN_CLASS:=}"
: "${MAVEN_JAR:=target/${APP_NAME}.jar}"
: "${VENDOR:=$APP_NAME}"
: "${APP_DESCRIPTION:=$APP_NAME}"
: "${VERSION:=}"
: "${VERSION_JAVA:=}"
: "${ICON_PNG:=other/icon.png}"
: "${ICON_ICO:=other/icon.ico}"
: "${ICON_ICNS:=other/icon.icns}"
: "${JP_MODULES:=java.base,java.desktop,java.datatransfer,java.prefs,jdk.charsets}"
: "${MAVEN_EXTRA_ARGS:=}"

LINUX_PKG_NAME="$(printf '%s' "$APP_NAME" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9+-.')"

missing=()
[[ -z "$MAIN_CLASS" ]] && missing+=(MAIN_CLASS)
[[ -z "$MAVEN_JAR" ]] && missing+=(MAVEN_JAR)
[[ -z "$VERSION" && -z "$VERSION_JAVA" ]] && missing+=(VERSION)

{
  cat <<EOF
# 由 init-app-conf.sh 自动生成。等号两边不要空格。路径相对项目根。
# 必填三项：APP_NAME / MAIN_CLASS / MAVEN_JAR（其余可留空，打包时还会再探测一次）

# —— 必填 ——
APP_NAME=$APP_NAME
MAIN_CLASS=$MAIN_CLASS
MAVEN_JAR=$MAVEN_JAR

# —— 建议核对 ——
VENDOR=$VENDOR
APP_DESCRIPTION=$APP_DESCRIPTION
VERSION=$VERSION
VERSION_JAVA=$VERSION_JAVA
LINUX_PKG_NAME=$LINUX_PKG_NAME

ICON_PNG=$ICON_PNG
ICON_ICO=$ICON_ICO
ICON_ICNS=$ICON_ICNS

JP_MODULES=$JP_MODULES
JAVAFX_JMODS=
MAVEN_EXTRA_ARGS=$MAVEN_EXTRA_ARGS
JAVA_OPTIONS=-Dfile.encoding=UTF-8
MENU_GROUP=Utility
EOF
} > "$CONF"

echo "已写入 $CONF"
echo
echo "探测结果："
echo "  APP_NAME=$APP_NAME"
echo "  MAIN_CLASS=${MAIN_CLASS:-（未找到，请手写）}"
echo "  MAVEN_JAR=$MAVEN_JAR"
echo "  VERSION=${VERSION:-（空）}  VERSION_JAVA=${VERSION_JAVA:-（空）}"
echo "  JP_MODULES=$JP_MODULES"
echo "  MAVEN_EXTRA_ARGS=${MAVEN_EXTRA_ARGS:-（无）}"
if [[ ${#missing[@]} -gt 0 ]]; then
  echo
  echo "请补全：${missing[*]}" >&2
  exit 1
fi
echo
echo "检查无误后：./pack.sh"
