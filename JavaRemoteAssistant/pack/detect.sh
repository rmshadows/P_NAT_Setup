#!/usr/bin/env bash
# 从 Maven 项目自动探测打包参数（被 common.sh / init-app-conf.sh source）
# 依赖：ROOT 已设；尽量用 mvn help:evaluate，失败则扫 pom.xml / 源码

detect_need_mvn() {
  command -v mvn >/dev/null 2>&1
}

# 去掉 pom 命名空间，方便 sed
_pom_flat() {
  if [[ -f "$ROOT/pom.xml" ]]; then
    tr -d '\r' < "$ROOT/pom.xml" | sed 's/xmlns="[^"]*"//g'
  fi
}

_mvn_eval() {
  local expr="$1"
  local out
  if ! detect_need_mvn || [[ ! -f "$ROOT/pom.xml" ]]; then
    return 1
  fi
  out="$(cd "$ROOT" && mvn -q -DforceStdout help:evaluate -Dexpression="$expr" 2>/dev/null || true)"
  out="${out//$'\r'/}"
  if [[ -z "$out" || "$out" == "null object or invalid expression" || "$out" == \$\{*\} ]]; then
    return 1
  fi
  printf '%s' "$out"
}

_pom_tag_after_parent() {
  local tag="$1"
  _pom_flat | awk -v tag="$tag" '
    /<parent>/ { p=1 }
    /<\/parent>/ { p=0; next }
    p { next }
    $0 ~ "<" tag ">" {
      sub(".*<" tag ">", "")
      sub("</" tag ">.*", "")
      gsub(/^[ \t]+|[ \t]+$/, "")
      print
      exit
    }
  '
}

_pom_property() {
  local key="$1"
  _pom_flat | sed -n "s#.*<${key}>\\([^<]*\\)</${key}>.*#\\1#p" | head -1
}

_pom_has_openjfx() {
  _pom_flat | grep -q '<groupId>org.openjfx</groupId>'
}

_pom_has_profile() {
  local id="$1"
  _pom_flat | grep -q "<id>${id}</id>"
}

_pom_javafx_artifacts() {
  _pom_flat | sed -n 's#.*<artifactId>\(javafx-[a-z0-9]*\)</artifactId>.*#\1#p' | sort -u
}

detect_artifact_id() {
  _mvn_eval project.artifactId || _pom_tag_after_parent artifactId
}

detect_version() {
  _mvn_eval project.version || _pom_tag_after_parent version
}

detect_app_name() {
  local n
  n="$(_mvn_eval project.name 2>/dev/null || true)"
  if [[ -n "$n" && "$n" != "null object or invalid expression" ]]; then
    # Maven 默认 name 常是 ${project.artifactId} 已展开；去掉空格给 jpackage
    n="$(printf '%s' "$n" | tr -d '[:space:]')"
    if [[ -n "$n" ]]; then
      printf '%s' "$n"
      return
    fi
  fi
  n="$(_pom_tag_after_parent name)"
  n="$(printf '%s' "$n" | tr -d '[:space:]')"
  if [[ -n "$n" && "$n" != '${'* ]]; then
    printf '%s' "$n"
    return
  fi
  detect_artifact_id
}

detect_vendor() {
  local g
  g="$(_mvn_eval project.groupId || _pom_tag_after_parent groupId)"
  # groupId 取最后一段更像厂商短名；整段也可
  printf '%s' "$g"
}

detect_description() {
  local d
  d="$(_mvn_eval project.description || _pom_tag_after_parent description)"
  printf '%s' "$d"
}

detect_main_class() {
  local m
  m="$(_mvn_eval main.class 2>/dev/null || true)"
  if [[ -n "$m" && "$m" != \$\{* ]]; then
    printf '%s' "$m"
    return
  fi
  m="$(_pom_property main.class)"
  if [[ -n "$m" && "$m" != \$\{* ]]; then
    printf '%s' "$m"
    return
  fi
  # shade / jar / javafx 插件里的 mainClass（去掉 module/ 前缀）
  m="$(_pom_flat | sed -n 's#.*<mainClass>\([^<]*\)</mainClass>.*#\1#p' | head -1)"
  if [[ -n "$m" ]]; then
    m="${m##*/}"
    if [[ "$m" != \$\{* ]]; then
      printf '%s' "$m"
      return
    fi
  fi
  # 源码扫 main；优先继承 Application 的
  if [[ -d "$ROOT/src/main/java" ]]; then
    local f best=""
    while IFS= read -r f; do
      [[ -z "$f" ]] && continue
      if grep -q 'extends Application' "$f" 2>/dev/null || grep -q 'javafx.application.Application' "$f" 2>/dev/null; then
        best="$f"
        break
      fi
      [[ -z "$best" ]] && best="$f"
    done < <(grep -rl --include='*.java' 'public static void main' "$ROOT/src/main/java" 2>/dev/null || true)
    if [[ -n "$best" ]]; then
      # 路径转类名
      m="${best#"$ROOT/src/main/java/"}"
      m="${m%.java}"
      m="${m//\//.}"
      printf '%s' "$m"
      return
    fi
  fi
}

detect_maven_jar() {
  local final art ver
  final="$(_mvn_eval project.build.finalName 2>/dev/null || true)"
  if [[ -n "$final" && "$final" != \$\{* ]]; then
    printf 'target/%s.jar' "$final"
    return
  fi
  art="$(detect_artifact_id)"
  ver="$(detect_version)"
  if [[ -n "$art" && -n "$ver" ]]; then
    printf 'target/%s-%s.jar' "$art" "$ver"
    return
  fi
  if [[ -n "$art" ]]; then
    printf 'target/%s.jar' "$art"
  fi
}

detect_version_java() {
  local f
  if [[ ! -d "$ROOT/src" ]]; then
    return
  fi
  f="$(grep -rl --include='*.java' 'VERSION = "' "$ROOT/src" 2>/dev/null | head -1 || true)"
  if [[ -n "$f" ]]; then
    printf '%s' "${f#"$ROOT/"}"
  fi
}

detect_icon_png() {
  local f
  for f in \
    src/main/resources/icon.png \
    src/main/resources/icons/icon.png \
    src/main/resources/resources/icon.png \
    other/icon.png \
    icon.png
  do
    if [[ -f "$ROOT/$f" ]]; then
      printf '%s' "$f"
      return
    fi
  done
  f="$(find "$ROOT/src" -iname 'icon.png' 2>/dev/null | head -1 || true)"
  if [[ -n "$f" ]]; then
    printf '%s' "${f#"$ROOT/"}"
  fi
}

detect_icon_ico() {
  local f
  for f in other/icon.ico src/main/resources/icon.ico icon.ico; do
    [[ -f "$ROOT/$f" ]] && { printf '%s' "$f"; return; }
  done
  f="$(find "$ROOT/src" -iname 'icon.ico' 2>/dev/null | head -1 || true)"
  [[ -n "$f" ]] && printf '%s' "${f#"$ROOT/"}"
}

detect_icon_icns() {
  local f
  for f in other/icon.icns src/main/resources/icon.icns icon.icns; do
    [[ -f "$ROOT/$f" ]] && { printf '%s' "$f"; return; }
  done
  f="$(find "$ROOT/src" -iname 'icon.icns' 2>/dev/null | head -1 || true)"
  [[ -n "$f" ]] && printf '%s' "${f#"$ROOT/"}"
}

# 输出完整 JP_MODULES 字符串
detect_jp_modules() {
  local base="java.base,java.desktop,java.datatransfer,java.prefs,jdk.charsets"
  local mods=()
  local a mi

  if [[ -f "$ROOT/src/main/java/module-info.java" ]]; then
    while IFS= read -r mi; do
      mods+=("$mi")
    done < <(sed -n 's/.*requires \(transitive \)*\(javafx\.[a-z0-9.]*\).*/\2/p' "$ROOT/src/main/java/module-info.java" | tr -d ';')
  fi

  if _pom_has_openjfx || [[ ${#mods[@]} -gt 0 ]]; then
    if [[ ${#mods[@]} -eq 0 ]]; then
      while IFS= read -r a; do
        case "$a" in
          javafx-base) mods+=(javafx.base) ;;
          javafx-graphics) mods+=(javafx.graphics) ;;
          javafx-controls) mods+=(javafx.controls) ;;
          javafx-fxml) mods+=(javafx.fxml) ;;
          javafx-media) mods+=(javafx.media) ;;
          javafx-web) mods+=(javafx.web) ;;
          javafx-swing) mods+=(javafx.swing) ;;
        esac
      done < <(_pom_javafx_artifacts)
    fi
    # 保底
    if [[ ${#mods[@]} -eq 0 ]]; then
      mods=(javafx.base javafx.graphics javafx.controls)
    fi
  fi

  if [[ ${#mods[@]} -eq 0 ]]; then
    printf '%s' "$base"
    return
  fi
  # 去重拼接
  local out="$base" m
  for m in "${mods[@]}"; do
    [[ "$out" == *"$m"* ]] && continue
    out+=",$m"
  done
  printf '%s' "$out"
}

detect_maven_extra_args() {
  if _pom_has_profile all-natives; then
    printf '%s' '-P all-natives'
  fi
}

# 用探测结果填充空变量（调用方已声明 APP_NAME 等）
detect_fill_empty() {
  local v
  if [[ -z "${APP_NAME:-}" ]]; then
    APP_NAME="$(detect_app_name || true)"
  fi
  if [[ -z "${MAIN_CLASS:-}" ]]; then
    MAIN_CLASS="$(detect_main_class || true)"
  fi
  if [[ -z "${MAVEN_JAR:-}" ]]; then
    MAVEN_JAR="$(detect_maven_jar || true)"
  fi
  if [[ -z "${VENDOR:-}" ]]; then
    VENDOR="$(detect_vendor || true)"
  fi
  if [[ -z "${APP_DESCRIPTION:-}" ]]; then
    APP_DESCRIPTION="$(detect_description || true)"
  fi
  if [[ -z "${VERSION:-}" && -z "${VERSION_JAVA:-}" ]]; then
    v="$(detect_version_java || true)"
    if [[ -n "$v" ]]; then
      VERSION_JAVA="$v"
    fi
  fi
  if [[ -z "${VERSION:-}" && -z "${VERSION_JAVA:-}" ]]; then
    VERSION="$(detect_version || true)"
  fi
  # 图标：仅当仍是默认路径且文件不存在时替换
  if [[ "${ICON_PNG:-}" == "other/icon.png" && ! -f "$ROOT/other/icon.png" ]]; then
    v="$(detect_icon_png || true)"
    [[ -n "$v" ]] && ICON_PNG="$v"
  fi
  if [[ "${ICON_ICO:-}" == "other/icon.ico" && ! -f "$ROOT/other/icon.ico" ]]; then
    v="$(detect_icon_ico || true)"
    [[ -n "$v" ]] && ICON_ICO="$v"
  fi
  if [[ "${ICON_ICNS:-}" == "other/icon.icns" && ! -f "$ROOT/other/icon.icns" ]]; then
    v="$(detect_icon_icns || true)"
    [[ -n "$v" ]] && ICON_ICNS="$v"
  fi
  # JP_MODULES：仍是默认 Swing 串且项目有 JavaFX 时升级
  if [[ "${JP_MODULES:-}" == "java.base,java.desktop,java.datatransfer,java.prefs,jdk.charsets" ]]; then
    if _pom_has_openjfx || { [[ -f "$ROOT/src/main/java/module-info.java" ]] && grep -q 'javafx\.' "$ROOT/src/main/java/module-info.java" 2>/dev/null; }; then
      JP_MODULES="$(detect_jp_modules)"
    fi
  fi
  if [[ -z "${MAVEN_EXTRA_ARGS:-}" ]]; then
    MAVEN_EXTRA_ARGS="$(detect_maven_extra_args || true)"
  fi
}
