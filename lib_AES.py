#!/usr/bin/python3
"""
AES模块
"""
import os

from Crypto.Util.Padding import pad, unpad
from Crypto.Cipher import AES

IS_WINDOWS = os.sep == "\\"
CHARACTER = "UTF-8"


class AES_CFB:
    # 密钥
    CFB_KEY = None
    # 密码长度
    CFB_KEY_LEN = 32
    # 偏移量
    CFB_IV = None

    def padding(self, pwd, leng):
        """
        填充到指定位数
        Args:
            pwd: str：待填充的数据
            leng: int：长度

        Returns:
            byte[]： 填充后的数据
        """
        # 密钥过长的报错交给AES模块
        b_pwd = bytearray(pwd, CHARACTER)
        if len(b_pwd) < leng:
            to_pad = leng - len(b_pwd)
            to_pad = bytes(to_pad)
            b_pwd.extend(to_pad)
        return b_pwd

    def encrypt(self, content):
        """
        加密
        Args:
            content: 内容

        Returns:
            hex_str： 十六进制字符串
        """
        # segment_size=128与Java兼容
        cipher = AES.new(self.CFB_KEY, AES.MODE_CFB, self.CFB_IV, segment_size=128)
        result = cipher.encrypt(content.encode(CHARACTER)).hex().upper()
        return result

    def decrypt(self, content):
        """
        解密
        Args:
            content: 十六进制字符串

        Returns:
            解密后的字符串
        """
        cipher = AES.new(self.CFB_KEY, AES.MODE_CFB, self.CFB_IV, segment_size=128)
        result = cipher.decrypt(bytes.fromhex(content.lower())).decode(CHARACTER)
        return result

    def ex_encrypt(self, content, ex_passwd, ex_iv=""):
        """
        临时加密
        修改填充、密码长度请另行新建类
        Args:
            content: 内容
            ex_passwd: 密码
            ex_iv: 向量

        Returns:
            十六进制字符串
        """
        cipher = AES.new(self.padding(ex_passwd, self.CFB_KEY_LEN),
                         AES.MODE_CFB,
                         self.padding(ex_iv, 16),
                         segment_size=128)
        result = cipher.encrypt(content.encode(CHARACTER)).hex().upper()
        return result

    def ex_decrypt(self, content, ex_passwd, ex_iv=""):
        """
        临时解密
        Args:
            content: 十六进制字符串
            ex_passwd: str密码
            ex_iv: str向量

        Returns:
            解密后的字符串
        """
        cipher = AES.new(self.padding(ex_passwd, self.CFB_KEY_LEN),
                         AES.MODE_CFB,
                         self.padding(ex_iv, 16),
                         segment_size=128)
        result = cipher.decrypt(bytes.fromhex(content.lower())).decode(CHARACTER)
        return result

    def __init__(self, passwd, iv="", key_len=32):
        # 以下是有顺序的
        self.CFB_KEY_LEN = key_len
        self.CFB_KEY = self.padding(passwd, self.CFB_KEY_LEN)
        self.CFB_IV = self.padding(iv, 16)


class AES_CBC:
    # 密钥
    CBC_KEY = None
    # 密码长度
    CBC_KEY_LEN = 16
    # 偏移量
    CBC_IV = None

    def padding(self, pwd, leng):
        """
        填充到指定位数
        Args:
            pwd: str：待填充的数据
            leng: int：长度

        Returns:
            byte[]： 填充后的数据
        """
        # 密钥过长的报错交给AES模块
        b_pwd = bytearray(pwd, CHARACTER)
        if len(b_pwd) < leng:
            to_pad = leng - len(b_pwd)
            to_pad = bytes(to_pad)
            b_pwd.extend(to_pad)
        return b_pwd

    def encrypt(self, content):
        """
        加密
        Args:
            content: 内容

        Returns:
            hex_str： 十六进制字符串
        """
        content = content.encode(CHARACTER)
        # 填充到16的倍数
        content = pad(content, 16)
        cipher = AES.new(self.CBC_KEY, AES.MODE_CBC, self.CBC_IV)
        result = cipher.encrypt(content).hex().upper()
        return result

    def decrypt(self, content):
        """
        解密
        Args:
            content: 十六进制字符串

        Returns:
            解密后的字符串
        """
        cipher = AES.new(self.CBC_KEY, AES.MODE_CBC, self.CBC_IV)
        result = cipher.decrypt(bytes.fromhex(content.lower()))
        result = unpad(result, 16)
        return result.decode(CHARACTER)

    def ex_encrypt(self, content, ex_passwd, ex_iv=""):
        """
        临时加密
        修改填充、密码长度请另行新建类
        Args:
            content: 内容
            ex_passwd: 密码
            ex_iv: 向量

        Returns:
            十六进制字符串
        """
        content = content.encode(CHARACTER)
        # 填充到16的倍数
        content = pad(content, 16)
        cipher = AES.new(self.padding(ex_passwd, self.CBC_KEY_LEN), AES.MODE_CBC, self.padding(ex_iv, 16))
        result = cipher.encrypt(content).hex().upper()
        return result

    def ex_decrypt(self, content, ex_passwd, ex_iv=""):
        """
        临时解密
        Args:
            content: 十六进制字符串
            ex_passwd: str密码
            ex_iv: str向量

        Returns:
            解密后的字符串
        """
        cipher = AES.new(self.padding(ex_passwd, self.CBC_KEY_LEN), AES.MODE_CBC, self.padding(ex_iv, 16))
        result = cipher.decrypt(bytes.fromhex(content.lower()))
        result = unpad(result, 16)
        return result.decode(CHARACTER)

    def __init__(self, passwd, iv="", key_len=16):
        # 以下是有顺序的
        self.CBC_KEY_LEN = key_len
        self.CBC_KEY = self.padding(passwd, self.CBC_KEY_LEN)
        self.CBC_IV = self.padding(iv, 16)


if __name__ == '__main__':
    s = "妳好Hello@"
    cipher = AES_CFB("123456", ";")
    es = cipher.encrypt(s)
    print("KEY:123456 IV:; KEY_SIZE: 32 加密：" + es)
    print("KEY:123456 IV:; KEY_SIZE: 32 解密：" + cipher.decrypt(es))
    es = cipher.ex_encrypt(s, "12345", "54321")
    print("KEY:12345 IV:54321 KEY_SIZE: 32 加密：" + es)
    print("KEY:12345 IV:54321 KEY_SIZE: 32 解密：" + cipher.ex_decrypt(es, "12345", "54321"))
    print("CBC Test: ")
    cipher = AES_CBC("123456", "4321", 32)
    es = cipher.encrypt(s)
    print("KEY:123456 IV:4321 KEY_SIZE: 32 加密：" + es)
    print("KEY:123456 IV:4321 KEY_SIZE: 32 解密：" + cipher.decrypt(es))
    es = cipher.ex_encrypt(s, "12345", "54321")
    print("KEY:12345 IV:54321 KEY_SIZE: 32 加密：" + es)
    print("KEY:12345 IV:54321 KEY_SIZE: 32 解密：" + cipher.ex_decrypt(es, "12345", "54321"))
