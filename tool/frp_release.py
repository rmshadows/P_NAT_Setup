#!/usr/bin/python3
# 从包根导出一份 FRP 帮助者目录（旧脚本，路径已对齐新目录）

import os
import os.path as op
import shutil
import sys

_LIB = op.abspath(op.join(op.dirname(__file__), "..", "lib"))
if _LIB not in sys.path:
    sys.path.insert(0, _LIB)
from lib_paths import ensure_import_path, res_dir

ROOT = ensure_import_path(op.dirname(op.abspath(__file__)))
os.chdir(ROOT)

CONF = "frpc.conf"
EXPORTD = "FRP_AUTO"
MAIN = [op.join("deploy", "frp", "frpc.py")]
SHARE = ["favicon.ico", "LICENSE", "README.md", op.join("tool", "runBackground.vbs")]


def addition():
    print("====>>>>Customize...")
    srcf = op.join(ROOT, "conf", "frpc")
    dstf = op.join(ROOT, EXPORTD, "conf", "frpc")
    shutil.copytree(srcf, dstf)


if __name__ == "__main__":
    print("====>>>>Check Required Files: ")
    conf_srcf = op.join(ROOT, "conf", CONF)
    print(conf_srcf)
    if not op.exists(conf_srcf):
        print("File or Directory not exist ! ")
        exit(1)
    for i in (op.join(res_dir(ROOT), "windows", "helper", "gsudo"),
              op.join(res_dir(ROOT), "windows", "frp"),
              op.join(res_dir(ROOT), "linux", "frp")):
        print(i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
    for i in MAIN + SHARE:
        i = op.join(ROOT, i)
        print(i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
    EXPORTD = op.join(ROOT, EXPORTD)
    print("====>>>>Export to :")
    print(EXPORTD)
    print("====>>>>Remove old exports...")
    if op.exists(EXPORTD):
        try:
            if op.isdir(EXPORTD):
                shutil.rmtree(EXPORTD)
            else:
                os.remove(EXPORTD)
        except Exception as e:
            print(e)
            exit(1)
    print("====>>>>Make export directory...")
    os.mkdir(EXPORTD)
    os.mkdir(op.join(EXPORTD, "conf"))
    os.mkdir(op.join(EXPORTD, "res"))
    print("====>>>>Copy conf file...")
    shutil.copyfile(conf_srcf, op.join(EXPORTD, "conf", CONF))
    print("====>>>>Copy res file...")
    shutil.copytree(op.join(res_dir(ROOT), "windows", "helper", "gsudo"),
                    op.join(EXPORTD, "res", "windows", "helper", "gsudo"))
    shutil.copytree(op.join(res_dir(ROOT), "windows", "frp"),
                    op.join(EXPORTD, "res", "windows", "frp"))
    shutil.copytree(op.join(res_dir(ROOT), "linux", "frp"),
                    op.join(EXPORTD, "res", "linux", "frp"))
    print("====>>>>Copy python file...")
    for i in MAIN:
        shutil.copyfile(op.join(ROOT, i), op.join(EXPORTD, op.basename(i)))
    print("====>>>>Copy share file...")
    for i in SHARE:
        shutil.copyfile(op.join(ROOT, i), op.join(EXPORTD, op.basename(i)))
    addition()
