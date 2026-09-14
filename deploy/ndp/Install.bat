REM 用于安装 .Net ndp48-x86-x64-allos-enu.exe
@echo off&color 17
if exist "%SystemRoot%\SysWOW64" path %path%;%windir%\SysNative;%SystemRoot%\SysWOW64;%~dp0
bcdedit >nul
if '%errorlevel%' NEQ '0' (goto UACPrompt) else (goto UACAdmin)
:UACPrompt
%1 start "" mshta vbscript:createobject("shell.application").shellexecute("""%~0""","::",,"runas",1)(window.close)&exit
exit /B
:UACAdmin
for %%I in ("%~dp0..\..") do set "PNAT_ROOT=%%~fI"
cd /d "%PNAT_ROOT%"
::echo 当前运行路径是：%CD%
echo 已获取管理员权限，开始安装。。。
REM .runtime\ndp\ndp.exe /install /quiet /norestart
.runtime\ndp\ndp.exe /install /norestart
pause
