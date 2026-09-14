#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
本机辅助依赖安装（转调 init/install.py，保持 deploy/ 入口统一）。

  python3 deploy/host-assist/install.py
  python3 deploy/host-assist/install.py --dry-run
"""
from __future__ import print_function

import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
INIT = os.path.join(ROOT, "init", "install.py")


def main():
    if not os.path.isfile(INIT):
        print("找不到", INIT)
        return 1
    return subprocess.call([sys.executable, INIT] + sys.argv[1:])


if __name__ == "__main__":
    sys.exit(main())
