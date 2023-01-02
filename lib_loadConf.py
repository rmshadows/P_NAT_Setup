#!/usr/bin/python3
"""
用于读取配置文件

格式:
# Zero Tier配置文件
# id是要加入的网络，可以多个
id=
# 是否在卸载程序列表隐藏
hide=1
"""
import sys

import lib_System as m_System
import lib_AES as m_AES
import os


WINDOWS = os.sep == "\\"


def loadConf(conf_content_list):
    """
    返回配置内容
    Args:
        conf_content_list: 配置文件读取的明文配置信息

    Returns:
        字典 {'id': '', 'hide': '1'}
    """
    conf_set = {}
    for i in conf_content_list:
        i = i.replace("\r\n", "")
        if i[0:1] == "#" or i == "":
            pass
        else:
            para = i.split("=")
            conf_set[para[0]] = para[1]
    print(conf_set)
    # for i in conf_set.keys():
    #     print(i)
    return conf_set


def readConfFile(conf_path):
    """
    仅读取配置文件内容，读取的内容换行符全部都是“\r\n”
    Args:
        conf_path: 配置文件路径

    Returns:

    """
    content = []
    if WINDOWS:
        m_System.removeBom(conf_path)
    with open(conf_path, "r", encoding="UTF-8") as f:
        for i in f.readlines():
            i = i.replace("\r\n", "").replace("\n", "").replace("\r", "")
            content.append("{}{}".format(i, "\r\n"))
    # print(content)
    return content


def readClearConf(conf_path):
    """
    加载明文配置文件的配置
    Args:
        conf_path:

    Returns:

    """
    print("加载配置文件: {}".format(conf_path))
    return loadConf(readConfFile(conf_path))


def readEncrytedConf(conf_path, passwd="encrypt", iv="aes"):
    print("加载配置文件: {}".format(conf_path))
    cipher = m_AES.AES_CFB(passwd, iv)
    encrypt_content = ""
    with open(conf_path, "r", encoding="UTF-8") as f:
        for i in f.readlines():
            encrypt_content += i
    # print(encrypt_content)
    decrypt_content = cipher.decrypt(encrypt_content)
    result = []
    for i in decrypt_content.split("\r\n"):
        result.append("{}{}".format(i, "\r\n"))
    # print(result)
    return loadConf(result)


def generateEncryptConfFile(conf_path, new_file_name, passwd="encrypt", iv="aes"):
    content_lst = readConfFile(conf_path)
    to_encrypt = ""
    for line in content_lst:
        to_encrypt += line
    cipher = m_AES.AES_CFB(passwd, iv)
    to_write = cipher.encrypt(to_encrypt)
    print(to_write)
    with open(new_file_name, "w", encoding="UTF-8") as f:
        f.write(to_write)
        

def generateDecryptConfFile(enconf_path, new_file_name, passwd="encrypt", iv="aes"):
    cipher = m_AES.AES_CFB(passwd, iv)
    encrypt_content = ""
    with open(enconf_path, "r", encoding="UTF-8") as f:
        for i in f.readlines():
            encrypt_content += i
    decrypt_content = cipher.decrypt(encrypt_content)
    result = []
    for i in decrypt_content.split("\r\n"):
        result.append("{}{}".format(i, "\r\n"))
    with open(new_file_name, "w", encoding="UTF-8") as f:
        for i in result:
            f.write(i)


def sysArgv():
    # print("传入参数的总长度为：", len(sys.argv))
    # print("type:", type(sys.argv))
    # print("function name:", sys.argv[0])
    # for i in sys.argv:
    #     print(i)
    return sys.argv


def clearMode():
    """
    如果传入参数“-v”，则使用明文配置文件，否则默认加密的配置文件
    Returns:

    """
    # print("传入参数的总长度为：", len(sys.argv))
    # print("type:", type(sys.argv))
    # print("function name:", sys.argv[0])
    # for i in sys.argv:
    #     print(i)
    mode = False
    for i in sys.argv:
        if i == "-v":
            mode = True
    return mode


if __name__ == '__main__':
    readClearConf("conf/zerotier.conf")
    # generateEncryptConfFile("conf/zerotier.conf", "conf/zerotier.e")
    # readEncrytedConf("conf/zerotier.e")
    # generateDecryptConfFile("conf/zerotier.e", "conf/zerotier.conf")