#!/usr/bin/python3

import os
import os.path as op
import shutil

### 请看清楚是文件还是文件夹！
# 资源文件夹名
RESD = ["wget"]
# 导出文件夹
EXPORTD = "NDP_AUTO"
# 共享文件名
SHARE = ["favicon.ico", "LICENSE", "README.md"]


def addition():
    """
    补充步骤
    :return:
    """
    print("====>>>>Customize...")
    fn = "download.Net.bat"
    print("Copy {}...".format(fn))
    srcf = op.join(".", fn)
    dstf = op.join(".", EXPORTD, fn)
    shutil.copyfile(srcf, dstf)
    fn = "install.Net.bat"
    print("Copy {}...".format(fn))
    srcf = op.join(".", fn)
    dstf = op.join(".", EXPORTD, fn)
    shutil.copyfile(srcf, dstf)


if __name__ == '__main__':
    print("====>>>>Check Required Files: ")
    # 路径化
    for i in RESD:
        i = op.join(".", "..","res", i)
        print(i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
    for i in SHARE:
        i = op.join(".", "..", i)
        print(i)
        if not op.exists(i):
            print("File or Directory not exist ! ")
            exit(1)
    EXPORTD = op.join(".", EXPORTD)
    print("====>>>>Export to :")
    print(EXPORTD)
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
    os.mkdir(op.join(EXPORTD, "conf"))
    os.mkdir(op.join(EXPORTD, "res"))
    # 复制文件
    print("====>>>>Copy res file...")
    for i in RESD:
        srcd = op.join(".", "..","res", i)
        dstd = op.join(EXPORTD, "res", i)
        shutil.copytree(srcd, dstd)
    print("====>>>>Copy lib file...")
    for i in SHARE:
        srcf = op.join(".", "..", i)
        dstf = op.join(EXPORTD, i)
        shutil.copyfile(srcf, dstf)
    addition()
