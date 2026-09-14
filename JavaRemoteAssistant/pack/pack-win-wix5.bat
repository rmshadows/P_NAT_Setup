@echo off
REM Windows .exe via jpackage + WiX 4/5 (wix.exe). Needs JDK 24+.
REM Prefer unified entry: pack.bat  /  pack-win-choose.bat
REM This file is the WiX 4/5 backup entry.
setlocal EnableExtensions EnableDelayedExpansion

call "%~dp0common.bat" || exit /b 1

if /i not "%OS%"=="Windows_NT" (
  echo pack-win-wix5.bat only works on Windows
  exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
  echo Missing jpackage. Add JDK 24+ to PATH.
  exit /b 1
)

set "JAVA_SPEC="
for /f "tokens=3" %%v in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"') do set "JAVA_SPEC=%%v"
if not defined JAVA_SPEC (
  echo Cannot read java.specification.version
  exit /b 1
)
echo Java spec %JAVA_SPEC%
if !JAVA_SPEC! LSS 24 (
  echo WiX 4/5 needs jpackage from JDK 24 or newer. This JDK reports java.specification.version=!JAVA_SPEC!
  echo Use pack-win.bat ^(WiX 3^) or install JDK 24+.
  exit /b 1
)

set "GOTWIX="
where wix >nul 2>&1
if not errorlevel 1 (
  echo Using wix.exe from PATH
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX if exist "%USERPROFILE%\.dotnet\tools\wix.exe" (
  set "PATH=%USERPROFILE%\.dotnet\tools;%PATH%"
  echo Using wix.exe: %USERPROFILE%\.dotnet\tools
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX if exist "%ProgramFiles%\WiX Toolset v5.0\bin\wix.exe" (
  set "PATH=%ProgramFiles%\WiX Toolset v5.0\bin;%PATH%"
  echo Using wix.exe: %ProgramFiles%\WiX Toolset v5.0\bin
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX if exist "%ProgramFiles%\WiX Toolset v5\bin\wix.exe" (
  set "PATH=%ProgramFiles%\WiX Toolset v5\bin;%PATH%"
  echo Using wix.exe: %ProgramFiles%\WiX Toolset v5\bin
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX if exist "%ProgramFiles%\WiX Toolset v4.0\bin\wix.exe" (
  set "PATH=%ProgramFiles%\WiX Toolset v4.0\bin;%PATH%"
  echo Using wix.exe: %ProgramFiles%\WiX Toolset v4.0\bin
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX if exist "%ProgramFiles%\WiX Toolset v4\bin\wix.exe" (
  set "PATH=%ProgramFiles%\WiX Toolset v4\bin;%PATH%"
  echo Using wix.exe: %ProgramFiles%\WiX Toolset v4\bin
  wix --version
  set "GOTWIX=1"
)
if not defined GOTWIX (
  echo Missing wix.exe. JDK 24+ jpackage looks for WiX 4/5 as wix.exe, not candle.exe.
  echo Install example ^(WiX 5^):
  echo   dotnet tool install --global wix
  echo   wix extension add -g WixToolset.Util.wixext
  echo   wix extension add -g WixToolset.UI.wixext
  echo WiX 3: pack.bat 3   or   pack-win.bat
  exit /b 1
)

if "%SKIP_JAR%"=="1" (
  if not exist "%FAT_JAR%" call "%~dp0pack-jar.bat" || exit /b 1
) else (
  call "%~dp0pack-jar.bat" || exit /b 1
)

if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
mkdir "%JPACKAGE_INPUT%"
copy /Y "%FAT_JAR%" "%JPACKAGE_INPUT%\%JP_MAIN_JAR%" >nul

echo ==^> jpackage exe (WiX 4/5, wix.exe)
del /f /q "%DIST%\*.exe" 2>nul

if not exist "%PACK_DIR%\win-console.properties" (
  echo Missing %PACK_DIR%\win-console.properties
  exit /b 1
)
call "%PACK_DIR%\prepare-javafx.bat" || exit /b 1
set "JP_MODPATH="
if defined JAVAFX_JMODS (
  set JP_MODPATH=--module-path "%JAVAFX_JMODS%"
  if defined JAVA_HOME if exist "%JAVA_HOME%\jmods" set JP_MODPATH=--module-path "%JAVA_HOME%\jmods;%JAVAFX_JMODS%"
)
if defined WIN_ICON (
  jpackage --type exe --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --description "%APP_DESCRIPTION%" --dest "%DIST%" --input "%JPACKAGE_INPUT%" --main-jar "%JP_MAIN_JAR%" --main-class "%MAIN_CLASS%" %JP_MODPATH% --add-modules "%JP_MODULES%" --add-launcher "%CONSOLE_LAUNCHER%=%JP_CONSOLE_PROPS%" --win-shortcut --win-menu --icon "%WIN_ICON%" --java-options "%JAVA_OPTIONS%" --java-options "--enable-native-access=javafx.graphics"
) else (
  echo Warning: no .ico for --icon, using default Java icon
  jpackage --type exe --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --description "%APP_DESCRIPTION%" --dest "%DIST%" --input "%JPACKAGE_INPUT%" --main-jar "%JP_MAIN_JAR%" --main-class "%MAIN_CLASS%" %JP_MODPATH% --add-modules "%JP_MODULES%" --add-launcher "%CONSOLE_LAUNCHER%=%JP_CONSOLE_PROPS%" --win-shortcut --win-menu --java-options "%JAVA_OPTIONS%" --java-options "--enable-native-access=javafx.graphics"
)
if errorlevel 1 (
  echo jpackage exe failed.
  echo If wix.exe exited 144, install extensions:
  echo   wix extension add -g WixToolset.Util.wixext
  echo   wix extension add -g WixToolset.UI.wixext
  exit /b 1
)

echo OK exe in %DIST%:
dir /b "%DIST%\*.exe"
exit /b 0
