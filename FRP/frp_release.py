#!/usr/bin/python3

import os
import os.path as op
import shutil

# 配置文件
CONF = "frpc.conf"
# 资源文件夹名
RESD = "frp"
# 导出文件夹
EXPORTD = "FRP_AUTO"
# 专属文件名
MAIN = ["frpc.py"]
# 共享文件名
SHARE = ["favicon.ico", "LICENSE", "README.md"]
# 是否需要GSUDO
GSUDO = True


if __name__ == '__main__':
    print("====>>>>Check Required Files: ")
    # 路径化
    CONF = op.join(".", "..", "conf", CONF)
    print(CONF)
    if not op.exists(str(CONF)):
        print("File or Directory not exist ! ")
        exit(1)
    RESD = op.join(".", "..", "res", RESD)
    print(RESD)
    if not op.exists(RESD):
        print("File or Directory not exist ! ")
        exit(1)
    a = MAIN.copy()
    MAIN = []
    for i in a:
        i = op.join(".", "..", i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
        MAIN.append(i)
    print(MAIN)
    a = SHARE.copy()
    SHARE = []
    for i in a:
        i = op.join(".", "..", i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
        SHARE.append(i)
    print(SHARE)
    EXPORTD = op.join(".", EXPORTD)
    print("====>>>>Export to :{}".format(EXPORTD))
    # 删除文件夹
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
    



