#!/usr/bin/env bash
# 被打包脚本 source。项目根 = 本文件夹的上一级（文件夹叫 pack / pack2 都行）。
set -euo pipefail

PACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$PACK_DIR/.." && pwd)"
cd "$ROOT"

# shellcheck source=detect.sh
source "$PACK_DIR/detect.sh"

CONF="$PACK_DIR/app.conf"
if [[ ! -f "$CONF" ]]; then
  echo "找不到 $CONF" >&2
  echo "请先运行：$PACK_DIR/init-app-conf.sh" >&2
  echo "或复制 app.conf.example 为 app.conf 并填写必填项" >&2
  exit 1
fi

APP_NAME=""
MAIN_CLASS=""
MAVEN_JAR=""
VENDOR=""
APP_DESCRIPTION=""
VERSION=""
VERSION_JAVA=""
LINUX_PKG_NAME=""
ICON_PNG="other/icon.png"
ICON_ICO="other/icon.ico"
ICON_ICNS="other/icon.icns"
JP_MODULES="java.base,java.desktop,java.datatransfer,java.prefs,jdk.charsets"
JAVA_OPTIONS="-Dfile.encoding=UTF-8"
MENU_GROUP="Utility"
JAVAFX_VERSION=""
JAVAFX_JMODS=""
MAVEN_EXTRA_ARGS=""

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ -z "${line//[[:space:]]/}" ]] && continue
  if [[ "$line" != *=* ]]; then
    echo "app.conf 无法解析：$line" >&2
    exit 1
  fi
  key="${line%%=*}"
  val="${line#*=}"
  key="${key%"${key##*[![:space:]]}"}"
  key="${key#"${key%%[![:space:]]*}"}"
  case "$key" in
    APP_NAME|MAIN_CLASS|MAVEN_JAR|VENDOR|APP_DESCRIPTION|VERSION|VERSION_JAVA|LINUX_PKG_NAME|ICON_PNG|ICON_ICO|ICON_ICNS|JP_MODULES|JAVA_OPTIONS|MENU_GROUP|JAVAFX_VERSION|JAVAFX_JMODS|MAVEN_EXTRA_ARGS)
      printf -v "$key" '%s' "$val"
      ;;
    *)
      echo "app.conf 未知项：$key" >&2
      exit 1
      ;;
  esac
done < "$CONF"

# 空字段从 pom / 源码自动补
detect_fill_empty

if [[ -z "$APP_NAME" || -z "$MAIN_CLASS" || -z "$MAVEN_JAR" ]]; then
  echo "仍缺必填项（自动探测也失败）：" >&2
  [[ -z "$APP_NAME" ]] && echo "  APP_NAME" >&2
  [[ -z "$MAIN_CLASS" ]] && echo "  MAIN_CLASS" >&2
  [[ -z "$MAVEN_JAR" ]] && echo "  MAVEN_JAR" >&2
  echo "请编辑 $CONF，或重新跑 $PACK_DIR/init-app-conf.sh --force" >&2
  exit 1
fi
if [[ "$APP_NAME" == *" "* ]]; then
  echo "APP_NAME 不要含空格（jpackage --name 会出问题）" >&2
  exit 1
fi

abs_under_root() {
  local p="$1"
  if [[ -z "$p" ]]; then
    echo ""
    return
  fi
  if [[ "$p" = /* ]]; then
    echo "$p"
  else
    echo "$ROOT/$p"
  fi
}

read_version_from_java() {
  sed -n 's/.*VERSION = "\([^"]*\)".*/\1/p' "$1" | head -1
}

read_version_from_pom() {
  detect_version
}

if [[ -z "$VERSION" && -n "$VERSION_JAVA" ]]; then
  VFILE="$(abs_under_root "$VERSION_JAVA")"
  if [[ ! -f "$VFILE" ]]; then
    echo "找不到 VERSION_JAVA：$VFILE" >&2
    exit 1
  fi
  VERSION="$(read_version_from_java "$VFILE")"
fi
if [[ -z "$VERSION" ]]; then
  VERSION="$(read_version_from_pom || true)"
fi
if [[ -z "$VERSION" ]]; then
  echo "读不到版本：请在 app.conf 写 VERSION=，或 VERSION_JAVA=，或保证 pom.xml 有项目 <version>" >&2
  exit 1
fi

APP_VERSION="${VERSION%%-*}"
if [[ -z "$VENDOR" ]]; then
  VENDOR="$APP_NAME"
fi
if [[ -z "$APP_DESCRIPTION" ]]; then
  APP_DESCRIPTION="$APP_NAME"
fi
if [[ -z "$LINUX_PKG_NAME" ]]; then
  LINUX_PKG_NAME="$(printf '%s' "$APP_NAME" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9+-.')"
fi

DIST="$ROOT/dist"
JPACKAGE_INPUT="$DIST/jpackage-input"
FAT_JAR="$DIST/${APP_NAME}_${VERSION}.jar"
MAVEN_JAR="$(abs_under_root "$MAVEN_JAR")"
ICON_PNG="$(abs_under_root "$ICON_PNG")"
ICON_ICO="$(abs_under_root "$ICON_ICO")"
ICON_ICNS="$(abs_under_root "$ICON_ICNS")"
JP_MAIN_JAR="${APP_NAME}.jar"
CONSOLE_LAUNCHER="${APP_NAME}-console"
DESKTOP_TEMPLATE="$PACK_DIR/jpackage-resources/app.desktop"
CONTROL_COMPAT="$PACK_DIR/jpackage-resources/control"

case "$(uname -s)" in
  Darwin*) JP_CONSOLE_PROPS="$PACK_DIR/mac-console.properties" ;;
  *)       JP_CONSOLE_PROPS="$PACK_DIR/linux-console.properties" ;;
esac

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令：$1" >&2
    exit 1
  fi
}

fill_desktop() {
  local dest="$1"
  sed \
    -e "s#@APP_NAME@#${APP_NAME}#g" \
    -e "s#@APP_DESCRIPTION@#${APP_DESCRIPTION}#g" \
    -e "s#@LINUX_PKG_NAME@#${LINUX_PKG_NAME}#g" \
    -e "s#@MENU_GROUP@#${MENU_GROUP}#g" \
    "$DESKTOP_TEMPLATE" > "$dest"
}

needs_javafx_modules() {
  [[ "$JP_MODULES" == *javafx.* ]]
}

# JavaFX 必须作为命名模块进 jlink 运行时。打进 fat JAR 不够，Application.launch() 仍会报缺少运行时组件。
# Maven Central 没有 javafx-jmods，用当前平台的模块 JAR（不要开 all-natives，避免把 win/mac 本地库也拷进来）。
ensure_javafx_modules() {
  local dest
  if [[ -n "${JAVAFX_JMODS:-}" ]]; then
    if [[ -d "${JAVAFX_JMODS}" ]] && { ls "${JAVAFX_JMODS}"/javafx.base.jmod >/dev/null 2>&1 || ls "${JAVAFX_JMODS}"/javafx.base*.jar >/dev/null 2>&1 || ls "${JAVAFX_JMODS}"/javafx-base*.jar >/dev/null 2>&1; }; then
      return 0
    fi
    echo "JAVAFX_JMODS 目录不可用：$JAVAFX_JMODS" >&2
    exit 1
  fi

  need mvn
  dest="$ROOT/target/javafx-runtime"
  rm -rf "$dest"
  mkdir -p "$dest"
  echo "==> 复制当前平台 JavaFX 模块 JAR"
  mvn -q -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
    -DincludeGroupIds=org.openjfx \
    -DoutputDirectory="$dest" \
    -DoverWriteIfNewer=true
  if ! ls "$dest"/javafx-*.jar >/dev/null 2>&1; then
    echo "没有拷到 JavaFX JAR，请确认 pom.xml 依赖 org.openjfx" >&2
    exit 1
  fi
  JAVAFX_JMODS="$dest"
  echo "JavaFX modules: $JAVAFX_JMODS"
}

resolve_jdk_jmods() {
  if [[ -n "${JAVA_HOME:-}" && -d "${JAVA_HOME}/jmods" ]]; then
    echo "${JAVA_HOME}/jmods"
    return
  fi
  local home
  home="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.home = //p' | head -1 | tr -d '\r')"
  if [[ -n "$home" && -d "$home/jmods" ]]; then
    echo "$home/jmods"
  fi
}

jpackage_modpath_args() {
  JP_MODPATH=()
  if needs_javafx_modules; then
    ensure_javafx_modules
    local jdk
    jdk="$(resolve_jdk_jmods)"
    if [[ -n "$jdk" ]]; then
      JP_MODPATH=(--module-path "${jdk}:${JAVAFX_JMODS}")
    else
      JP_MODPATH=(--module-path "$JAVAFX_JMODS")
    fi
  fi
}

echo "$APP_NAME $VERSION  (app-version $APP_VERSION)"
echo "项目目录 $ROOT"
echo "配置 $CONF"
echo "主类 $MAIN_CLASS"
echo "JAR  $MAVEN_JAR"
