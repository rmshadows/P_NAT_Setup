# P_NAT_Setup
我的内网穿透一键部署工具。

使用系统：Windows 10 & Linux 

开发目的：让零基础的用户也能通过单击，进行远程连接开发者的电脑

模式：开发者 and 用户远程连接

用户端：需要放在用户端连接

开发端：只需要开发端部署

## 文件列表

- `ZeroTier.py`——部署Zero Tier。用户端/开发端，两边都需要加入ZeroTier网络。
- `frpc.py`——部署frp连接。开发端，通过FRP暴露在公网等待用户连接。配合JRA、Dayon等项目。
- `TightVNCServer.py`——部署Windows端TightVNC。用户端，配合JRA、Dayon项目。注意：仅限Windows。(未完成)

## 更新日志

- 2022.01.21——1.0.1
  - 添加TightVNC脚本(Windows Only)
- 2021.09.16——1.0.0
  - 完成了ZeroTier和FRP的部署脚本

​	