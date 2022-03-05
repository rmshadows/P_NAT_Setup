# P_NAT_Setup
我的内网穿透一键部署工具。

使用系统：Windows 10 & Linux 

开发目的：让零基础的用户也能通过简单的运行，进行远程连接开发者的电脑

模式：开发者 and 用户远程连接

用户端：需要放在用户端连接

开发端：只需要开发端部署

注意：

- 部分文件(如`gsudo.exe`)需要`.Net`才能运行，点此访问[下载页面(v4.7)](https://dotnet.microsoft.com/en-us/download/dotnet-framework/net47)  |  [ndp48-x86-x64-allos-enu](https://download.visualstudio.microsoft.com/download/pr/2d6bb6b2-226a-4baa-bdec-798822606ff1/8494001c276a4b96804cde7829c04d7f/ndp48-x86-x64-allos-enu.exe)。
- `conf`文件夹只能放一个配置文件
- `res`文件夹只能放文件夹

## 文件夹列表

### FRP

- `frpc.py`——部署frp连接。开发端，通过FRP暴露在公网等待用户连接。配合JRA、Dayon等项目。

### ndp(.Net)

- `download.Net.bat`——用于下载`.Net`版本
- `install.Net.bat`——用于安装`.Net`版本

### Tight VNC

- `TightVNCServer.py`——部署Windows端TightVNC。用户端，配合JRA、Dayon项目。注意：仅限Windows。(未完成)
- `InstallTightVNC.bat`——安装脚本 ([配置参考](https://www.tightvnc.com/doc/win/TightVNC_2.7_for_Windows_Installing_from_MSI_Packages.pdf))

### ZeroTier

>Required .Net > 4.5

- `ZeroTier.py`——部署Zero Tier。用户端/开发端，两边都需要加入ZeroTier网络，选配是否隐藏在卸载列表。
- `PyinstallerZeroTier.bat`——使用`pyinstaller`生成`exe`文件

### Share

- `regedit.py` —— 操作Windows注册表
- res
  - `gsudo`——Windows提权工具 | [Github](https://github.com/gerardog/gsudo) 要求.Net > 4.0
  - `wget`——[wgetwin-1_5_3_1](http://www.interlog.com/~tcharron/wgetwin-1_5_3_1-binary.zip) from http://www.interlog.com/~tcharron/wgetwin.html

## 使用方法

每个文件夹都是一个基本单位。比如需要配置ZeroTier，那就进入`ZeroTier`文件夹，运行文件夹中的脚本即可(会自动吧需要的文件复制到一块)。

## 更新日志

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