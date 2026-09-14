#!/usr/bin/env bash
# macOS .dmg（jpackage，自带 JRE）。必须在 Mac 上跑。
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo ".dmg 只能在 macOS 上打（jpackage 不能跨平台打包）" >&2
  exit 1
fi

need jpackage
if [[ "${SKIP_JAR:-}" != 1 ]]; then
  "$PACK_DIR/pack-jar.sh"
elif [[ ! -f "$FAT_JAR" ]]; then
  "$PACK_DIR/pack-jar.sh"
fi

rm -rf "$JPACKAGE_INPUT"
mkdir -p "$JPACKAGE_INPUT"
cp -f "$FAT_JAR" "$JPACKAGE_INPUT/$JP_MAIN_JAR"

echo "==> jpackage dmg"
rm -f "$DIST"/*.dmg

if [[ ! -f "$ICON_ICNS" && -f "$ICON_PNG" ]]; then
  ICONSET="$DIST/${APP_NAME}.iconset"
  rm -rf "$ICONSET"
  mkdir -p "$ICONSET"
  sips -z 16 16 "$ICON_PNG" --out "$ICONSET/icon_16x16.png" >/dev/null
  sips -z 32 32 "$ICON_PNG" --out "$ICONSET/icon_16x16@2x.png" >/dev/null
  sips -z 32 32 "$ICON_PNG" --out "$ICONSET/icon_32x32.png" >/dev/null
  sips -z 64 64 "$ICON_PNG" --out "$ICONSET/icon_32x32@2x.png" >/dev/null
  sips -z 128 128 "$ICON_PNG" --out "$ICONSET/icon_128x128.png" >/dev/null
  sips -z 256 256 "$ICON_PNG" --out "$ICONSET/icon_128x128@2x.png" >/dev/null
  sips -z 256 256 "$ICON_PNG" --out "$ICONSET/icon_256x256.png" >/dev/null
  sips -z 512 512 "$ICON_PNG" --out "$ICONSET/icon_256x256@2x.png" >/dev/null
  sips -z 512 512 "$ICON_PNG" --out "$ICONSET/icon_512x512.png" >/dev/null
  sips -z 1024 1024 "$ICON_PNG" --out "$ICONSET/icon_512x512@2x.png" >/dev/null
  iconutil -c icns "$ICONSET" -o "$ICON_ICNS"
  rm -rf "$ICONSET"
fi

ICON_JP=()
if [[ -f "$ICON_ICNS" ]]; then
  ICON_JP=(--icon "$ICON_ICNS")
else
  echo "警告：找不到 $ICON_ICNS，.dmg 将用默认 Java 图标" >&2
fi

if [[ ! -f "$JP_CONSOLE_PROPS" ]]; then
  echo "缺少 $JP_CONSOLE_PROPS" >&2
  exit 1
fi

jpackage_modpath_args

JP_OPTS=(--java-options "$JAVA_OPTIONS")
if needs_javafx_modules; then
  JP_OPTS+=(--java-options '--enable-native-access=javafx.graphics')
fi

jpackage \
  --type dmg \
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
  --add-launcher "${CONSOLE_LAUNCHER}=${JP_CONSOLE_PROPS}" \
  "${ICON_JP[@]}" \
  "${JP_OPTS[@]}"

echo "OK  dist/ 下的 .dmg"
ls -1 "$DIST"/*.dmg
echo "日常：安装后的 ${APP_NAME}.app"
echo "调试：${APP_NAME}.app/Contents/MacOS/${CONSOLE_LAUNCHER}"
echo "未签名。本机打开若被拦，可右键打开或去系统设置放行。"
