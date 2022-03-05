REM 用于安装TightVNC
@echo off&color 17
if exist "%SystemRoot%\SysWOW64" path %path%;%windir%\SysNative;%SystemRoot%\SysWOW64;%~dp0
bcdedit >nul
if '%errorlevel%' NEQ '0' (goto UACPrompt) else (goto UACAdmin)
:UACPrompt
%1 start "" mshta vbscript:createobject("shell.application").shellexecute("""%~0""","::",,"runas",1)(window.close)&exit
exit /B
:UACAdmin
cd /d "%~dp0"
::echo 当前运行路径是：%CD%
echo 已获取管理员权限。。。
echo.
echo.
echo.
echo.
echo.
echo.
echo.
echo install TightVNC
echo.
echo.
echo.
echo.
echo.
echo.
echo.
echo INSTALLING AND CONFIGURING ... ... ... .. . .
REM VIEWER_ASSOCIATE_VNC_EXTENSION=0 客户端扩展名关联
REM SERVER_REGISTER_AS_SERVICE=0 服务端扩展名关联
REM SERVER_ADD_FIREWALL_EXCEPTION=1 添加服务端防火墙规则
REM VIEWER_ADD_FIREWALL_EXCEPTION=1 添加客户端防火墙规则
REM SERVER_ALLOW_SAS=1 SET_USEVNCAUTHENTICATION=1  允许”Ctrl+Alt+Del“
REM SET_ACCEPTRFBCONNECTIONS=1 VALUE_OF_ACCEPTRFBCONNECTIONS=0 关闭Web Server
REM SET_DISCONNECTACTION=1 VALUE_OF_DISCONNECTACTION=1 (当断开连接的时候 0 Nothing 1锁屏 2登出)
REM SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 (显示远程壁纸)
REM SET_RFBPORT=1 VALUE_OF_RFBPORT=5900 (远程端口)
REM SET_RUNCONTROLINTERFACE=1 VALUE_OF_RUNCONTROLINTERFACE=0 (不显示在系统托盘)
REM 密码相关：
REM SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1    (控制面板密码control operations)
REM SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=""  (run the server control interface, 要求Requires USECONTROLAUTHENTICATION to be set to 1)
REM SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1  (控制establishing connection)
REM SET_PASSWORD=1 VALUE_OF_PASSWORD="" (Requires USEVNCAUTHENTICATION to be set to 1)
REM SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=""  (仅查看)
REM 加载变量
copy /y conf\tightvnc.conf tmpScript.bat >nul 2>nul
CALL tmpScript.bat
REM del /f /q tmpScript.bat
echo 控制面板：%ctrl_passwd%
echo VNC密码：%vnc_passwd%
echo 仅查看：%viewonly_passwd%
echo 隐藏托盘：%hide_systray%
echo 5秒后开始安装....
TIMEOUT /T 5
if %hide_systray% EQU 1 (
echo 隐藏托盘
::注意：只有/quiet才能使用安装参数
msiexec /i res\tightvnc\tightvnc.msi /quiet /norestart ADDLOCAL="Server,Viewer" VIEWER_ASSOCIATE_VNC_EXTENSION=0 SERVER_REGISTER_AS_SERVICE=1 SERVER_ADD_FIREWALL_EXCEPTION=1 VIEWER_ADD_FIREWALL_EXCEPTION=1 SERVER_ALLOW_SAS=1 SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1 SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=%ctrl_passwd% SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1 SET_PASSWORD=1 VALUE_OF_PASSWORD=%vnc_passwd% SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=%viewonly_passwd% SET_RUNCONTROLINTERFACE=1 VALUE_OF_RUNCONTROLINTERFACE=0
) else (
echo 不隐藏托盘
msiexec /i res\tightvnc\tightvnc.msi /quiet /norestart ADDLOCAL="Server,Viewer" VIEWER_ASSOCIATE_VNC_EXTENSION=0 SERVER_REGISTER_AS_SERVICE=1 SERVER_ADD_FIREWALL_EXCEPTION=1 VIEWER_ADD_FIREWALL_EXCEPTION=1 SERVER_ALLOW_SAS=1 SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1 SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=%ctrl_passwd% SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1 SET_PASSWORD=1 VALUE_OF_PASSWORD=%vnc_passwd% SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=%viewonly_passwd%
)
echo.
echo.
echo.
echo.
echo FINISHED!

pause
