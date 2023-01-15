#!/usr/bin/python3
# 隐藏Python3

import os
import sys
import threading
import time
import lib_regedit as regedit
import lib_System as m_System

Windows = os.path.sep == "\\"
# 提权gsudo
gsudo = os.path.join("gsudo", "gsudo.exe")
# Task线程状态
Task = False


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
    # 隐藏
    regedit.hideSoftware("Python", True, False)
    # 删除 C:\ProgramData\Microsoft\Windows\Start Menu\Programs\\Python 3.7
    RM_FD = ["C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs\\Python 3.7"]
    for i in RM_FD:
        if m_System.fdExisted(i):
            m_System.rmFD(i)
    
    print("== END ==")
