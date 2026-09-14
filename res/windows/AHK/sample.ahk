Run "notepad.exe"
Sleep 2000
Send "^ "
Send "{Space}"
Send "Hi，"
Send "这是一个Auto Hot Key v2.0 alpha版本的演示。"
Send "{Enter}"
Send "{Blind}{Text}具体的使用请查阅官方文档：https://lexikos.github.io/v2/docs/AutoHotkey.htm"
; 打开一个网址:
; Run "https://www.autohotkey.com"
Send "{Enter}"
Sleep 500
Send "{Blind}{Text}关于Java Remote Assistant (JRA)"
Send "{Enter}"
Send "{Blind}{Text}JRA是一款协助搭建TightVNC远程协助环境的软件。"
Send "{Enter}"
Send "{Blind}{Text}首先，肯定没有Teamviewer好用，因为部分按键快捷键TightVNC不支持。"
Send "{Enter}"
Send "{Blind}{Text}但这是目前我能想到的，能够摆脱Teamviewer误判我商业用途的办法（AnyDesk也不错，但连接不稳定，速度慢）。"
Send "{Enter}"
Send "{Blind}{Text}JRA运行在受控端，从受控端逆向连接到控制端的TightVNC Viewer。所以控制端需要进行内网穿透，这么做是为了方便受控端使用。"
Send "{Enter}"
Send "{Blind}{Text}首先是连接：点击加载远程桌面，有UAC的会弹出管理员权限询问和Windows防火墙询问，同意就是。断线重连直接点就是，一样的。"
Send "{Enter}"
Send "{Blind}{Text}记得输入控制端IP地址和端口，要不默认谷歌8.8.8.8  /0.0/"
Send "{Enter}"
Send "{Blind}{Text}断开远程桌面：你懂的，不解释。"
Send "{Enter}"
Send "{Blind}{Text}任务管理器：打开任务管理器。"
Send "{Enter}"
Send "{Blind}{Text}显示桌面：回到桌面。"
Send "{Enter}"
Send "{Blind}{Text}切换桌面：鼠标左键：下一个桌面。鼠标右键：上一个桌面。鼠标中键：新建虚拟桌面。"
Send "{Enter}"
Send "{Blind}{Text}关闭输入法：模拟Ctrl+Space。"
Send "{Enter}"
Send "{Blind}{Text}Ctrl、Shift、Alt、开始键锁定：点一下锁定，再点一下松开。只能单独用，不能同时锁定两个(需要的自己修改源代码，本人能力不够，而且实在没时间，目前还处于无业游民状态晓得吧。。)"
Send "{Enter}"
Send "{Blind}{Text}这个锁定干嘛用的呢？TightVNC有些按键没法按的，比如“开始键+R”，有了这个你可以锁定开始键，按一下R，远程桌面就会显示‘运行’了"
Send "{Enter}"
Send "{Blind}{Text}自定义按键：自己写AutoHotKey脚本，放在当前目录，输入脚本名字（没有扩展名）。左键：5秒延时，右键：10秒延时，中键：20秒延时。"
Send "{Enter}"
Send "{Blind}{Text}屏幕键盘：调用屏幕键盘。"
Send "{Enter}"
Send "{Blind}{Text}关于：同理，不解释。"
Send "{Enter}"
Send "{Enter}"
Send "{Blind}{Text}======================下面打印README(旧版)=========================="
Send "{Enter}"
Send "{Blind}{Text} # Java Remote Assistant远程协作助手"
Send "{Enter}"
Send "{Blind}{Text} - 开源、免费的远程协助工具TightVNC是个很棒的选择，但远程协助受控方一般是电脑小白，搭建TightVNC Server可能对他们来说有点困难。于是我写了这个JRA助手帮我更方便的协助小白朋友处理电脑的一些小问题。"
Send "{Enter}"
Send "{Blind}{Text} #### 连接过程："
Send "{Enter}"
Send "{Blind}{Text}       * 控制方：向对方发送JRA压缩包。搭建内网穿透，打开VNC_Viewer监听模式，告诉小白 **远程地址** ，等待逆向连接。"
Send "{Enter}"
Send "{Blind}{Text}       * 被控方：解压JRA，双击JRA.exe运行。点击加载远程桌面，在询问窗口中输入 **远程地址** ，等待大白(。。？)连接成功。"
Send "{Enter}"
Send "{Blind}{Text}       * 注意：记得告诉小白，要给予JRA运行权限，UAC要同意，防火墙也要同意（直接跟小白说有弹窗全同意、确认就是了）。"
Send "{Enter}"
Send "{Blind}{Text} - 当前版本：4.1（截止2020-08-04）"
Send "{Enter}"
Send "{Blind}{Text} 基于[TightVNC](https://www.tightvnc.com/)（JRA是受控端，控制端请到TightVNC官网下载Viewer，当然，其他VNC Viewer也行），可以用[Auto Hot Key](https://www.autohotkey.com/)脚本自定义按键等。Java Remote Assistant主要是帮助我更方便的使用TightVNC和朋友进行远程协作。因为Teamviewer有时会误判我商业用途，导致我一段时间用不了，破解版我不知道为啥，我的电脑用不了，一直显示未知原因，正版报价太贵，我也就每个月用个十次估计，帮朋友弄弄电脑啥的，划不来（AnyDesk也不错，但连接不稳定，速度慢）。所以采用VNC咯， **但VNC没有加密** ，我是 **建议弄个VPN或者SSH隧道** ，这样比较安全。这个玩意儿匆忙写的，bug肯定有，优化不好，但能用就是。"
Send "{Enter}"
Send "{Blind}{Text} -  ~~**注意：** TightVNC在 _**Windows的UAC**_ 下会闪退，断开连接，我也不懂，对吧……所以记得有UAC的，电话联系下对方。。帮忙点个确认，或者关闭UAC（不推荐）。~~【已解决，3.0及以上版本采用服务方式入驻，解决app模式的UAC地雷问题】"
Send "{Enter}"
Send "{Blind}{Text} -  _**只能运行在Windows系统！**_ (Linux那么方便，都会用Linux了还用我这个干嘛呀。。这个是给小白的VNC被控端，一键搭建VNC Server。再说，Linux用SSH够了，没有SSH解决不了的，有的话，就是`sudo rm -rf /`【开玩笑】 [。。不要真的给我执行这命令惹，晕])"
Send "{Enter}"
Send "{Blind}{Text} # 使用帮助"
Send "{Enter}"
Send "{Blind}{Text} -  **加载远程桌面** ：有UAC的会弹出管理员权限询问和Windows防火墙询问，同意就是。断线重连直接点就是，一样的。记得输入控制端IP地址和端口，要不默认谷歌8.8.8.8:6666  /0.0/"
Send "{Enter}"
Send "{Blind}{Text} -  **断开远程桌面** ：你懂的，不解释。"
Send "{Enter}"
Send "{Blind}{Text} -  **任务管理器** ：打开任务管理器。"
Send "{Enter}"
Send "{Blind}{Text} -  **显示桌面** ：回到桌面。"
Send "{Enter}"
Send "{Blind}{Text} -  **切换桌面** ：鼠标左键：下一个虚拟桌面。鼠标右键：上一个虚拟桌面。鼠标中键（滚轮按下去）：新建虚拟桌面。"
Send "{Enter}"
Send "{Blind}{Text} -  **关闭输入法** ：模拟Ctrl+Space。"
Send "{Enter}"
Send "{Blind}{Text} -  **Ctrl、Shift、Alt、开始键锁定** ：点一下锁定，再点一下松开。只能单独用，不能同时锁定两个(需要的自己修改源代码，本人能力不够，而且实在没时间，目前还处于无业游民状态晓得吧。。)这个锁定干嘛用的呢？TightVNC有些按键没法按的，比如“开始键+R”，有了这个你可以锁定开始键，按一下R，远程桌面就会显示‘运行’了"
Send "{Enter}"
Send "{Blind}{Text} -  **自定义按键** ：自己写AutoHotKey脚本，放在当前目录，输入脚本名字（没有扩展名）。左键：5秒延时，右键：10秒延时，中键：20秒延时。"
Send "{Enter}"
Send "{Blind}{Text} -  **屏幕键盘** ：调用屏幕键盘，注意，有的电脑不支持Ctrl+Win+O快捷键调用屏幕键盘，所以没法用。"
Send "{Enter}"
Send "{Blind}{Text} -  **关于** ：同理，不解释。"
Send "{Enter}"
Send "{Blind}{Text} # 感谢"
Send "{Enter}"
Send "{Blind}{Text} 感谢[ **TightVNC** ](https://www.tightvnc.com/) 和 [ **TigerVNC** ](https://tigervnc.org/) 作者们的辛勤劳作，很棒的项目，特别是逆向连接简直妙极了哈哈哈哈哈哈。Windows这边我优先使用TightVNC，Linux平台我优先使用TigerVNC，都是很棒的项目，再次感谢！"
Send "{Enter}"
Send "{Blind}{Text} Demo打印完毕。"




















