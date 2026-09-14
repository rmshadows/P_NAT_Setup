# 通用 Java 应用打包（jpackage）

把**整个本文件夹**拷到任意 **Maven + JDK（带 jpackage）** 项目里即可。文件夹叫 `pack`、`pack2` 都行：脚本把**上一级目录当成项目根**。

本仓库已配好：`JavaRemoteAssistant/pack/app.conf`。在 `JavaRemoteAssistant/` 下执行：

```bash
./pack/pack.sh          # Linux：JAR + 绿色目录 + .deb
./pack/pack.sh jar      # 只打 JAR
```

产物在 `JavaRemoteAssistant/dist/`（已 gitignore）。deb 包名 `javaremoteassistant`，安装目录 **`/opt/JavaRemoteAssistant`**。

## 最快上手

```bash
./pack/init-app-conf.sh    # 扫描 pom / 源码，生成 app.conf
# 看一眼必填三项对不对，再：
./pack/pack.sh
```

Windows：

```bat
pack\init-app-conf.bat
pack\pack.bat
```

需要：`java`、`mvn` 在 PATH。绿色目录 / 安装包还要 `jpackage`（JDK 自带）。产物在项目根 `dist/`。

## 必填 vs 可选

| 项 | 必填？ | 自动探测来源 |
|----|--------|--------------|
| **`APP_NAME`** | **是** | pom `<name>` → `<artifactId>` |
| **`MAIN_CLASS`** | **是** | pom `main.class` / `<mainClass>` → 源码里 `public static void main`（优先 JavaFX `Application`） |
| **`MAVEN_JAR`** | **是** | `project.build.finalName` → `target/<artifactId>-<version>.jar` |
| `VERSION` | 要有版本 | 手写 / `VERSION_JAVA` / pom `<version>` |
| `VENDOR` | 否 | pom `<groupId>`，否则用 `APP_NAME` |
| `APP_DESCRIPTION` | 否 | pom `<description>` |
| `LINUX_PKG_NAME` | 否 | `APP_NAME` 转小写 |
| `ICON_*` | 否 | 常见路径 / `find …/icon.png` |
| `JP_MODULES` | 否* | 有 `org.openjfx` 或 `module-info` 含 javafx 时自动加上 `javafx.*` |
| `MAVEN_EXTRA_ARGS` | 否 | pom 有 `all-natives` profile → `-P all-natives` |
| `JAVAFX_JMODS` / `JAVA_OPTIONS` / `MENU_GROUP` | 否 | 默认值 |

\* JavaFX 应用最终 `JP_MODULES` **必须**含 `javafx.*`（只靠 fat JAR 会报「缺少 JavaFX 运行时组件」）。自动探测一般能补上，仍建议生成后核对。

`app.conf` 里某项**留空**时，打包前还会再探测一次补全。三项都探测失败才会报错。

## 一键打包

**Linux / macOS：**

```bash
./pack/pack.sh          # 当前平台能打的全部
./pack/pack.sh jar      # 只打跨平台 JAR
./pack/pack.sh linux    # Linux：绿色目录 + .deb
./pack/pack.sh mac      # macOS：绿色目录 + .dmg
./pack/pack.sh win      # Windows Git Bash：绿色目录 + .exe
```

**Windows（cmd）：**

```bat
pack\pack.bat           # JAR + 绿色目录 + .exe（自动探测 WiX 3/5，双有则询问）
pack\pack.bat jar       # 只打跨平台 JAR
pack\pack.bat 3         # 强制 WiX 3
pack\pack.bat 5         # 强制 WiX 4/5
```

也可：`set WIX_VER=5` 再跑 `pack.bat`。备用直入口：`pack-win.bat`（仅 WiX 3）、`pack-win-wix5.bat`（仅 WiX 4/5）。

| 你在哪台机器上跑 | 额外安装包 |
|------------------|------------|
| Linux | `.deb` |
| macOS | `.dmg` |
| Windows（`pack.bat`） | `.exe`（WiX 3 或 4/5） |

**不能交叉编译原生包。** Windows `.exe` 要 WiX；JDK 24+ 才能用 WiX 4/5。

## JavaFX

1. `JP_MODULES` 含 `javafx.base,javafx.graphics,javafx.controls,...`（init / 打包会尽量自动加）
2. 脚本把当前平台 `org.openjfx` 模块 JAR 加进 `jpackage --module-path`
3. 三平台本地库：pom 有 `all-natives` 时 init 会写 `MAVEN_EXTRA_ARGS=-P all-natives`

## app.conf 全表

等号两边不要空格。路径相对**项目根**。

| 项 | 说明 |
|----|------|
| `APP_NAME` | 程序名，不要空格 |
| `MAIN_CLASS` | 主类，如 `com.example.Main` |
| `MAVEN_JAR` | `mvn package` 产出，如 `target/myapp.jar` |
| `VERSION` / `VERSION_JAVA` | 见上表 |
| `VENDOR` / `APP_DESCRIPTION` | 显示用 |
| `LINUX_PKG_NAME` | deb 包名 |
| `ICON_PNG` / `ICON_ICO` / `ICON_ICNS` | 可选图标 |
| `JP_MODULES` | `jpackage --add-modules` |
| `JAVAFX_JMODS` | 本地 JavaFX 目录；空则 Maven 复制 |
| `MAVEN_EXTRA_ARGS` | 传给 `mvn package` |
| `JAVA_OPTIONS` | 一条 `--java-options` |
| `MENU_GROUP` | Linux 菜单分组，默认 `Utility` |

## 分项脚本

| 文件 | 产物 |
|------|------|
| `init-app-conf.sh` / `.bat` | 生成 `app.conf` |
| `pack-jar.sh` / `.bat` | `dist/<APP_NAME>_<版本>.jar` |
| `pack-appimage.sh` / `.bat` | jpackage 绿色目录 + tar.gz / zip（**不是** Linux `.AppImage`） |
| `pack-deb.sh` | `.deb`（原版 + `.compat.deb`） |
| `pack-mac.sh` | `.dmg` |
| `pack-win-choose.bat` | **统一入口**：探测 WiX 3 / 4-5，双有则让用户选 |
| `pack-win.bat` | `.exe`（WiX 3，备用） |
| `pack-win-wix5.bat` | `.exe`（WiX 4/5，备用） |
| `pack.sh` / `pack.bat` | 按平台串起来（Windows `.exe` 走 choose） |

绿色包里两个启动器：日常 `<App>`，调试 `<App>-console`（终端看日志）。

## Windows WiX

`pack.bat` / `pack-win-choose.bat` 会扫描：

- **WiX 3**：`candle.exe`（PATH、`%WIX%\bin`、常见安装目录）
- **WiX 4/5**：`wix.exe`（PATH、`%USERPROFILE%\.dotnet\tools`、常见安装目录）

| 情况 | 行为 |
|------|------|
| 只装了一种 | 直接用那种 |
| 两种都有 | 交互选择；回车则按 JDK：≥24 用 5，否则用 3 |
| 命令行指定 | `pack.bat 3` / `pack.bat 5` / `set WIX_VER=5` |
| 备用直入口 | `pack-win.bat`、`pack-win-wix5.bat` |

| 工具 | WiX | JDK |
|------|-----|-----|
| WiX 3 路径 | 3（`candle.exe`） | 17+ |
| WiX 4/5 路径 | 4/5（`wix.exe`） | 24+ |

```bat
dotnet tool install --global wix
wix extension add -g WixToolset.Util.wixext
wix extension add -g WixToolset.UI.wixext
```

缺扩展时 `wix` 常 exit **144**。`.bat` 保持 CRLF（`.gitattributes` 已指定）。

## Windows 常见坑（已在脚本里处理）

| 现象 | 原因 | 脚本做法 |
|------|------|----------|
| `VERSION` 变成 `;=`，产物名 `App_;=.jar` | cmd 用 `set "V=%V:"=%"` 剥引号会坏掉 | 用 `delims="` 取引号内版本号 |
| `错误：选项 [xxx] 无效`（厂商名当选项） | `--app-version` 含 `;` 时 cmd 切断命令行 | 先校验版本格式再调 jpackage |
| PNG 当 Windows 图标直接失败 | jpackage Windows 只要真正的 `.ico` | 仅 `.ico` 才传 `--icon`（`WIN_ICON`） |
| `mvn` 跑不起来 | Conda 的无扩展名 `mvn` 是 bash 脚本 | 优先用 `mvn.cmd` / `mvn.bat` |

`ICON_ICO` 请指向真实 `.ico` 文件；把 PNG 改后缀不够。

## 还没有的

- Linux 单文件 `.AppImage`、Windows `.msi`、macOS 签名 / 公证、rpm / 跨架构
