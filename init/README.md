# 初始化说明
#
# Linux 一键装 apt + pip（需 sudo）：
#   python3 init/install.py
#   python3 deploy/host-assist/install.py   # 同上
#
# 清单：
#   init/requirements-apt.txt   — onboard xdotool wmctrl python3-xlib …
#   init/requirements-pip.txt   — pycryptodome / python-xlib 兜底
#
# 热键抓取（避免系统抢走 Super+D）：
#   python3 tool/capture_hotkey.py
