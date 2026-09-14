#!/usr/bin/env bash
# Linux .deb（jpackage，自带 JRE）
# 产物：
#   dist/<pkg>_<ver>_<arch>.deb         — 原版：Depends 按本机扫库名
#   dist/<pkg>_<ver>_<arch>.compat.deb  — 宽松：libasound2 | libasound2t64
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo ".deb 只能在 Linux 上打" >&2
  exit 1
fi

need jpackage
need fakeroot
need dpkg-deb

if [[ "${SKIP_JAR:-}" != 1 ]]; then
  "$PACK_DIR/pack-jar.sh"
elif [[ ! -f "$FAT_JAR" ]]; then
  "$PACK_DIR/pack-jar.sh"
fi

rm -rf "$JPACKAGE_INPUT"
mkdir -p "$JPACKAGE_INPUT"
cp -f "$FAT_JAR" "$JPACKAGE_INPUT/$JP_MAIN_JAR"

echo "==> jpackage deb"
rm -f "$DIST"/${LINUX_PKG_NAME}_*.deb "$DIST"/${APP_NAME}-*.deb

ICON_JP=()
if [[ -f "$ICON_PNG" ]]; then
  ICON_JP=(--icon "$ICON_PNG")
else
  echo "警告：找不到 $ICON_PNG，.deb 将用默认 Java 图标" >&2
fi

if [[ ! -f "$DESKTOP_TEMPLATE" ]]; then
  echo "缺少 $DESKTOP_TEMPLATE" >&2
  exit 1
fi
if [[ ! -f "$CONTROL_COMPAT" ]]; then
  echo "缺少 $CONTROL_COMPAT" >&2
  exit 1
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
JP_OPTS+=(--java-options '--add-opens=java.desktop/sun.awt=ALL-UNNAMED')
JP_OPTS+=(--java-options '--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED')

build_deb() {
  local resdir="$1"
  jpackage \
    --type deb \
    --name "$APP_NAME" \
    --linux-package-name "$LINUX_PKG_NAME" \
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
    --linux-shortcut \
    --linux-menu-group "$MENU_GROUP" \
    --resource-dir "$resdir" \
    "${ICON_JP[@]}" \
    "${JP_OPTS[@]}"
}

# jpackage 要求 Debian 包名全小写，默认装到 /opt/<包名>。
# 用户可见目录用 APP_NAME 全称：/opt/JavaRemoteAssistant
relocate_deb_install_dir() {
  local deb="$1"
  local src_opt="/opt/${LINUX_PKG_NAME}"
  local dst_opt="/opt/${APP_NAME}"
  if [[ "$src_opt" == "$dst_opt" || ! -f "$deb" ]]; then
    return 0
  fi
  local work
  work="$(mktemp -d)"
  dpkg-deb -R "$deb" "$work"
  if [[ -d "$work$src_opt" ]]; then
    mkdir -p "$(dirname "$work$dst_opt")"
    mv "$work$src_opt" "$work$dst_opt"
  fi
  local f
  while IFS= read -r f; do
    [[ -n "$f" ]] && sed -i "s#${src_opt}#${dst_opt}#g" "$f"
  done < <(grep -rl --binary-files=without-match "$src_opt" "$work" 2>/dev/null || true)
  dpkg-deb --root-owner-group -b "$work" "$deb"
  rm -rf "$work"
}

RES_ORIG="$(mktemp -d)"
fill_desktop "$RES_ORIG/${APP_NAME}.desktop"
echo "==> deb 原版（扫库名）"
build_deb "$RES_ORIG"
ORIG_DEB="$(ls -1 "$DIST"/${LINUX_PKG_NAME}_*.deb | head -1)"
ORIG_SAVED="$(mktemp)"
cp -f "$ORIG_DEB" "$ORIG_SAVED"
rm -f "$ORIG_DEB"
rm -rf "$RES_ORIG"

RES_COMPAT="$(mktemp -d)"
fill_desktop "$RES_COMPAT/${APP_NAME}.desktop"
cp -f "$CONTROL_COMPAT" "$RES_COMPAT/control"
echo "==> deb 宽松 Depends（compat）"
build_deb "$RES_COMPAT"
COMPAT_DEB="$(ls -1 "$DIST"/${LINUX_PKG_NAME}_*.deb | head -1)"
BASE="$(basename "$COMPAT_DEB" .deb)"
mv -f "$COMPAT_DEB" "$DIST/${BASE}.compat.deb"
cp -f "$ORIG_SAVED" "$DIST/${BASE}.deb"
rm -f "$ORIG_SAVED"
rm -rf "$RES_COMPAT"
echo "==> 安装目录改为 /opt/${APP_NAME}"
relocate_deb_install_dir "$DIST/${BASE}.deb"
relocate_deb_install_dir "$DIST/${BASE}.compat.deb"

echo "OK  dist/ 下的 .deb"
ls -1 "$DIST"/*.deb
echo "原版（本机扫库名）：$DIST/${BASE}.deb"
echo "宽松（Debian 12/13、Ubuntu）：$DIST/${BASE}.compat.deb"
echo "装一份即可，不要两个一起装（同名包）。"
echo "日常：/opt/${APP_NAME}/bin/${APP_NAME}"
echo "调试：/opt/${APP_NAME}/bin/${CONSOLE_LAUNCHER}"
