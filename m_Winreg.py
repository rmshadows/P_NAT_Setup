#!/usr/bin/python3
"""
此模块用于Windows注册表修改
DOC: https://docs.python.org/zh-cn/3/library/winreg.html#winreg.SaveKey
"""
import m_System

import os
import winreg
import os.path as op


def __debug():
    # w = winreg.EnumKey(r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")
    # reg = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")
    reg = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"")
    # print(reg)
    PATH = r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\WIC"
    # exportReg(PATH, "1.reg")
    importReg(PATH, "1.reg")
    print("====END====")


def exportReg(fullpath, filepath):
    """
    导出注册表
    Args:
        fullpath:
        filepath:

    Returns:

    """
    if op.exists(filepath):
        os.unlink(filepath)
    cmd = "reg export {} {}".format(fullpath, filepath)
    m_System.execCommand(cmd)


def importReg(filepath):
    """
    导入注册表
    Args:
        filepath:

    Returns:

    """
    cmd = "reg import {}".format(filepath)
    m_System.execCommand(cmd)


def saveKey(key, filepath):
    """
    DISCARD 弃用
    导出注册表
    Args:
        key: 已经打开的key
        filepath:

    Returns:

    """
    try:
        import win32api  # pywin32
        import win32security
        priv_flags = win32security.TOKEN_ADJUST_PRIVILEGES | win32security.TOKEN_QUERY
        hToken = win32security.OpenProcessToken(win32api.GetCurrentProcess(), priv_flags)
        privilege_id = win32security.LookupPrivilegeValue(None, "SeBackupPrivilege")
        win32security.AdjustTokenPrivileges(hToken, 0, [(privilege_id, win32security.SE_PRIVILEGE_ENABLED)])
        if op.exists(filepath):
            os.unlink(filepath)
        winreg.SaveKey(key, filepath)
    except Exception as e:
        print("saveKey(key, filepath): {}".format(e))


def saveKeyEx(fullpath, filepath):
    """
    DISCARD 弃用
    导出注册表
    Args:
        fullpath:
        filepath:

    Returns:

    """
    try:
        import win32api  # pywin32
        import win32security
        # https://stackoverflow.com/questions/30984406/winreg-savekey-error-a-required-privilege-is-not-held-by-the-client
        # You need to have SeBackupPrivilege enabled for this to work
        # https://sourceforge.net/projects/pywin32/files/pywin32/Build%20219/
        priv_flags = win32security.TOKEN_ADJUST_PRIVILEGES | win32security.TOKEN_QUERY
        hToken = win32security.OpenProcessToken(win32api.GetCurrentProcess(), priv_flags)
        privilege_id = win32security.LookupPrivilegeValue(None, "SeBackupPrivilege")
        win32security.AdjustTokenPrivileges(hToken, 0, [(privilege_id, win32security.SE_PRIVILEGE_ENABLED)])
        if op.exists(filepath):
            os.unlink(filepath)
        key = getKeyEx(fullpath)
        winreg.SaveKey(key, filepath)
    except Exception as e:
        print("saveKeyEx(fullpath, filepath): {}".format(e))


def loadKey(fullpath, filepath):
    """
    DISCARD 弃用
    导入注册表
    在指定键之下创建一个子键，并将指定文件中的注册表信息存入该子键中。
    该文件必须是用 SaveKey() 函数创建的。在文件分配表（FAT）文件系统中，文件名可能不带扩展名。
    Args:
        fullpath:
        filepath:

    Returns:

    """
    try:
        import win32api  # pywin32
        import win32security
        priv_flags = win32security.TOKEN_ADJUST_PRIVILEGES | win32security.TOKEN_QUERY
        hToken = win32security.OpenProcessToken(win32api.GetCurrentProcess(), priv_flags)
        privilege_id = win32security.LookupPrivilegeValue(None, "SeBackupPrivilege")
        win32security.AdjustTokenPrivileges(hToken, 0, [(privilege_id, win32security.SE_PRIVILEGE_ENABLED)])
        s = splitRootPath(fullpath)
        winreg.LoadKey(s[0], s[1], filepath)
    except Exception as e:
        print("loadKey(fullpath, filepath): {}".format(e))


def createValue(key, subname, type, value):
    """
    创建值
    Args:
        key: 键
        subname: 名称
        type: winreg.REG_DWORD winreg.REG_QWORD winreg.REG_BINARY winreg.REG_DWORD_LITTLE_ENDIAN winreg.REG_DWORD_BIG_ENDIAN
        winreg.REG_EXPAND_SZ winreg.REG_LINK winreg.REG_MULTI_SZ winreg.REG_NONE winreg.REG_QWORD_LITTLE_ENDIAN  winreg.REG_RESOURCE_LIST
        winreg.REG_FULL_RESOURCE_DESCRIPTOR winreg.REG_RESOURCE_REQUIREMENTS_LIST winreg.REG_SZ
        value: 值

    Returns:

    """
    winreg.SetValueEx(key, subname, 0, type, value)
    # if existedSubkey(key, subname):
    #     winreg.SetValueEx(key, subname, 0, type, value)
    # else:
    #     createSubkey(key, subname)
    #     winreg.SetValueEx(key, subname, 0, type, value)


def deleteValue(key, value):
    """
    删除值
    Args:
        key:
        value:

    Returns:

    """
    winreg.DeleteValue(key, value)


def deleteValueEx(fullpath, value):
    """
    删除值
    Args:
        fullpath:
        value:

    Returns:

    """
    winreg.DeleteValue(getKeyEx(fullpath), value)


def splitRootPath(fullpath):
    """
    分解全路径为root和子路径
    Args:
        fullpath: str 全路径
    Returns:
        winreg root: HKEY_LOCAL_MACHINE HKEY_CLASSES_ROOT HKEY_CURRENT_CONFIG HKEY_USERS HKEY_CURRENT_USER
        path: 子路径
    """
    s = fullpath.split("\\", 1)
    root = s[0]
    sub = s[1]
    # print(sub)
    if root == "HKEY_LOCAL_MACHINE":
        return winreg.HKEY_LOCAL_MACHINE, sub
    elif root == "HKEY_CLASSES_ROOT":
        return winreg.HKEY_CLASSES_ROOT, sub
    elif root == "HKEY_CURRENT_CONFIG":
        return winreg.HKEY_CURRENT_CONFIG, sub
    elif root == "HKEY_USERS":
        return winreg.HKEY_USERS, sub
    elif root == "HKEY_CURRENT_USER":
        return winreg.HKEY_CURRENT_USER, sub
    else:
        return None


def getKeyInfo(reg, query):
    """
    查询Key信息
    Args:
        reg: 键
        query: 0:整数值，给出了此注册表键的子键数量。 1:整数值，给出了此注册表键的值的数量。 2:整数值，给出了此注册表键的最后修改时间，单位为自 1601 年 1 月 1 日以来的 100 纳秒。
    Returns:
    """
    return winreg.QueryInfoKey(reg)[query]


def getValue(reg, valuename):
    """
    返回值、类型
    Args:
        reg: key
        valuename: 名称
    Returns:
        value, type, typename
        值 类型 类型名称
    """
    r = winreg.QueryValueEx(reg, valuename)
    value = r[0]
    type = r[1]
    typename = None
    TYPES = {"REG_BINARY": winreg.REG_BINARY,
             "REG_DWORD": winreg.REG_DWORD,
             "REG_DWORD_LITTLE_ENDIAN": winreg.REG_DWORD_LITTLE_ENDIAN,
             "REG_DWORD_BIG_ENDIAN": winreg.REG_DWORD_BIG_ENDIAN,
             "REG_EXPAND_SZ": winreg.REG_EXPAND_SZ,
             "REG_LINK": winreg.REG_LINK,
             "REG_MULTI_SZ": winreg.REG_MULTI_SZ,
             "REG_NONE": winreg.REG_NONE,
             "REG_QWORD": winreg.REG_QWORD,
             "REG_QWORD_LITTLE_ENDIAN": winreg.REG_QWORD_LITTLE_ENDIAN,
             "REG_RESOURCE_LIST": winreg.REG_RESOURCE_LIST,
             "REG_FULL_RESOURCE_DESCRIPTOR": winreg.REG_FULL_RESOURCE_DESCRIPTOR,
             "REG_RESOURCE_REQUIREMENTS_LIST": winreg.REG_RESOURCE_REQUIREMENTS_LIST,
             "REG_SZ": winreg.REG_SZ}
    for i in TYPES.keys():
        if TYPES[i] == r[1]:
            typename = i
    return value, type, typename


def getValueEx(fullpath, valuename):
    """
    返回值、类型
    Args:
        fullpath: 路径
        valuename: 名称
    Returns:
        value, type, typename 如: REG_BINARY
    """
    r = winreg.QueryValueEx(getKeyEx(fullpath), valuename)
    value = r[0]
    type = r[1]
    typename = None
    TYPES = {"REG_BINARY":winreg.REG_BINARY,
             "REG_DWORD":winreg.REG_DWORD,
             "REG_DWORD_LITTLE_ENDIAN":winreg.REG_DWORD_LITTLE_ENDIAN,
             "REG_DWORD_BIG_ENDIAN":winreg.REG_DWORD_BIG_ENDIAN,
             "REG_EXPAND_SZ":winreg.REG_EXPAND_SZ,
             "REG_LINK":winreg.REG_LINK,
             "REG_MULTI_SZ":winreg.REG_MULTI_SZ,
             "REG_NONE":winreg.REG_NONE,
             "REG_QWORD":winreg.REG_QWORD,
             "REG_QWORD_LITTLE_ENDIAN":winreg.REG_QWORD_LITTLE_ENDIAN,
             "REG_RESOURCE_LIST":winreg.REG_RESOURCE_LIST,
             "REG_FULL_RESOURCE_DESCRIPTOR":winreg.REG_FULL_RESOURCE_DESCRIPTOR,
             "REG_RESOURCE_REQUIREMENTS_LIST":winreg.REG_RESOURCE_REQUIREMENTS_LIST,
             "REG_SZ":winreg.REG_SZ}
    for i in TYPES.keys():
        if TYPES[i] == r[1]:
            typename = i
    return value, type, typename


def createSubkey(reg, subname):
    """
    创建注册表子键
    Args:
        reg: 前缀 winreg.HKEY_LOCAL_MACHINE
        Path: 路径 "SOFTWARE\Microsoft\Windows\CurrentVersion\\Uninstall"
    Returns:
        注册表类
    """
    winreg.CreateKey(reg, subname)


def createKey(fullpath):
    """
    创建注册表键
    Args:
        fullpath: 路径
    Returns:
        None
    """
    if existedKey(fullpath):
        # raise Exception("Key Existed!")
        print("Key Existed!")
    else:
        s = splitRootPath(fullpath)
        winreg.CreateKey(winreg.OpenKey(s[0], r"", 0, winreg.KEY_ALL_ACCESS), s[1])


def existedSubkey(key, subname):
    """
    是否有注册表子键
    Args:
        key: 键
        subname: 子键名称
    Returns:
        boolean
    """
    for i in getSubkey(key):
        if i == subname:
            return True
    return False


def deleteSubkey(reg, subname):
    """
    删除注册表子键
    Args:
        reg: 前缀 winreg.HKEY_LOCAL_MACHINE
        Path: 路径 "SOFTWARE\Microsoft\Windows\CurrentVersion\\Uninstall"
    Returns:
        注册表类
    """
    winreg.DeleteKey(reg, subname)


def deleteKey(fullpath):
    """
    删除注册表键
    Args:
        fullpath: 路径
    Returns:
        None
    """
    if existedKey(fullpath):
        s = splitRootPath(fullpath)
        winreg.DeleteKey(winreg.OpenKey(s[0], r"", 0, winreg.KEY_ALL_ACCESS), s[1])
    else:
        print("Key not existed!")


def existedKey(fullpath):
    """
    全路径是否存在
    Args:
        fullpath: 全路径
    Returns:
        boolean
    """
    s = splitRootPath(fullpath)
    try:
        k = winreg.OpenKey(s[0], s[1])
        k.Close()
        return True
    except Exception as e:
        print("existedKey(fullpath):{}".format(e))
        return False


def getKey(root, Path, permission=winreg.KEY_ALL_ACCESS):
    """
    打开注册表
    Args:
        root: 前缀 winreg.HKEY_LOCAL_MACHINE
        Path: 路径 "SOFTWARE\Microsoft\Windows\CurrentVersion\\Uninstall"
    Returns:
        注册表类
    """
    return winreg.OpenKey(root, Path, 0, permission)


def getKeyEx(fullpath, permission=winreg.KEY_ALL_ACCESS):
    """
    打开注册表
    Args:
        fullpath: 路径
    Returns:
        注册表类
    """
    s = splitRootPath(fullpath)
    return winreg.OpenKey(s[0], s[1], 0, permission)


def getSubkey(key, mode=0):
    """
    给定注册表，返回子键名称列表
    Args:
        key: 注册表类
        mode: 0: 暴力遍历 1: 查询值数再遍历
    Returns:
        子类
    """
    subKey = []
    if mode == 0:
        # 获取该键的所有键值，遍历枚举
        try:
            i = 0
            while True:
                # EnumValue方法用来枚举键值，EnumKey用来枚举子键
                sk = winreg.EnumKey(key, i)
                # print(sk)
                subKey.append(sk)
                i += 1
        except Exception as e:
            print("getSubkey(W): "+str(winreg.QueryInfoKey(key)))
            # print("\tsubKey(): {}".format(e))
    elif mode == 1:
        for i in range(0, getKeyInfo(key, 0)):
            subKey.append(winreg.EnumKey(key, i))
    else:
        return None
    return subKey


def hideSoftware(name, is64Bit=True, accurate=True):
    """
    to hide a software from regedit, 添加Dword SystemComponent 1
    name: 软件的DisplayName
    is64Bit: 是否是64位
    accurate: 是否精准匹配，否的话只要名称含有某字段就执行
    :return: 是否找到该应用(并非是否执行成功！)
    https://blog.csdn.net/wybshyy/article/details/52064296
    """
    regr = ""
    result = False
    # HKEY_LOCAL_MACHINE
    if is64Bit:
        regr = getKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")
    else:
        regr = getKey(winreg.HKEY_LOCAL_MACHINE,r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall")
    # 遍历
    for i in getSubkey(regr):
        try:
            # 假如知道键名，也可以直接取值
            if is64Bit:
                sub_key = getKeyEx(
                    r"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i))
            else:
                sub_key = getKeyEx(
                    r"HKEY_LOCAL_MACHINE\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i))
            value, type, typename = getValue(sub_key, "DisplayName")
            # print("Display: {} , Type: {}, TypeName: {}".format(value, type, typename))
            # 找到软件
            found = False
            # 是否精确搜寻
            if accurate:
                if name == str(value):
                    found = True
            else:
                if name in str(value):
                    found = True
            if found:
                result = True
                # 检查是否有SystemComponent
                try:
                    createValue(sub_key, r"SystemComponent", winreg.REG_DWORD, 1)
                    # winreg.SetValueEx(regr, "SystemComponent", 0, winreg.REG_DWORD, 1)
                except Exception as e:
                    print(e)
                print("Software Found: "+i)
        except Exception as e:
            print("{} got wrong : {}".format(i, e))
    # HKEY_CURRENT_USER
    if is64Bit:
        regr = getKey(winreg.HKEY_CURRENT_USER, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")
    else:
        regr = getKey(winreg.HKEY_CURRENT_USER,r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall")
    # 遍历
    for i in getSubkey(regr):
        try:
            if is64Bit:
                sub_key = getKeyEx(
                    r"HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i))
            else:
                sub_key = getKeyEx(
                    r"HKEY_CURRENT_USER\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i))
            value, type, typename = getValue(sub_key, "DisplayName")
            found = False
            if accurate:
                if name == str(value):
                    found = True
            else:
                if name in str(value):
                    found = True
            if found:
                result = True
                try:
                    createValue(sub_key, r"SystemComponent", winreg.REG_DWORD, 1)
                except Exception as e:
                    print(e)
                print("Software Found: "+i)
        except Exception as e:
            print("{} got wrong : {}".format(i, e))
    if not result:
        print("Software Not Found.")
    # 关闭
    winreg.CloseKey(regr)
    return result


if __name__ == '__main__':
    # subKey("")
    __debug()










