@echo off
REM Windows .exe installer via jpackage. WiX 3 only (candle.exe + light.exe).
REM Prefer unified entry: pack.bat  /  pack-win-choose.bat
REM This file is the WiX 3 backup entry.
setlocal EnableExtensions EnableDelayedExpansion

call "%~dp0common.bat" || exit /b 1

if /i not "%OS%"=="Windows_NT" (
  echo pack-win.bat only works on Windows
  exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
  echo Missing jpackage. Add a JDK with jpackage to PATH.
  exit /b 1
)

call :find_wix
if errorlevel 1 exit /b 1

REM JDK 24+ prefers wix.exe over candle.exe. Inline (not call :label):
REM cmd misses a second call-label when this file has Unix LF endings.
set "NEWPATH="
for %%A in ("%PATH:;=";"%") do (
  set "DIR=%%~A"
  if not "!DIR!"=="" (
    if exist "!DIR!\wix.exe" (
      echo Note: hiding !DIR!\wix.exe so jpackage stays on WiX 3
    ) else if defined NEWPATH (
      set "NEWPATH=!NEWPATH!;!DIR!"
    ) else (
      set "NEWPATH=!DIR!"
    )
  )
)
if defined NEWPATH set "PATH=!NEWPATH!"

if "%SKIP_JAR%"=="1" (
  if not exist "%FAT_JAR%" call "%~dp0pack-jar.bat" || exit /b 1
) else (
  call "%~dp0pack-jar.bat" || exit /b 1
)

if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
mkdir "%JPACKAGE_INPUT%"
copy /Y "%FAT_JAR%" "%JPACKAGE_INPUT%\%JP_MAIN_JAR%" >nul

echo ==^> jpackage exe (WiX 3)
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
  echo jpackage exe failed
  exit /b 1
)

echo OK exe in %DIST%:
dir /b "%DIST%\*.exe"
exit /b 0

:find_wix
where candle >nul 2>&1
if not errorlevel 1 exit /b 0
if defined WIX if exist "%WIX%\bin\candle.exe" (
  set "PATH=%WIX%\bin;%PATH%"
  echo Using WiX from %WIX%\bin
  exit /b 0
)
if exist "%USERPROFILE%\Program\wix311-binaries\candle.exe" (
  set "PATH=%USERPROFILE%\Program\wix311-binaries;%PATH%"
  echo Using WiX: %USERPROFILE%\Program\wix311-binaries
  exit /b 0
)
if exist "%ProgramFiles(x86)%\WiX Toolset v3.14\bin\candle.exe" (
  set "PATH=%ProgramFiles(x86)%\WiX Toolset v3.14\bin;%PATH%"
  echo Using WiX Toolset v3.14
  exit /b 0
)
if exist "%ProgramFiles(x86)%\WiX Toolset v3.11\bin\candle.exe" (
  set "PATH=%ProgramFiles(x86)%\WiX Toolset v3.11\bin;%PATH%"
  echo Using WiX Toolset v3.11
  exit /b 0
)
echo Missing WiX 3. jpackage --type exe on this script needs candle.exe and light.exe.
echo You already have binaries if this folder exists:
echo   %USERPROFILE%\Program\wix311-binaries
echo Add that folder to user PATH, or install WiX 3 from https://wixtoolset.org
echo For WiX 4/5 (JDK 24+): pack.bat 5   or   pack-win-wix5.bat
echo JAR and portable zip do not need WiX; they are already in dist\
exit /b 1
