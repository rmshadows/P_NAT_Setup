#!/usr/bin/python3
# 运行Frpc

import os
import threading

Windows = os.path.sep == "\\"
frp_w = os.path.join("res", "frp", "windows", "frpc.exe")
frp_l = os.path.join("res", "frp", "linux", "frpc")


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


class frpThread (threading.Thread):
    def __init__(self, cmd):
        threading.Thread.__init__(self)
        self.cmd = cmd


    def run(self):
        print ("开始线程：" + self.cmd)
        try:
            print("启动FRP中。。。终止请按 Ctrl+C")
            os.system(self.cmd)
        except Exception as e:
            print(e)
        print ("退出线程：" + self.cmd)


if __name__ == '__main__':
    c = loadConf()
    frp = ""
    if Windows:
        frp = frp_w
    else:
        frp = frp_l
    cmd = "{} -c {}".format(frp, c)
    if Windows:
        cmd = "start cmd /k " + cmd
    print(cmd)
    frp_r = frpThread(cmd)
    frp_r.start()
    if not Windows:
        frp_r.join()

