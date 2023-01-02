# P_NAT_Setup
**远程控制一键部署工具。**

使用系统：Windows 10 & Linux (大部分是针对Windows的)

开发目的：让零基础的用户也能通过简单的运行，进行远程连接开发者的电脑

模式：开发者 and 用户远程连接

用户端：需要放在用户端部署

开发端：只需要开发端部署

注意：

- 部分文件(如`gsudo.exe`)需要`.Net`才能运行，点此访问[下载页面(v4.7)](https://dotnet.microsoft.com/en-us/download/dotnet-framework/net47)  |  [ndp48-x86-x64-allos-enu](https://download.visualstudio.microsoft.com/download/pr/2d6bb6b2-226a-4baa-bdec-798822606ff1/8494001c276a4b96804cde7829c04d7f/ndp48-x86-x64-allos-enu.exe)。
- `conf`文件夹只能放一个配置文件
- `res`文件夹只能放文件夹

## 文件夹列表

### 通用文件

- `xxxxx_requirement.txt`——需要的文件清单
- `tool_xxxxxx`——工具，无需在客户端部署的东西
- `lib_xxxxx`——Python依赖
- `func_xxxx`——功能性脚本

### FRP

- `frpc.py`——部署frp连接。开发端，通过FRP暴露在公网等待用户连接。配合JRA、Dayon等项目。

### ndp(.Net)

- `download.Net.bat`——用于下载`.Net`版本。用户端。注意：默认下载4.6.2版本。
- `install.Net.bat`——用于安装`.Net`版本。用户端。注意：默认下载4.6.2版本。而且，有时能用有时不能用，原因未知，随缘安装。
- `check.NetVersion.bat`——用于检查`.Net`版本。用户端。

### Tight VNC

- `tightvnc_Install.bat`——安装脚本 ([配置参考](https://www.tightvnc.com/doc/win/TightVNC_2.7_for_Windows_Installing_from_MSI_Packages.pdf))
- `tightvnc_HideRegedit.py`——注册表隐藏Windows端TightVNC。用户端。注意：仅限Windows。

### ZeroTier

>Required .Net > 4.5
>
>支持配置文件加密

- `zerotier_Install.py`——部署Zero Tier。用户端/开发端，注意：仅限Windows。两边都需要加入ZeroTier网络，选配是否隐藏在卸载列表。
- `zerotier_ManuallyInstall.bat`——手动安装的脚本

### Share

- `res`——资源文件
- `conf`——配置文件
- `gsudo`——Windows提权工具 | [Github](https://github.com/gerardog/gsudo) 要求.Net > 4.0
- `wget`——[wgetwin-1_5_3_1](http://www.interlog.com/~tcharron/wgetwin-1_5_3_1-binary.zip) from http://www.interlog.com/~tcharron/wgetwin.html

### Tool

- `tool_decrypt_conf.py`——加密配置文件
- `tool_encrypt_conf.py`——加密配置文件
- `tool_Pyinstaller.bat`——用于Windows客户端的Pyinstaller打包脚本，需要`pyinstaller.txt`文件

### Libs

- `lib_regedit.py` —— 操作Windows注册表
- `lib_AES.py`——AES加密模块
- `lib_System.py`——系统功能模块
- `lib_loadConf.py`——加载配置文件的模块

### Function

- `func_runBackground.vbs`——将文件拖放到此VBS上可后台运行

## 使用方法

（TODO）

## 更新日志

- 2023.01.02——1.0.9
  - 取消原有的目录结构
  - 新增配置文件加密功能(仅部分)
- 2022.07.15——1.0.8
  - `TightVNC`新增安装位置选择
- 2022.03.07——1.0.7
  - 添加了`.Net`版本检测脚本
- 2022.03.06——1.0.6
  - 添加了后台运行的vbs脚本
- 2022.03.06——1.0.5
  - 完成了TightVNC部署脚本
- 2022.03.05——1.0.4
  - 添加了微软.Net安装脚本
- 2022.03.04——1.0.3
  - 更新了部署脚本
  - 更新目录结构
- 2022.02.28——1.0.2
  - `ZeroTier`脚本更新：支持隐藏`卸载程序`列表
- 2022.01.21——1.0.1
  - 添加TightVNC脚本(Windows Only)
- 2021.09.16——1.0.0
  - 完成了ZeroTier和FRP的部署脚本

​	