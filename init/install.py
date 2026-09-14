#!/usr/bin/python3
# -*- coding: utf-8 -*-
"""
初始化本机依赖（apt + pip）。

用法（在包根）：
  python3 init/install.py
  python3 init/install.py --dry-run
  python3 init/install.py --apt-only
  python3 init/install.py --pip-only

与 deploy/host-assist/install.py 同一套清单（init/requirements-*.txt）。
"""
from __future__ import print_function

import argparse
import os
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APT_LIST = os.path.join(ROOT, "init", "requirements-apt.txt")
PIP_LIST = os.path.join(ROOT, "init", "requirements-pip.txt")


def read_list(path):
    items = []
    if not os.path.isfile(path):
        return items
    with open(path, "r", encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            items.append(line)
    return items


def run(cmd, dry):
    print("+", " ".join(cmd))
    if dry:
        return 0
    return subprocess.call(cmd)


def apt_install(pkgs, dry):
    if not pkgs:
        print("apt：无包可装")
        return 0
    if shutil.which("apt-get") is None:
        print("未找到 apt-get，跳过 apt（非 Debian/Ubuntu？）")
        return 0
    missing = []
    for p in pkgs:
        try:
            out = subprocess.check_output(
                ["dpkg-query", "-W", "-f=${Status}", p], stderr=subprocess.DEVNULL
            ).decode("utf-8", "replace")
            if "install ok installed" in out:
                print("已安装:", p)
            else:
                missing.append(p)
        except Exception:
            missing.append(p)
    if not missing:
        print("apt：全部已装")
        return 0
    print("将安装 apt 包:", ", ".join(missing))
    code = run(["sudo", "apt-get", "update", "-y"], dry)
    if code != 0 and not dry:
        return code
    return run(["sudo", "apt-get", "install", "-y"] + missing, dry)


def pip_install(pkgs, dry):
    if not pkgs:
        print("pip：无包可装")
        return 0
    # 已能 import 的跳过
    skip_map = {
        "pycryptodome": "Crypto.Cipher.AES",
        "python-xlib": "Xlib",
    }
    need = []
    for p in pkgs:
        mod = skip_map.get(p, p.replace("-", "_"))
        try:
            __import__(mod.split(".")[0] if "." in mod else mod)
            # pycryptodome -> Crypto
            if p == "pycryptodome":
                __import__("Crypto.Cipher.AES")
            elif p == "python-xlib":
                __import__("Xlib")
            print("已可用:", p)
        except Exception:
            need.append(p)
    if not need:
        print("pip：全部已可用")
        return 0
    pip = None
    for cand in ("pip3", "pip"):
        if shutil.which(cand):
            pip = cand
            break
    if pip is None:
        print("未找到 pip3。可先: sudo apt-get install -y python3-pip")
        return 1
    print("将 pip 安装:", ", ".join(need))
    # Debian 常需 --break-system-packages 或用户目录
    cmd = [pip, "install", "--user"] + need
    code = run(cmd, dry)
    if code != 0 and not dry:
        print("重试带 --break-system-packages …")
        code = run([pip, "install", "--user", "--break-system-packages"] + need, dry)
    return code


def main():
    ap = argparse.ArgumentParser(description="P_NAT_SETUP 依赖初始化")
    ap.add_argument("--dry-run", action="store_true", help="只打印命令")
    ap.add_argument("--apt-only", action="store_true")
    ap.add_argument("--pip-only", action="store_true")
    args = ap.parse_args()

    os.chdir(ROOT)
    print("包根:", ROOT)
    code = 0
    if not args.pip_only:
        code = apt_install(read_list(APT_LIST), args.dry_run) or code
    if not args.apt_only:
        code = pip_install(read_list(PIP_LIST), args.dry_run) or code
    if code == 0:
        print("初始化完成。热键抓取可试: python3 tool/capture_hotkey.py")
    else:
        print("初始化有错误，exit={}".format(code))
    return code


if __name__ == "__main__":
    sys.exit(main())
