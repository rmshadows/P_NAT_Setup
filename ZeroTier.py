#!/usr/bin/python3
# 安装ZeroTier

import os
import sys
import threading
import time

import regedit

Windows = os.path.sep == "\\"
# 提权gsudo
gsudo = os.path.join("gsudo", "gsudo.exe")
# 要加入的网络
net_id = []
# 安装状态
Install = False
# Task线程状态
Task = False
# 是否隐藏
hide = -1


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
    global net_id
    global hide
    with open(os.path.join("conf", "zero_tier.conf"), "r", encoding="utf-8") as f:
        for line in f.readlines():
            if line[0:1] == "#":
                # 去掉注释
                pass
            else:
                line = line.replace("\r\n", "").replace("\n", "")
                if line.split("=")[0] == "id":
                    if line.split("=")[1] == "":
                        print("配置文件有误: id")
                        sys.exit(1)
                    else:
                        net_id.append(line.split("=")[1])
                elif line.split("=")[0] == "hide":
                    if line.split("=")[1] == "":
                        print("配置文件有误: hide")
                        sys.exit(1)
                    else:
                        hide=int(line.split("=")[1])
                else:
                    pass


def installZeroTier():
    """
    安装ZeroTier
    :return:
    """
    if Windows:
        run = os.system("dir \"C:\Program Files (x86)\ZeroTier\One\ZeroTier One.exe\"")
    else:
        if os.geteuid() != 0:
            print("This program must be run as root. Aborting.")
            sys.exit(1)
        run = os.system("zerotier-cli")
    if run != 0:
        print("您似乎没有安装ZeroTier(Status code = {}),开始安装".format(run))
        if Windows:
            # execCommand("{} msiexec /q /i {}".format(gsudo, os.path.join("res", "zero_tier_one", "zero_tier_one.msi")))
            ins = cmdThread(
                "{} msiexec /q /i {}".format(gsudo, os.path.join("res", "zero_tier_one", "zero_tier_one.msi")), True,
                60)
            ins.start()
            ins.join()
            if not Install:
                run = os.system("dir \"C:\Program Files (x86)\ZeroTier\One\ZeroTier One.exe\"")
                if run != 0:
                    print("安装失败？")
                    ins = cmdThread(
                        "{} msiexec /i {}".format(gsudo, os.path.join("res", "zero_tier_one", "zero_tier_one.msi")),
                        True,
                        60)
                    ins.start()
                    ins.join()
            time.sleep(1)
        else:
            execCommand("curl -s https://install.zerotier.com | sudo bash")
        print("安装完成，请检查。如有报错请手动安装ZeroTier。")
    else:
        print("您似乎已经安装了ZeroTier: C盘下有ZeroTier文件夹(C:\Program Files (x86)\ZeroTier\One) ")


def joinNetwork(j_id):
    """
    加入网络
    :return:
    """
    global Install
    if Windows:
        # 如果刚通过安装，则使用绝地路径
        if Install:
            execCommand("{} \"C:\Program Files (x86)\ZeroTier\One\zerotier-cli.bat\" join {}".format(gsudo, j_id))
        else:
            execCommand("{} zerotier-cli.bat join {}".format(gsudo, j_id))
    else:
        execCommand("sudo zerotier-cli.bat join {}".format(j_id))


def checkZTStatus():
    """
    检查连接状态
    不是200终止
    :return:
    """
    global Install
    print("是否新安装: {}".format(Install))
    try:
        if Windows:
            # 如果刚通过安装，则使用绝地路径
            if Install:
                lines = execCommand("{} \"C:\Program Files (x86)\ZeroTier\One\zerotier-cli.bat\" info".format(gsudo))
            else:
                lines = execCommand("{} zerotier-cli info".format(gsudo))
            # 200 info 409ed457bb 1.6.5 ONLINE //// info
        else:
            lines = execCommand("sudo zerotier-cli info")
        i = lines.split(" ")[0]
        if int(i) != 200:
            print("状态码不等于200！：{} ,退出。".format(lines))
            sys.exit(1)
        else:
            print("客户端ID：{}".format(lines.split(" ")[2]))
    except Exception as e:
        print(e)
        sys.exit(1)


def checkJoined(id):
    """
    检查是否已经加入
    :return:
    """
    global Install
    joined = []
    try:
        # 200 listnetworks a09acf023319bd5a DayonNet 5a:fd:87:e7:55:74 OK PRIVATE ethernet_32772 10.147.17.45/24
        # lines = ['200 listnetworks <nwid> <name> <mac> <status> <type> <dev> <ZT assigned ips>', '', '200 listnetworks a09acf023319bd5a DayonNet 5a:fd:87:e7:55:74 OK PRIVATE ethernet_32772 10.147.17.45/24', '', '']
        if Windows:
            # 如果刚通过安装，则使用绝地路径
            if Install:
                lines = execCommand("{} \"C:\Program Files (x86)\ZeroTier\One\zerotier-cli.bat\" listnetworks".format(gsudo)).split("\n")
            else:
                lines = execCommand("{} zerotier-cli.bat listnetworks".format(gsudo)).split("\n")
        else:
            lines = execCommand("sudo zerotier-cli.bat listnetworks").split("\n")
        for each in lines:
            try:
                each = each.split(" ")
                if each[2] == "<nwid>":
                    pass
                else:
                    joined.append(each[2])
                    print("已加入:{}".format(each[2]))
            except Exception as e:
                pass
    except Exception as e:
        pass
    if id in joined:
        return True
    else:
        return False


def checkWindowsAdmin():
    """
    检查管理权限
    :return:
    """
    admin = execCommand("whoami /groups | find \"S-1-16-12288\" && echo YES_ADMIN")
    # print(admin)
    if "YES_ADMIN" in admin:
        return True
    else:
        print("Please run as administrator.")
        return False


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
    if Windows and not checkWindowsAdmin():
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
    loadConf()
    installZeroTier()
    checkZTStatus()
    for id in net_id:
        if checkJoined(id) is False:
            print("正在加入 {} 网络".format(id))
            joinNetwork(id)
            print("操作完成。")
        else:
            print("您已经加入了 {} 网络".format(id))
    # 隐藏
    if hide == 1:
        regedit.hideSoftware("Zerotier", False, False)
    print("== END ==")
