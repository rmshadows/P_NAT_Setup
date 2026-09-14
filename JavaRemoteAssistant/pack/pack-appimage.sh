#!/usr/bin/env bash
# 绿色目录：自带精简 JRE，解压即用（不装系统 Java）
# 注意：这是 jpackage app-image，不是 Linux .AppImage 单文件
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

need jpackage
if [[ "${SKIP_JAR:-}" != 1 ]]; then
  "$PACK_DIR/pack-jar.sh"
elif [[ ! -f "$FAT_JAR" ]]; then
  "$PACK_DIR/pack-jar.sh"
fi

rm -rf "$JPACKAGE_INPUT" "$DIST/$APP_NAME" "$DIST/${APP_NAME}.app"
mkdir -p "$JPACKAGE_INPUT"
cp -f "$FAT_JAR" "$JPACKAGE_INPUT/$JP_MAIN_JAR"

OS="$(uname -s)"
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|amd64) TAG_ARCH="x64" ;;
  aarch64|arm64) TAG_ARCH="aarch64" ;;
  *) TAG_ARCH="$ARCH" ;;
esac
case "$OS" in
  Linux*)             TAG="linux-${TAG_ARCH}" ;;
  Darwin*)            TAG="macos-${TAG_ARCH}" ;;
  MINGW*|MSYS*|CYGWIN*) TAG="windows-${TAG_ARCH}" ;;
  *)                  TAG="unknown" ;;
esac

echo "==> jpackage app-image"
ICON_JP=()
if [[ "$(uname -s)" == Darwin* ]]; then
  if [[ -f "$ICON_ICNS" ]]; then
    ICON_JP=(--icon "$ICON_ICNS")
  elif [[ -f "$ICON_PNG" ]]; then
    ICON_JP=(--icon "$ICON_PNG")
  else
    echo "警告：没有图标，绿色目录将用默认 Java 图标" >&2
  fi
elif [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; then
  if [[ -f "$ICON_ICO" && "$ICON_ICO" == *.ico ]]; then
    ICON_JP=(--icon "$ICON_ICO")
  else
    echo "警告：Windows 需要真正的 .ico（PNG 会让 jpackage 失败），将用默认 Java 图标" >&2
  fi
elif [[ -f "$ICON_PNG" ]]; then
  ICON_JP=(--icon "$ICON_PNG")
else
  echo "警告：找不到 $ICON_PNG，绿色目录将用默认 Java 图标" >&2
fi

if [[ ! -f "$JP_CONSOLE_PROPS" ]]; then
  echo "缺少 $JP_CONSOLE_PROPS" >&2
  exit 1
fi
ADD_LAUNCHER=(--add-launcher "${CONSOLE_LAUNCHER}=${JP_CONSOLE_PROPS}")
jpackage_modpath_args

JP_OPTS=(--java-options "$JAVA_OPTIONS")
if needs_javafx_modules; then
  JP_OPTS+=(--java-options '--enable-native-access=javafx.graphics')
fi
if [[ "$(uname -s)" == Linux* ]]; then
  JP_OPTS+=(--java-options '--add-opens=java.desktop/sun.awt=ALL-UNNAMED')
  JP_OPTS+=(--java-options '--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED')
fi

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --description "$APP_DESCRIPTION" \
  --dest "$DIST" \
  --input "$JPACKAGE_INPUT" \
  --main-jar "$JP_MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  "${JP_MODPATH[@]}" \
  --add-modules "$JP_MODULES" \
  "${ADD_LAUNCHER[@]}" \
  "${ICON_JP[@]}" \
  "${JP_OPTS[@]}"

ARCHIVE="$DIST/${APP_NAME}-${VERSION}-${TAG}.tar.gz"
rm -f "$ARCHIVE"
if [[ -d "$DIST/${APP_NAME}.app" ]]; then
  tar -C "$DIST" -czf "$ARCHIVE" "${APP_NAME}.app"
  echo "OK  $DIST/${APP_NAME}.app"
  echo "日常：$DIST/${APP_NAME}.app"
  if [[ -x "$DIST/${APP_NAME}.app/Contents/MacOS/${CONSOLE_LAUNCHER}" ]]; then
    echo "调试：$DIST/${APP_NAME}.app/Contents/MacOS/${CONSOLE_LAUNCHER}"
  fi
else
  tar -C "$DIST" -czf "$ARCHIVE" "$APP_NAME"
  echo "OK  $DIST/$APP_NAME/"
  echo "日常：$DIST/$APP_NAME/bin/$APP_NAME"
  if [[ -x "$DIST/$APP_NAME/bin/${CONSOLE_LAUNCHER}" ]]; then
    echo "调试：$DIST/$APP_NAME/bin/${CONSOLE_LAUNCHER}"
  fi
fi
echo "OK  $ARCHIVE"
