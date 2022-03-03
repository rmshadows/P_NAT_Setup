#!/usr/bin/python3

import os
import os.path as op
import shutil

### 请看清楚是文件还是文件夹！
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


def addition():
    """
    补充步骤
    :return:
    """
    pass


if __name__ == '__main__':
    print("====>>>>Check Required Files: ")
    # 路径化
    conf_srcf = op.join(".", "..", "conf", CONF)
    print(conf_srcf)
    if not op.exists(conf_srcf):
        print("File or Directory not exist ! ")
        exit(1)
    resd_srcd = op.join(".", "..", "res", RESD)
    print(resd_srcd)
    if not op.exists(resd_srcd):
        print("File or Directory not exist ! ")
        exit(1)
    for i in MAIN:
        i = op.join(".", "..", i)
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
    # GSUDO
    print("====>>>>Copy gsudo...")
    gsudod = op.join(".", "..", "gsudo")
    shutil.copytree(gsudod, op.join(EXPORTD, "gsudo"))
    # 复制文件
    print("====>>>>Copy conf file...")
    shutil.copyfile(conf_srcf, op.join(EXPORTD, "conf", CONF))
    print("====>>>>Copy res file...")
    shutil.copytree(resd_srcd, op.join(EXPORTD, "res", RESD))
    print("====>>>>Copy python file...")
    for i in MAIN:
        srcf = op.join(".", "..", i)
        dstf = op.join(EXPORTD, i)
        shutil.copyfile(srcf, dstf)
    print("====>>>>Copy lib file...")
    for i in SHARE:
        srcf = op.join(".", "..", i)
        dstf = op.join(EXPORTD, i)
        shutil.copyfile(srcf, dstf)
    addition()