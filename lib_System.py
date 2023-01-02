#!/usr/bin/python3
"""
返回系统配置
"""
import multiprocessing
import os

IS_WINDOWS = os.sep == "\\"


def execCommand(cmd, debug=False):
    """
    执行命令
    Args:
        cmd: str：命令
        debug: 是否显示运行详情
    Returns:
        执行结果
    """
    r = None
    try:
        r = os.popen(cmd).read()
        if debug:
            print(r)
    except Exception as e:
        print(e)
    return r


def checkAdministrator():
    """
    检查是否有管理员权限
    Returns:
        boolean： 有/无
    """
    if IS_WINDOWS:
        admin = execCommand("whoami /groups | find \"S-1-16-12288\" && echo YES_ADMIN")
        # print(admin)
        if "YES_ADMIN" in admin:
            return True
    else:
        if os.getuid() == 0:
            return True
    return False


def cpu_count():
    """
    返回CPU核心数
    Returns:
        int cpu核心数
    """
    return multiprocessing.cpu_count()


def isBomExist(text_file_path):
    """
    检查文件（UTF-8文本文档）头部是否包含有UTF-BOM
    Args:
        text_file_path: UTF-8文本文档路径

    Returns:
        boolean: 是否含有BOM
        True: 有
        False: 无
    """
    BOM = b'\xef\xbb\xbf'
    bomExisted = lambda s: True if s == BOM else False
    with open(text_file_path, 'rb') as r:
        if bomExisted(r.read(3)):
            print("{}: 检测到UTF-BOM...".format(text_file_path))
            return True
        else:
            return False


def getSuffixFile(suffix, directory="."):
    """
    返回文件夹下的带后缀的文件
    使用os模块的walk函数，搜索出指定目录下的全部PDF文件
    获取同一目录下的所有xxx文件的绝对路径
    # https://www.jb51.net/article/216431.htm
    Args:
        suffix: 后缀
        directory: 文件夹名

    Returns:
        列表
    """
    file_list = [os.path.join(root, filespath) \
                 for root, dirs, files in os.walk(directory) \
                 for filespath in files \
                 if str(filespath).endswith(suffix)
                 ]
    return file_list if file_list else []


def removeBom(filepath):
    """
    existBom(f.read(3))
    移除UTF-8文件的BOM字节
    Args:
        filepath: 带有BOM的文本文档路径
    """
    with open(filepath, 'rb') as r:
        # 只有先读取三个字节，接下来的读取才是去掉BOM的内容
        r.read(3)
        if isBomExist(filepath):
            print("正在移除{}的BOM...".format(filepath))
            fbody = r.read()
            print(fbody)
            with open(filepath, 'wb') as f:
                f.write(fbody)


def averageSplitList(list2split:list, n:int):
    """
    平均分配到列表
    e.g.:
    [1,2,3,4,5,6,7] 3
    [1,2,3] [4,5,6] [7]
    Args:
        list2split: 列表
        n: 平分后每份列表包含的个数n

    Returns:

    """
    for i in range(0, len(list2split), n):
        yield list2split[i:i + n]


def splitListInto(list2split:list, n:int):
    """
    将列表强制分为n个
    e.g.:
    [1,2,3,4,5,6,7,8,9] 4
    [[1, 2], [3, 4], [5, 6], [7, 8, 9]]
    https://www.pythonheidong.com/blog/article/1090214/7731b9881faa69629e0d/
    Args:
        list2split: 列表
        n: 份数

    Returns:
        分隔后的列表
    """
    if not isinstance(list2split, list) or not isinstance(n, int):
        return []
    ls_len = len(list2split)
    if n <= 0 or 0 == ls_len:
        return []
    if n > ls_len:
        return []
    elif n == ls_len:
        return [[i] for i in list2split]
    else:
        j = ls_len // n
        k = ls_len % n
        ### j,j,j,...(前面有n-1个j),j+k
        # 步长j,次数n-1
        ls_return = []
        for i in range(0, (n - 1) * j, j):
            ls_return.append(list2split[i:i + j])
        # 算上末尾的j+k
        ls_return.append(list2split[(n - 1) * j:])
        return ls_return


if __name__ == '__main__':
    print("是否是管理员：{}".format(checkAdministrator()))
    execCommand("ls", True)
    print("CPU核心数: {}".format(cpu_count()))
    print("gitignore文件是否有UTF-8 BOM: {}".format(isBomExist("gitignore")))
    # 列出py文件
    for f in getSuffixFile("py"):
        print(f)
