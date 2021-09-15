#!/usr/bin/python3
# 运行Frpc

import os


Windows = os.path.sep == "\\"
frp_w = os.path.join("res", "frp", "windows", "frpc.exe")
frp_l = os.path.join("res", "frp", "linux", "frpc")

def execCommand(cmd):
    """
    只有返回值
    :return: result
    """
    r = ""
    try:
        r = os.popen(cmd).read()
    except Exception as e:
        print(e)
    return r


def loadConf():
    """
    加载配置文件
    :return:
    """
    conf=""
    with open(os.path.join("conf", "frpc.conf"), "r", encoding="utf-8") as f:
        for line in f.readlines():
            if line[0:1] == "#":
                # 去掉注释
                pass
            else:
                line = line.replace("\r\n","").replace("\n","")
                if line.split("=")[0] == "id" :
                    conf = os.path.join("conf", "frpc", "{}.ini".format(line.split("=")[1]))
    return conf


if __name__ == '__main__':
    c = loadConf()
    frp = ""
    if Windows:
        frp = frp_w
    else:
        frp = frp_l
    cmd = "{} -c {}".format(frp, c)
    print(cmd)
    execCommand(cmd)

