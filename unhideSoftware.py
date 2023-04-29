#!/usr/bin/python3
"""
取消隐藏软件（用于卸载）
"""
import m_Winreg

# manual
# 取消隐藏的软件
TODO_S = ["Tight"]

if __name__ == '__main__':
    for i in TODO_S:
        try:
            m_Winreg.hideSoftware(i, True, False, False)
            m_Winreg.hideSoftware(i, False, False, False)
        except Exception as e:
            print(e)
