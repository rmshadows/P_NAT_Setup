#!/usr/bin/python3
"""
加密配置文件
"""
import os
import sys

_LIB = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "lib"))
if _LIB not in sys.path:
    sys.path.insert(0, _LIB)

import lib_loadConf as m_Conf

if __name__ == '__main__':
    args = m_Conf.sysArgv()
    m_Conf.generateEncryptConfFile(args[1], args[2])