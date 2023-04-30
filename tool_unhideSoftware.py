#!/usr/bin/python3
"""
取消隐藏软件（用于卸载）
"""
import m_Winreg
import m_loadConf as m_Conf

# manual
# 取消隐藏的软件
TODO_S = []

if __name__ == '__main__':
    args = m_Conf.sysArgv()
    for i in args:
        TODO_S.append(i)
    for i in TODO_S:
        try:
            m_Winreg.hideSoftware(i, True, False, False)
            m_Winreg.hideSoftware(i, False, False, False)
        except Exception as e:
            print(e)
