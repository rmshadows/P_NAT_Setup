#!/usr/bin/python3
# 安装模板

import os
import sys
import threading
import time
import m_Winreg as regedit
import m_loadConf as loadConf
import m_System


# Manual
# 配置文件名称前缀 (zerotier.conf 或者 zerotier.eonf)
CONF_PREFIX = "zerotier"
# 资源路径
ZEROTIER_PATH = os.path.join("res", "ZeroTierOne", "ZeroTierOne.msi")


Windows = os.path.sep == "\\"
# 提权gsudo
gsudo = os.path.join("gsudo", "gsudo.exe")

# 是否隐藏
HIDE_APP = -1
# 安装状态
Install = False
# Task线程状态
Task = False


def getConf():
    """
    加载配置文件
    :return:
    """
    global NET_ID
    global HIDE_APP
    conf_dic = {}

    if loadConf.clearMode():
        conf_dic = loadConf.readClearConf(os.path.join("conf", "{}.conf".format(CONF_PREFIX)))
    else:
        conf_dic = loadConf.readEncrytedConf(os.path.join("conf", "{}.eonf".format(CONF_PREFIX)))
    # print(conf_dic)
    for i in conf_dic:
        if i == "id":
            NET_ID = conf_dic[i].split(";")
        elif i == "hide":
            HIDE_APP = conf_dic[i]
    # print(NET_ID, HIDE_APP)
    

def install():
    """
    安装
    :return:
    """
    pass


class taskThread(threading.Thread):
    """
    执行cmd命令
    """

    def __init__(self, cmd):
        threading.Thread.__init__(self)
        self.cmd = cmd

    def run(self):
        global Install
        global Task
        Install = False
        Task = False
        print("Task do：" + self.cmd)
        i = -1

        try:
            i = os.system(self.cmd)
        except Exception as e:
            print(e)
        if i == 0:
            Install = True
        Task = True
        print("Task done code：{} 新装：{} Task完成：{} ".format(i, Install, Task))


class cmdThread(threading.Thread):
    """
    新线程执行
    """

    def __init__(self, cmd, daemon, timeout=0):
        threading.Thread.__init__(self)
        self.cmd = cmd
        self.setDaemon(daemon)
        self.timeout = timeout

    def run(self):
        if self.daemon:
            print("Submit Task...")
            taskThread(self.cmd).start()
            self.quit()
            print("CMD Thread quit")
        else:
            try:
                os.popen(self.cmd)
            except Exception as e:
                print(e)

    def quit(self):
        global Task
        to = self.timeout
        print("Set timeout: " + str(to))
        done = False
        if to == 0:
            pass
        else:
            while to > 0:
                time.sleep(1)
                print("Task-Timeout:" +str(to) ,end="  ")
                print("Task状态: {}".format(Task))
                to -= 1
                if Task:
                    print("Task Done")
                    done = True
                    break
        if not done:
            print("Task Timeout!")
        return None


if __name__ == '__main__':
    print("== START ==")
    if Windows and not m_System.checkAdministrator():
        name = sys.executable
        print("名称：" + name)
        if "python" in name:
            # 作为脚本运行时不做处理
            print("当前运行：{}".format(__file__))
            cmdThread("{} start cmd.exe /k {}".format(gsudo, "dir"), False).start()
        else:
            # 提权运行
            # execCommand("{} start cmd.exe /k {} && pause".format(gsudo, name))
            cmdThread("{} start cmd.exe /k {}".format(gsudo, name), False).start()
        sys.exit(0)
    getConf()
    install()
    # 隐藏
    if HIDE_APP == 1:
        try:
            regedit.hideSoftware("ZeroTier", False, False)
            regedit.hideSoftware("ZeroTier", True, False)
        except Exception as e:
            print(e)
    print("== END ==")