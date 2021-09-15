#!/usr/bin/python3
# 安装ZeroTier

import os


Windows = os.path.sep == "\\"
# 提权gsudo
gsudo=os.path.join("gsudo","gsudo.exe")
# 要加入的网络
net_id = []


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
    with open(os.path.join("conf", "zero_tier.conf"), "r", encoding="utf-8") as f:
        for line in f.readlines():
            if line[0:1] == "#":
                # 去掉注释
                pass
            else:
                line = line.replace("\r\n","").replace("\n","")
                if line.split("=")[0] == "id" :
                    if line.split("=")[1] == "":
                        print("配置文件有误。")
                        exit(1)
                    else:
                        net_id.append(line.split("=")[1])
                else:
                    pass


def installZeroTier():
    """
    安装ZeroTier
    :return:
    """
    if Windows :
        run = os.system("cd C:\Program Files (x86)\ZeroTier\One")
    else:
        if os.geteuid() != 0:
            print("This program must be run as root. Aborting.")
            exit(1)
        run = os.system("zerotier-cli")
    if  run != 0:
        print("您似乎没有安装ZeroTier(Status code = {}),开始安装".format(run))
        if Windows:
            execCommand("{} msiexec /q /i {}".format(gsudo , os.path.join("res", "zero_tier_one", "zero_tier_one.msi")))
        else:
            execCommand("curl -s https://install.zerotier.com | sudo bash")
        print("安装完成，请检查。")
    else:
        print("您似乎已经安装了ZeroTier")


def joinNetwork(id):
    """
    加入网络
    :return:
    """
    execCommand("{} zerotier-cli.bat join {}".format(gsudo, id))


def checkJoined(id):
    """
    检查是否已经加入
    :return:
    """
    joined = []
    try:
        # 200 info 409ed457bb 1.6.5 ONLINE //// info
        # 200 listnetworks a09acf023319bd5a DayonNet 5a:fd:87:e7:55:74 OK PRIVATE ethernet_32772 10.147.17.45/24
        # lines = ['200 listnetworks <nwid> <name> <mac> <status> <type> <dev> <ZT assigned ips>', '', '200 listnetworks a09acf023319bd5a DayonNet 5a:fd:87:e7:55:74 OK PRIVATE ethernet_32772 10.147.17.45/24', '', '']
        lines = execCommand("{} zerotier-cli.bat listnetworks".format(gsudo)).split("\n")
        for each in lines:
            try:
                each = each.split(" ")
                if each[2]=="<nwid>":
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


if __name__ == '__main__':
    loadConf()
    installZeroTier()
    for id in net_id:
        if checkJoined(id) is False:
            print("正在加入 {} 网络".format(id))
            joinNetwork(id)
        else:
            print("您已经加入了 {} 网络".format(id))
