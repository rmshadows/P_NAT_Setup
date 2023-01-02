REM 用于下载.Net4.8
REM https://dotnet.microsoft.com/en-us/download/dotnet-framework/net47
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
REM v4.8
REM wget\wget.exe -O ndp.exe download.visualstudio.microsoft.com/download/pr/2d6bb6b2-226a-4baa-bdec-798822606ff1/8494001c276a4b96804cde7829c04d7f/ndp48-x86-x64-allos-enu.exe

REM v4.6.2
wget\wget.exe -O ndp.exe download.visualstudio.microsoft.com/download/pr/8e396c75-4d0d-41d3-aea8-848babc2736a/80b431456d8866ebe053eb8b81a168b3/ndp462-kb3151800-x86-x64-allos-enu.exe
REM v4.6.2 Web
::wget\wget.exe -O ndp.exe download.visualstudio.microsoft.com/download/pr/8e396c75-4d0d-41d3-aea8-848babc2736a/570f7c7e1975df353a4652ae70b3e0ac/ndp462-kb3151802-web.exe
