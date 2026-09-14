# P_NAT_SETUP

远程协助一键部署：**小白主动连出，帮助者等待。**  
JRA 是跨平台 GUI（按键辅助）；安装 / 启停 / 卸载都走仓库里的 `deploy/` 脚本，开发者也可以不打开 GUI、直接跑脚本。

- 小白：打开 JRA，**左键**「加载远程桌面」
- 帮助者 / 开发者：同一套 GUI，或直接跑下面的脚本
- 运行时只写 `.runtime/`（可用 `PNAT_RUNTIME` 改），不写桌面、不碰用户家目录

Windows 10+、Linux。部分 helper（如 `gsudo`）需要 [.NET Framework 4.7+](https://dotnet.microsoft.com/en-us/download/dotnet-framework/net47)。

---

## 开发交接规则

交接或改 JRA / deploy 时按这个来，少踩坑。

1. **尽量不引入外来依赖。** 默认只靠 JDK / JavaFX / 本仓库脚本。不要为了方便再加 JNA、第三方热键库、必须装的系统包。`xdotool` / `wmctrl` / `onboard` / `python3-xlib` 都是**可选**，须在长按菜单或 `init/install.py` 里写明，默认路径不能假定它们存在。
2. **切桌面跟原版 / 上一版能用的做法，别再发明第三条路。**
   - **Windows（原版 JRA）**：`Ctrl+Win+方向`（或 D 新建），然后走 `showFront()`。
   - **`showFront()` = 出现在当前桌面**（托盘点击也必须这样）：Windows 用 hide/show；Linux X11 先迁窗再 hide/show；Wayland / 迁不了就新建 Stage。不要只 `toFront()`，窗还在旧工作区等于没显示。
   - **Linux X11**：`xdotool`/`wmctrl` 按编号切并迁窗（上一版能用的）。Wayland 上这两样对真工作区无效，**禁止当主路径**，否则等于炸。
   - **Linux Wayland / 没有 xdotool**：与原版 Windows 相同——JavaFX `Robot` 发 `Ctrl+Alt+方向` + `hide/show`。不引入 ydotool/JNA。缺工具只降级，不准抛死。
3. **xdotool 不是 Linux 唯一出路，也不是 Wayland 出路。** 先看 `XDG_SESSION_TYPE`。X11 有工具才走编号；否则热键。不要为 Wayland 再加必须装的输入注入守护进程。
4. **JavaFX Robot / 改 Stage 必须在 JavaFX 应用线程。** 后台线程只负责 `sleep` 和排队 `Platform.runLater`。在 `jra-*-switch` 这类线程里直接 `robot.keyPress` 会抛 `event thread only`。
5. **改 Windows 别炸 Linux，改 Linux 别炸 Windows。** 分支用 `Tools.isWindows()` 隔开。Linux 的 xdotool 编号切换不要套到 Windows；Windows 的 PowerShell / 虚拟桌面脚本不要套到 Linux。
6. **一份 conf、一份 res。** GUI 与 CLI 共用包根 `conf/`、`res/`、`deploy/`。运行时只写 `.runtime/`。不要在 `JavaRemoteAssistant/` 里再拷一份资源。
7. **Windows 批处理：** 纯 ASCII + CRLF；不要用 `%PNAT_ROOT%`（`%P` 会被拆掉）。Java 可读环境变量 `PNAT_ROOT` / `PNAT_CONF` / `PNAT_RES`。
8. **先本机再远程。** 热键、跟随、托盘先在本机 DE/Win 上点到能用，再谈 Dayon/TightVNC。
9. **关窗默认缩到托盘**（`close_to_tray=1`）。JRA设置勾选「关闭窗口时退出」才真正退出。Windows 切桌面：发热键后等 DWM 再 hide/show。到头桌面 toast，不要再切。

---

## 使用入口（别懵）

| 谁点 | 入口 | 干什么 |
|------|------|--------|
| 小白 | 左键「加载远程桌面」 | 按 `desktop=` 普通启动；**体验版**走完整界面状态（不真装） |
| 小白 | 「停止远程桌面」 | 停当前引擎；体验版模拟停止 |
| 帮助者/开发者 | **JRA设置** | 远程桌面方式、部署属性、体验版、托盘 |
| 开发者 | CLI 脚本 | 与 GUI 同一套 `deploy/` |
| 任何人 | `python3 tool/dry_run.py` | CLI 预演文案；GUI 体验版以界面为准 |

**体验版（dry-run）**：在「JRA设置」勾选，或 `conf/app.conf` 写 `dry_run=1`。回到主界面后加载/停止/高级会像正式版一样切状态徽章与按钮，不启真实进程；本机辅助与修饰键也只改界面、不真按本机键。`simulate_os=windows` 时可在 Linux 上体验 TightVNC 布局并显示 AHK。本机 IP 下拉列出全部网卡的 IPv4/IPv6（标注网卡名），默认仍猜主 IPv4 并记忆；`client_ip` 同步 frpc（仅 IPv4）。各辅助按钮延时写入 `delay_*`。AHK：默认 `res/windows/AHK/`（包根共用）；`ahk_dir` / `ahk_exe` / `ahk_script` 可覆盖。主界面左键下拉选脚本，右键或设置里可换目录。

```bash
# 看本机按 conf 会跑什么
python3 tool/dry_run.py

# 模拟 Windows 普通加载 / 高级装服务
python3 tool/dry_run.py --os windows --desktop tightvnc --action load
python3 tool/dry_run.py --os windows --desktop tightvnc --action advanced

# 模拟 Linux Dayon
python3 tool/dry_run.py --os linux --desktop dayon --action load
python3 tool/dry_run.py --os linux --desktop dayon --action stop

# 一张总表（各平台×引擎×动作 + 入口说明）
python3 tool/dry_run.py --all
```

---

## 使用方法

开发者在仓库根目录：

```bash
# 首次：装 Linux 本机辅助依赖（onboard / xdotool / python3-xlib 等）
python3 init/install.py
# 同上入口
python3 deploy/host-assist/install.py

# 模拟（推荐先跑）
python3 tool/dry_run.py --all

# Linux Dayon（小白 / 帮助者）
python3 deploy/dayon/install.py
python3 deploy/dayon/install.py assistant
python3 deploy/dayon/install.py --keep
python3 deploy/dayon/install.py --stop
python3 deploy/dayon/install.py --uninstall

# 配置加解密
python3 tool/encrypt_conf.py conf/zerotier.conf conf/zerotier.eonf
python3 tool/decrypt_conf.py conf/zerotier.eonf conf/zerotier.conf
```

Windows 脚本从包根解析路径，可直接双击 `deploy\tightvnc\install.bat`。

打包 JRA（在 `JavaRemoteAssistant/` 下，需 JDK + Maven + jpackage）：

```bash
./pack/pack.sh          # Linux：JAR + 绿色目录 + .deb
./pack/pack.sh jar
# Windows：pack\pack.bat
```

产物在 `JavaRemoteAssistant/dist/`。

---

## JRA 按钮


界面在 `JavaRemoteAssistant/`。按钮本身不写安装逻辑，只触发脚本或本机按键。

| 按钮 | 操作 | 做什么 | 调用 |
|------|------|--------|------|
| **加载远程桌面** | **左键（普通）** | 预检通过后启动桌面层，小白无感接入 | **Windows**：不跑 `deploy/`，直接用便携端 `res/windows/TightVNC/RAServer/start_server.exe`（`-reinstall` / `-start` / `-controlservice -connect`），地址来自 `conf/app.conf` 的 `host:port`。**Linux**：`python3 deploy/dayon/install.py`（角色也读 `app.conf`） |
| **加载远程桌面** | **右键（高级）** | 同「高级部署…」按钮。仅 Windows | `deploy/tightvnc/install.bat`（有 `gsudo` 则用它提权） |
| **停止远程桌面** | 单击（仅运行中可点） | 停掉本次桌面层；旁白「未运行 / 启动中 / 运行中 / 停止中」每 2 秒探测进程 | **Windows**：`RAServer/start_server.exe` 或 `tvnserver.exe`。**Linux**：`python3 deploy/dayon/install.py --stop` |
| 置顶 | 勾选 | 窗口是否始终在最前（默认开） | 无脚本；`conf/app.conf` 可写 `always_on_top=0` 改默认 |
| 系统监视器 / 任务管理器 | 单击；**本按钮**滚轮调秒 | 倒计时后打开监视器（默认 0s） | **Windows**：`Ctrl+Shift+Esc`。**Linux**：自动匹配监视器程序 |
| 显示桌面 | 单击；本按钮滚轮 | 默认 0s | **Windows**：`Win+D`。**Linux**：`wmctrl` / `Super+D` |
| 切换工作区 | 左 / 右 / 中键；本按钮滚轮 | 默认 0s | **Windows**：`Ctrl+Win+方向/D`。**Linux**：`Ctrl+Alt+方向` / `Super` |
| 切换输入法 | 单击；本按钮滚轮 | 默认 2s | `Ctrl+Space` |
| CTRL / SHIFT / ALT / WIN·Super | 单击切换；「松开全部」可强制清 | 在被控端按住修饰键 | JavaFX Robot；松开时会抑制「松了又按回」 |
| AHK 脚本 | 左键选脚本；右键换目录；滚轮调延时 | 仅 Windows（或体验·模拟 Windows），默认 5s | `res/windows/AHK/` |
| 屏幕键盘 | 单击开/关；本按钮滚轮 | 默认 0s | **Windows**：`Ctrl+Win+O`。**Linux**：`onboard` D-Bus 切换（需 `init/install.py`） |

各辅助按钮**独立延时**（0–15 秒，滚轮只改当前按钮，0=立即执行），写入 `conf/app.conf` 的 `delay_*`，下次启动沿用。

| **JRA设置** | 单击 | 方式 / 部署属性 / 体验版 / 托盘 | 读写 `conf/app.conf` + TightVNC 时写 `tightvnc.conf`；保存后回主界面即生效 |
| 关于 | 单击 | 说明与版本 | 无脚本 |

预检失败（缺包根、缺 `python3`、缺 `start_server.exe` / `dayon.sh`、无 `DISPLAY` 等）**不会继续执行**，弹中文原因。脚本失败会再试一次。

### Linux 本机辅助：哪些比较通用

| 动作 | 匹配方式 | 通用程度 |
|------|----------|----------|
| 系统监视器 | 读 `XDG_CURRENT_DESKTOP` 优先选对应程序，再扫 PATH / `.desktop` | **高**（装了监视器就能开；不依赖固定快捷键） |
| 显示桌面 | `wmctrl -k on` → `Super+D` / `Ctrl+Alt+D` | 中高（有 `wmctrl` 最稳；快捷键视 DE） |
| 切换工作区 | `Ctrl+Alt+←/→` → GNOME `Super+Page` | 中（XFCE/MATE/Cinnamon 常默认；纯 GNOME 可能要改键） |
| 切换输入法 | `Ctrl+Space` | 中高（fcitx/ibus 常见；少数是 Ctrl+Shift） |
| 屏幕键盘 | `onboard` / `florence` → GNOME 无障碍 | 中（未安装会提示） |
| 按住 Ctrl/Shift/Alt/Super | Robot 本机按住 | **高**（不依赖 DE 快捷键） |
| AHK | — | 仅 Windows |

监视器自动顺序示例：KDE→`plasma-systemmonitor`；GNOME→`gnome-system-monitor`；XFCE→`xfce4-taskmanager`；MATE/Cinnamon/Deepin/LXQt 各用自家，找不到再扫其它。

---

## 高级部署能做什么

只对 **Windows TightVNC**。Linux 右键会提示：用左键普通部署即可（Dayon 没有「装成系统服务」这一档）。

右键「加载远程桌面」或主界面 **「高级部署…」** → 确认对话框（列出设置里的密码是否已设、托盘、安装路径）→ 调用：

```text
deploy/tightvnc/install.bat
```

配置只读这一份，GUI **不另做密码编辑器**：

```text
conf/tightvnc.conf
```

| 项 | 配置键 | 实际效果 |
|----|--------|----------|
| 装成 Windows 服务 | 脚本写死 `SERVER_REGISTER_AS_SERVICE=1` | 开机可随服务起来，不是便携进程 |
| 控制面板密码 | `ctrl_passwd` | TightVNC 控制接口密码 |
| 连接密码 | `vnc_passwd` | 建立 VNC 连接的密码 |
| 只看密码 | `viewonly_passwd` | 只能看、不能控 |
| 托盘 | `hide_systray=1` 隐藏，`0` 显示 | `1` 时 `SET_RUNCONTROLINTERFACE=0` |
| 安装目录 | `install_to`（可空） | 空则 MSI 默认路径；现默认示例是 `C:\Windows\` |
| 防火墙例外 | 脚本写死 | Server / Viewer 各加一条 |
| 允许 Ctrl+Alt+Del | 脚本写死 `SERVER_ALLOW_SAS=1` | 远程可发安全注意序列 |

资源：`res/windows/TightVNC/tightvnc.msi`。需要管理员（UAC）。有 `res/windows/helper/gsudo/gsudo.exe` 时 GUI 用 gsudo 等提升后的进程结束；没有则脚本自己弹 UAC，可能先返回「脚本已结束」，以管理员窗口为准。

**和左键的区别**：左键只用便携 `RAServer`，不装 MSI、不写系统服务、不读 `tightvnc.conf` 的密码/托盘。右键才是「脚本那么深」的那一档。

同目录还有 `deploy/tightvnc/HideRegedit.py`：从「程序和功能」里隐藏 TightVNC 卸载项。GUI **不会**自动调用，需要时手动：

```bat
python deploy\tightvnc\HideRegedit.py
```

---

## 直接跑脚本（与 GUI 等价）

都在**包根**执行（有 `conf/` 且有 `deploy/` 或 `res/`）。也可设 `PNAT_ROOT` / `PNAT_RES` / `PNAT_RUNTIME`。

### 桌面层

```bash
# Linux Dayon（JRA 左键「加载」= 不带参数的这一条）
python3 deploy/dayon/install.py
python3 deploy/dayon/install.py assistant
python3 deploy/dayon/install.py --keep
python3 deploy/dayon/install.py --stop          # JRA「停止远程桌面」
python3 deploy/dayon/install.py --uninstall
```

```bat
REM Windows TightVNC 高级（JRA 右键「加载」）
deploy\tightvnc\install.bat
```

Windows 普通档没有单独的 `.bat`：就是 `res\windows\TightVNC\RAServer\start_server.exe`。

### 网络层（GUI 按钮尚未接，只给命令行）

```bash
python3 deploy/zerotier/install.py
# Windows 也可：deploy\zerotier\ManuallyInstall.bat
python3 deploy/frp/frpc.py
```

### 其它

```bat
deploy\ndp\CheckVersion.bat
deploy\ndp\Download.bat
deploy\ndp\Install.bat
python3 tool\encrypt_conf.py conf\zerotier.conf conf\zerotier.eonf
python3 tool\decrypt_conf.py conf\zerotier.eonf conf\zerotier.conf
```

---

## 配置

| 文件 | 谁读 | 内容 |
|------|------|------|
| `conf/app.conf` | JRA + Dayon | `role`（assisted/assistant）、`host`、`port`、`desktop`、`network` |
| `conf/tightvnc.conf` | 仅 `install.bat` | 密码、托盘、安装路径 |
| `conf/zerotier.conf` / `.eonf` | ZeroTier 脚本 | 网络 ID 等；小白包建议只带 `.eonf` |
| `conf/frpc.conf` + `conf/frpc/*.ini` | FRP 脚本 | 选哪条隧道 |

等号两边不要空格。

---

## 目录

```text
JavaRemoteAssistant/   GUI 源码与 pack/（不含 VNC/Dayon/AHK 安装包）
deploy/dayon/          install.py
deploy/tightvnc/       install.bat、HideRegedit.py
deploy/zerotier/       install.py、ManuallyInstall.bat
deploy/frp/            frpc.py
deploy/ndp/            CheckVersion / Download / Install.bat
lib/                   路径、读 conf、AES、注册表
tool/                  加解密、打包辅助
conf/                  共用配置
res/linux|windows/     按平台放资源；打出去的包只带本平台
                       TightVNC、Dayon、AHK、helper、JRA 启动器等一律在此
                       （已废弃 JavaRemoteAssistant/other，勿再往 GUI 子目录塞资源）
release/               发行切片清单
.runtime/              仅运行时，gitignore
```

| 变量 | 含义 | zip | deb |
|------|------|-----|-----|
| `PNAT_ROOT` | 包根（JRA、deploy、conf） | 解压目录 | `/opt/JavaRemoteAssistant` |
| `PNAT_RES` | 资源 | `$ROOT/res` | 同左 |
| `PNAT_RUNTIME` | 唯一可写 | `$ROOT/.runtime` | `~/.cache/JavaRemoteAssistant` |

查找 ROOT：`PNAT_ROOT` → 往上找同时有 `conf/` + (`deploy/` 或 `res/`) → 当前目录。

Windows 资源示意：

```text
res/windows/AHK/                 AHK.exe + *.ahk
res/windows/TightVNC/RAServer/   便携端
res/windows/TightVNC/for_windows7/  dfmirage（可选）
res/windows/Dayon/
res/windows/helper/gsudo/
res/windows/JRA/                 Launcher / CLI（可选）
res/windows/legacy/              旧 32bit 归档等
```
打包 GUI（在 `JavaRemoteAssistant/`，需 JDK + Maven + jpackage）：

```bash
./pack/pack.sh          # Linux：JAR + 绿色目录 + .deb
./pack/pack.sh jar
# Windows：pack\pack.bat
```

产物在 `JavaRemoteAssistant/dist/`。完整发行再用 `release/*.list` 把 `deploy` + `conf` + 本平台 `res` 拷进去。

---

## 开发备忘

JRA 不重写安装逻辑。先整合现有模块，再加新通路（局域网联调、角色产品化、Windows Dayon 一键包等先按下）。

- [x] **Windows 切桌面 toast**：已实机确认。编号、到头提示、独立气泡、不挡按钮。
- [x] **远程桌面方式 + 部署属性**：JRA设置可选方式（auto/dayon/tightvnc/rdp）并按方式配置 role/host/port、TightVNC portable|service 与密码等；加载/高级吃同一份 conf。窗口可缩放；内容过长窗内滚动（滚动条空闲隐藏）。
- [ ] 脚本统一动词和退出码（start / stop / keep / uninstall）
- [ ] 运行时只写 `.runtime/<模块>/`；卸载不碰用户自己的目录
- [ ] JRA 按钮接到 ZeroTier / FRP（现在只有命令行）
- [ ] 打包：pack 打 GUI；完整包用 `release/*.list`
- [ ] 脚本单独跑与 JRA 调用结果一致（验收）

---

## 更新日志

### 2026-09-09（远程桌面 UI + 收工）

JRA设置拆成「远程桌面方式」与「部署属性」；主界面一键加载 / 高级部署共用 conf。

- **方式**：`desktop=auto|dayon|tightvnc|rdp`（auto：Windows→TightVNC，Linux→Dayon）。RDP / Windows Dayon 真部署仍禁止加载并提示。
- **部署属性**：角色、host/port、monitor、Dayon `auto_accept`；TightVNC `tightvnc_mode=portable|service` 与 `conf/tightvnc.conf` 密码/托盘/安装路径可在设置里改（`TightVncConf`；读写容错编码，保存写干净 UTF-8）。
- **加载**：portable 便携连出（地址默认设置里的 host:port，确认后写回）；service 模式提示改用高级部署。
- **高级部署**：确认框列出当前 TightVNC 属性；Dayon 明确无服务档。
- **窗口**：可缩放；高度钳在屏幕可视区；主界面/设置用滚动；**滚动条空闲隐藏**，滚动/悬停/拖动才出现。
- **其它同日**：Windows 切桌面编号 toast、跟窗记坐标、toast 不挡按钮、托盘中文菜单、拖窗不跳。

### 2026-09-09（切桌面）

Windows 切桌面 toast（只改 Windows，Linux 编号切换不动）。

- **编号**：Java 直接 `reg.exe` 读 `HKCU\...\Explorer\VirtualDesktops`（`CurrentVirtualDesktop` + `VirtualDesktopIDs`），失败再试 `SessionInfo`，最后才回退 `win_follow_desktop.ps1 -QueryPos`。不再把提示绑在 PowerShell 冷启动上。
- **提示**：Windows toast 不抢鼠标；跟窗后记窗口坐标；托盘中文用 Swing 菜单。
- **到头**：能读到编号则切之前就判断，不再发 `Ctrl+Win`；编号失败则对比桌面 GUID，至少提示已经是第一个/最后一个（可无 N/M）。

### 2026-09-06

切工作区 / 托盘 / 关窗（JRA）。Linux 本机（GNOME X11）验证过的留下；Windows 编号提示当时未完成。

- **切桌面分平台**：Windows 走原版 `Ctrl+Win` + 等 DWM 再 `hide/show`（修第一次点没切过去）。Linux X11 用 `xdotool`/`wmctrl` 按编号切并迁窗；Wayland 不走 xdotool，降级为热键。
- **`showFront()`**：目标是出现在当前桌面。托盘点击也走这里。
- **关窗默认缩到托盘**（`close_to_tray=1`）。JRA设置可改为打叉即退出。GNOME 托盘报错不再卸图标，避免开/关几次后进程退出。
- **到头 / 第几个桌面**：Linux 已 toast「当前第 N / M」「已经是第一个/最后一个（1/M）」。Windows 见 2026-09-09。
- **不要**在热路径上反复重建 Stage（GNOME `MetaWindowActorX11` allocation 刷屏，宿主机曾卡死）。测 Windows 时在 KVM/SPICE 里，卡死先 `Ctrl+Alt` 放抓键。
- 交接规则写在本文「开发交接规则」。`tool/linux_follow_desktop.sh`、`tool/win_follow_desktop.ps1`（含 `-QueryPos`）已在仓库。
