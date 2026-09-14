#!/usr/bin/env bash
# 一键打包：跨平台 fat JAR + 当前系统绿色目录 + 当前系统安装包
#
# jpackage 不能交叉编译原生安装包，要哪种系统的安装包就在哪种系统上跑：
#   Linux  → JAR + 绿色目录 + .deb
#   macOS  → JAR + 绿色目录 + .dmg
#   Windows（Git Bash）→ JAR + 绿色目录 + .exe   日常请用 pack.bat
#
# 用法：
#   ./pack.sh           当前平台能打的全部
#   ./pack.sh jar       只打跨平台 JAR（win/linux/mac 都能 java -jar）
#   ./pack.sh linux     只打 Linux 安装包（必须在 Linux）
#   ./pack.sh mac       只打 macOS 安装包（必须在 macOS）
#   ./pack.sh win       只打 Windows 安装包（必须在 Windows；WiX 自动探测/选择）
#   ./pack.sh win 3     强制 WiX 3
#   ./pack.sh win 5     强制 WiX 4/5
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TARGET="${1:-all}"
WIX_ARG="${2:-}"
OS="$(uname -s)"

pack_win_exe() {
  # 统一走 pack-win-choose.bat（探测 WiX 3 / 5）；备用：pack-win.bat / pack-win-wix5.bat
  if [[ -f "$PACK_DIR/pack-win-choose.bat" ]]; then
    local arg="${1:-}"
    if [[ -n "$arg" ]]; then
      cmd.exe //c "\"$PACK_DIR/pack-win-choose.bat\" $arg"
    else
      cmd.exe //c "\"$PACK_DIR/pack-win-choose.bat\""
    fi
  else
    "$PACK_DIR/pack-win.sh"
  fi
}

pack_current_native() {
  "$PACK_DIR/pack-appimage.sh"
  case "$OS" in
    Linux)
      "$PACK_DIR/pack-deb.sh"
      ;;
    Darwin)
      "$PACK_DIR/pack-mac.sh"
      ;;
    MINGW*|MSYS*|CYGWIN*)
      pack_win_exe "$WIX_ARG"
      ;;
    *)
      echo "本平台没有系统安装包脚本（只出了 JAR 和绿色目录）"
      ;;
  esac
}

case "$TARGET" in
  jar)
    "$PACK_DIR/pack-jar.sh"
    ;;
  linux)
    if [[ "$OS" != "Linux" ]]; then
      echo "Linux 包只能在 Linux 上打（jpackage 不能跨平台）" >&2
      exit 1
    fi
    "$PACK_DIR/pack-jar.sh"
    export SKIP_JAR=1
    "$PACK_DIR/pack-appimage.sh"
    "$PACK_DIR/pack-deb.sh"
    ;;
  mac|macos|darwin)
    if [[ "$OS" != "Darwin" ]]; then
      echo "macOS 包只能在 macOS 上打（jpackage 不能跨平台）" >&2
      exit 1
    fi
    "$PACK_DIR/pack-jar.sh"
    export SKIP_JAR=1
    "$PACK_DIR/pack-appimage.sh"
    "$PACK_DIR/pack-mac.sh"
    ;;
  win|windows)
    case "$OS" in
      MINGW*|MSYS*|CYGWIN*) ;;
      *)
        echo "Windows 包只能在 Windows 上打（jpackage 不能跨平台）。请用 pack.bat" >&2
        exit 1
        ;;
    esac
    "$PACK_DIR/pack-jar.sh"
    export SKIP_JAR=1
    "$PACK_DIR/pack-appimage.sh"
    pack_win_exe "$WIX_ARG"
    ;;
  3|5|wix3|wix5|wix4|4)
    # 在 Windows 上一键：./pack.sh 5 等同 pack.bat 5
    case "$OS" in
      MINGW*|MSYS*|CYGWIN*) ;;
      *)
        echo "WiX 选择只能在 Windows 上用。请用 pack.bat $TARGET" >&2
        exit 1
        ;;
    esac
    "$PACK_DIR/pack-jar.sh"
    export SKIP_JAR=1
    "$PACK_DIR/pack-appimage.sh"
    pack_win_exe "$TARGET"
    ;;
  all|"")
    "$PACK_DIR/pack-jar.sh"
    export SKIP_JAR=1
    pack_current_native
    echo
    echo "三平台产物："
    echo "  JAR（已打，win/linux/mac 都能跑）：$FAT_JAR"
    echo "  Linux .deb / 绿色目录：在 Linux 上跑 ./pack.sh linux 或 ./pack.sh"
    echo "  macOS .dmg / .app：在 macOS 上跑 ./pack.sh mac"
    echo "  Windows .exe / 绿色目录：在 Windows 上跑 pack.bat（自动选 WiX）"
    ;;
  *)
    echo "未知参数：$TARGET" >&2
    echo "用法：./pack.sh [all|jar|linux|mac|win|3|5]" >&2
    exit 1
    ;;
esac

echo
echo "==> 完成，产物在 $DIST"
ls -1 "$DIST" | sed 's/^/  /'
if [[ -x "$DIST/$APP_NAME/bin/${CONSOLE_LAUNCHER}" ]]; then
  echo
  echo "日常：$DIST/$APP_NAME/bin/$APP_NAME"
  echo "调试：$DIST/$APP_NAME/bin/$CONSOLE_LAUNCHER"
elif [[ -x "$DIST/${APP_NAME}.app/Contents/MacOS/${CONSOLE_LAUNCHER}" ]]; then
  echo
  echo "日常：$DIST/${APP_NAME}.app"
  echo "调试：$DIST/${APP_NAME}.app/Contents/MacOS/$CONSOLE_LAUNCHER"
fi
