#!/usr/bin/python3

import lib_loadConf as m_Conf

if __name__ == '__main__':
    args = m_Conf.sysArgv()
    m_Conf.generateDecryptConfFile(args[1], args[2])