REM ���ڰ�װTightVNC
@echo off&color 17
if exist "%SystemRoot%\SysWOW64" path %path%;%windir%\SysNative;%SystemRoot%\SysWOW64;%~dp0
bcdedit >nul
if '%errorlevel%' NEQ '0' (goto UACPrompt) else (goto UACAdmin)
:UACPrompt
%1 start "" mshta vbscript:createobject("shell.application").shellexecute("""%~0""","::",,"runas",1)(window.close)&exit
exit /B
:UACAdmin
cd /d "%~dp0"
::echo ��ǰ����·���ǣ�%CD%
echo �ѻ�ȡ����ԱȨ�ޡ�����
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
REM VIEWER_ASSOCIATE_VNC_EXTENSION=0 �ͻ�����չ������
REM SERVER_REGISTER_AS_SERVICE=0 �������չ������
REM SERVER_ADD_FIREWALL_EXCEPTION=1 ���ӷ���˷���ǽ����
REM VIEWER_ADD_FIREWALL_EXCEPTION=1 ���ӿͻ��˷���ǽ����
REM SERVER_ALLOW_SAS=1 SET_USEVNCAUTHENTICATION=1  ������Ctrl+Alt+Del��
REM SET_ACCEPTRFBCONNECTIONS=1 VALUE_OF_ACCEPTRFBCONNECTIONS=0 �ر�Web Server
REM SET_DISCONNECTACTION=1 VALUE_OF_DISCONNECTACTION=1 (���Ͽ����ӵ�ʱ�� 0 Nothing 1���� 2�ǳ�)
REM SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 (��ʾԶ�̱�ֽ)
REM SET_RFBPORT=1 VALUE_OF_RFBPORT=5900 (Զ�̶˿�)
REM SET_RUNCONTROLINTERFACE=1 VALUE_OF_RUNCONTROLINTERFACE=0 (����ʾ��ϵͳ����)
REM ������أ�
REM SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1    (�����������control operations)
REM SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=""  (run the server control interface, Ҫ��Requires USECONTROLAUTHENTICATION to be set to 1)
REM SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1  (����establishing connection)
REM SET_PASSWORD=1 VALUE_OF_PASSWORD="" (Requires USEVNCAUTHENTICATION to be set to 1)
REM SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=""  (���鿴)
REM ���ر���
copy /y conf\tightvnc.conf tmpScript.bat >nul 2>nul
CALL tmpScript.bat
REM del /f /q tmpScript.bat
echo ������壺%ctrl_passwd%
echo VNC���룺%vnc_passwd%
echo ���鿴��%viewonly_passwd%
echo �������̣�%hide_systray%
echo 5���ʼ��װ....
TIMEOUT /T 5
if %hide_systray% EQU 1 (
echo ��������
::ע�⣺ֻ��/quiet����ʹ�ð�װ����
msiexec /i res\tightvnc\tightvnc.msi /quiet /norestart ADDLOCAL="Server,Viewer" VIEWER_ASSOCIATE_VNC_EXTENSION=0 SERVER_REGISTER_AS_SERVICE=1 SERVER_ADD_FIREWALL_EXCEPTION=1 VIEWER_ADD_FIREWALL_EXCEPTION=1 SERVER_ALLOW_SAS=1 SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1 SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=%ctrl_passwd% SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1 SET_PASSWORD=1 VALUE_OF_PASSWORD=%vnc_passwd% SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=%viewonly_passwd% SET_RUNCONTROLINTERFACE=1 VALUE_OF_RUNCONTROLINTERFACE=0
) else (
echo ����������
msiexec /i res\tightvnc\tightvnc.msi /quiet /norestart ADDLOCAL="Server,Viewer" VIEWER_ASSOCIATE_VNC_EXTENSION=0 SERVER_REGISTER_AS_SERVICE=1 SERVER_ADD_FIREWALL_EXCEPTION=1 VIEWER_ADD_FIREWALL_EXCEPTION=1 SERVER_ALLOW_SAS=1 SET_REMOVEWALLPAPER=1 VALUE_OF_REMOVEWALLPAPER=0 SET_USECONTROLAUTHENTICATION=1 VALUE_OF_USECONTROLAUTHENTICATION=1 SET_CONTROLPASSWORD=1 VALUE_OF_CONTROLPASSWORD=%ctrl_passwd% SET_USEVNCAUTHENTICATION=1 VALUE_OF_USEVNCAUTHENTICATION=1 SET_PASSWORD=1 VALUE_OF_PASSWORD=%vnc_passwd% SET_VIEWONLYPASSWORD=1 VALUE_OF_VIEWONLYPASSWORD=%viewonly_passwd%
)
echo.
echo.
echo.
echo.
echo FINISHED!

pause