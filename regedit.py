import winreg

"""
此脚本用于添加应用程序注册表项目
"""


def subKey(reg):
    """
    给定注册表，返回子键名称列表
    :param reg:
    :return:
    """
    subKey = []
    # 获取该键的所有键值，遍历枚举
    # print("====>>subKey()<<====")
    try:
        i = 0
        while True:
            # EnumValue方法用来枚举键值，EnumKey用来枚举子键
            key = winreg.EnumKey(reg, i)
            # print(key)
            subKey.append(key)
            i += 1
    except Exception as e:
        print("==={}===".format(e))
        return subKey


def hideSoftware(name, is64Bit=True, accurate=True):
    """
    to hide a software from regedit, 添加Dword SystemComponent 1
    name: 软件的DisplayName
    is64Bit: 是否是64位
    accurate: 是否精准匹配，否的话只要名称含有某字段就执行
    :return: 是否找到该应用(并非是否执行成功！)
    """
    reg = ""
    if is64Bit:
        reg = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall")
    else:
        reg=winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall")
    sk = subKey(reg)
    # 遍历
    for i in sk:
        if is64Bit:
            # 需要打开访问权限
            reg = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i), 0, winreg.KEY_ALL_ACCESS)
        else:
            reg = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{}".format(i), 0, winreg.KEY_ALL_ACCESS)
        try:
            # 假如知道键名，也可以直接取值
            value, type = winreg.QueryValueEx(reg, "DisplayName")
            # print("Display: {} , Type: {}".format(value, type))
            # 找到软件
            found = False
            if accurate:
                if name == str(value):
                    found = True
            else:
                if name in str(value):
                    found = True
            if found:
                # 检查是否有SystemComponent
                try:
                    winreg.SetValueEx(reg, "SystemComponent", 0, winreg.REG_DWORD, 1)
                except Exception as e:
                    print(e)
                print("Software Found.")
                return True
        except Exception as e:
            print("{}  出错 : {}".format(i, e))
    print("Software Not Found.")
    # 关闭
    winreg.CloseKey(reg)
    return False


if __name__ == '__main__':
    hideSoftware("")




