@echo off
REM Unified Windows .exe entry: detect WiX 3 / WiX 4-5, then pack.
REM   pack-win-choose.bat           interactive if both installed
REM   pack-win-choose.bat 3         force WiX 3  (also: wix3 / old)
REM   pack-win-choose.bat 5         force WiX 4/5 (also: 4 / wix5 / new)
REM   set WIX_VER=5 && pack-win-choose.bat
REM Backup direct entries: pack-win.bat (WiX 3), pack-win-wix5.bat (WiX 4/5)
REM From PowerShell:  cmd /c pack-win-choose.bat 5
setlocal EnableExtensions EnableDelayedExpansion

set "PACK_DIR=%~dp0"
if "%PACK_DIR:~-1%"=="\" set "PACK_DIR=%PACK_DIR:~0,-1%"

set "HAS_WIX3=0"
set "HAS_WIX5=0"
set "WIX3_HINT="
set "WIX5_HINT="
set "JAVA_SPEC="

call :detect_wix3
call :detect_wix5
call :detect_java_spec

echo.
echo WiX detection:
if "%HAS_WIX3%"=="1" (echo   [OK] WiX 3     candle.exe  %WIX3_HINT%) else (echo   [--] WiX 3     candle.exe not found)
if "%HAS_WIX5%"=="1" (echo   [OK] WiX 4/5   wix.exe     %WIX5_HINT%) else (echo   [--] WiX 4/5   wix.exe not found)
if defined JAVA_SPEC (echo   Java spec %JAVA_SPEC%) else (echo   Java spec unknown)
echo.

set "ARG=%~1"
if not defined ARG set "ARG=%WIX_VER%"

if /i "%ARG%"=="5" goto want5
if /i "%ARG%"=="4" goto want5
if /i "%ARG%"=="2" goto want5
if /i "%ARG%"=="wix5" goto want5
if /i "%ARG%"=="wix4" goto want5
if /i "%ARG%"=="new" goto want5
if /i "%ARG%"=="3" goto want3
if /i "%ARG%"=="1" goto want3
if /i "%ARG%"=="wix3" goto want3
if /i "%ARG%"=="old" goto want3

if defined ARG (
  echo Unknown WiX choice: %ARG%
  echo Use 3 ^(WiX 3^) or 5 ^(WiX 4/5^).
  exit /b 1
)

REM No arg: auto or ask
if "%HAS_WIX3%"=="1" if "%HAS_WIX5%"=="0" goto do3
if "%HAS_WIX3%"=="0" if "%HAS_WIX5%"=="1" goto check5_auto
if "%HAS_WIX3%"=="0" if "%HAS_WIX5%"=="0" goto none

echo Both WiX 3 and WiX 4/5 are available. Pick one:
echo   1^) WiX 3     candle.exe / light.exe     JDK 17+
echo   2^) WiX 4/5   wix.exe                    JDK 24+
echo.
set /p ARG=Choose 1 or 2 ^(Enter=auto by JDK^): 
if not defined ARG goto auto_by_jdk
if /i "%ARG%"=="2" goto want5
if /i "%ARG%"=="5" goto want5
if /i "%ARG%"=="wix5" goto want5
if /i "%ARG%"=="1" goto want3
if /i "%ARG%"=="3" goto want3
if /i "%ARG%"=="wix3" goto want3
echo Unknown: %ARG%
exit /b 1

:auto_by_jdk
if defined JAVA_SPEC if !JAVA_SPEC! GEQ 24 goto want5
goto want3

:check5_auto
goto want5

:want3
if "%HAS_WIX3%"=="0" (
  echo WiX 3 requested but candle.exe not found.
  echo Install WiX 3, or run: pack-win-choose.bat 5
  exit /b 1
)
goto do3

:want5
if "%HAS_WIX5%"=="0" (
  echo WiX 4/5 requested but wix.exe not found.
  echo Install: dotnet tool install --global wix
  echo Or run: pack-win-choose.bat 3
  exit /b 1
)
if defined JAVA_SPEC if !JAVA_SPEC! LSS 24 (
  echo WiX 4/5 needs JDK 24+. This JDK reports java.specification.version=!JAVA_SPEC!
  if "%HAS_WIX3%"=="1" (
    echo Falling back to WiX 3.
    goto do3
  )
  exit /b 1
)
goto do5

:do5
echo ==^> WiX 4/5  ^(pack-win-wix5.bat^)
call "%PACK_DIR%\pack-win-wix5.bat"
exit /b %ERRORLEVEL%

:do3
echo ==^> WiX 3  ^(pack-win.bat^)
call "%PACK_DIR%\pack-win.bat"
exit /b %ERRORLEVEL%

:none
echo No WiX found. jpackage --type exe needs WiX.
echo   WiX 3:  candle.exe  https://wixtoolset.org  ^(or unpack wix311-binaries to PATH^)
echo   WiX 5:  dotnet tool install --global wix
echo           wix extension add -g WixToolset.Util.wixext
echo           wix extension add -g WixToolset.UI.wixext
echo JAR / portable zip do not need WiX.
exit /b 1

:detect_java_spec
set "JAVA_SPEC="
for /f "tokens=3" %%v in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"') do set "JAVA_SPEC=%%v"
exit /b 0

:detect_wix3
set "HAS_WIX3=0"
where candle >nul 2>&1
if not errorlevel 1 (
  set "HAS_WIX3=1"
  set "WIX3_HINT=(PATH)"
  exit /b 0
)
if defined WIX if exist "%WIX%\bin\candle.exe" (
  set "HAS_WIX3=1"
  set "WIX3_HINT=(%WIX%\bin)"
  exit /b 0
)
if exist "%USERPROFILE%\Program\wix311-binaries\candle.exe" (
  set "HAS_WIX3=1"
  set "WIX3_HINT=(%USERPROFILE%\Program\wix311-binaries)"
  exit /b 0
)
if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\candle.exe" (
  set "HAS_WIX3=1"
  set "WIX3_HINT=(WiX Toolset v3.14)"
  exit /b 0
)
if exist "%ProgramFiles(x86)%\WiX Toolset v3.11\bin\candle.exe" (
  set "HAS_WIX3=1"
  set "WIX3_HINT=(WiX Toolset v3.11)"
  exit /b 0
)
exit /b 0

:detect_wix5
set "HAS_WIX5=0"
where wix >nul 2>&1
if not errorlevel 1 (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(PATH)"
  exit /b 0
)
if exist "%USERPROFILE%\.dotnet\tools\wix.exe" (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(%USERPROFILE%\.dotnet\tools)"
  exit /b 0
)
if exist "%ProgramFiles%\WiX Toolset v5.0\bin\wix.exe" (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(WiX Toolset v5.0)"
  exit /b 0
)
if exist "%ProgramFiles%\WiX Toolset v5\bin\wix.exe" (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(WiX Toolset v5)"
  exit /b 0
)
if exist "%ProgramFiles%\WiX Toolset v4.0\bin\wix.exe" (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(WiX Toolset v4.0)"
  exit /b 0
)
if exist "%ProgramFiles%\WiX Toolset v4\bin\wix.exe" (
  set "HAS_WIX5=1"
  set "WIX5_HINT=(WiX Toolset v4)"
  exit /b 0
)
exit /b 0
