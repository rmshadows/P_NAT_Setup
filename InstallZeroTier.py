#!/usr/bin/python3
# 安装ZeroTier

import os


Windows = os.path.sep == "\\"


def execCommand(cmd):
    """
    只有返回值
    :return: result
    """
    r = os.system(cmd)
    return r


def installZeroTier():
    """
    安装ZeroTier
    :return:
    """
    if Windows:
        # return execCommand("")
        pass
    else:
        return execCommand("curl -s https://install.zerotier.com | sudo bash")


if __name__ == '__main__':
    installZeroTier()
