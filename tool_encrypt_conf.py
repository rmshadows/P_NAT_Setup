#!/usr/bin/python3
"""
加密配置文件
"""

import m_loadConf as m_Conf

if __name__ == '__main__':
    args = m_Conf.sysArgv()
    m_Conf.generateEncryptConfFile(args[1], args[2])