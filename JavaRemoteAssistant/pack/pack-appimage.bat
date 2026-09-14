@echo off
REM Portable app-image with bundled JRE
setlocal EnableExtensions

call "%~dp0common.bat" || exit /b 1

where jpackage >nul 2>&1
if errorlevel 1 (
  echo Missing jpackage. Add a JDK with jpackage to PATH.
  exit /b 1
)

if "%SKIP_JAR%"=="1" (
  if not exist "%FAT_JAR%" call "%~dp0pack-jar.bat" || exit /b 1
) else (
  call "%~dp0pack-jar.bat" || exit /b 1
)

if not exist "%FAT_JAR%" (
  echo Missing fat JAR: %FAT_JAR%
  echo Run pack-jar.bat first.
  exit /b 1
)
if exist "%JPACKAGE_INPUT%" rmdir /s /q "%JPACKAGE_INPUT%"
if exist "%DIST%\%APP_NAME%" rmdir /s /q "%DIST%\%APP_NAME%"
mkdir "%JPACKAGE_INPUT%"
copy /Y "%FAT_JAR%" "%JPACKAGE_INPUT%\%JP_MAIN_JAR%"
if errorlevel 1 (
  echo copy failed: %FAT_JAR% -^> %JPACKAGE_INPUT%\%JP_MAIN_JAR%
  exit /b 1
)

echo ==^> jpackage app-image
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
  jpackage --type app-image --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --description "%APP_DESCRIPTION%" --dest "%DIST%" --input "%JPACKAGE_INPUT%" --main-jar "%JP_MAIN_JAR%" --main-class "%MAIN_CLASS%" %JP_MODPATH% --add-modules "%JP_MODULES%" --add-launcher "%CONSOLE_LAUNCHER%=%JP_CONSOLE_PROPS%" --icon "%WIN_ICON%" --java-options "%JAVA_OPTIONS%" --java-options "--enable-native-access=javafx.graphics"
) else (
  echo Warning: no .ico for --icon, using default Java icon
  jpackage --type app-image --name "%APP_NAME%" --app-version "%APP_VERSION%" --vendor "%VENDOR%" --description "%APP_DESCRIPTION%" --dest "%DIST%" --input "%JPACKAGE_INPUT%" --main-jar "%JP_MAIN_JAR%" --main-class "%MAIN_CLASS%" %JP_MODPATH% --add-modules "%JP_MODULES%" --add-launcher "%CONSOLE_LAUNCHER%=%JP_CONSOLE_PROPS%" --java-options "%JAVA_OPTIONS%" --java-options "--enable-native-access=javafx.graphics"
)
if errorlevel 1 (
  echo jpackage app-image failed
  exit /b 1
)

set "ARCHIVE=%DIST%\%APP_NAME%-%VERSION%-windows-x64.zip"
if /i "%PROCESSOR_ARCHITECTURE%"=="ARM64" set "ARCHIVE=%DIST%\%APP_NAME%-%VERSION%-windows-arm64.zip"
if exist "%ARCHIVE%" del /f /q "%ARCHIVE%"
tar -a -c -f "%ARCHIVE%" -C "%DIST%" %APP_NAME%
if errorlevel 1 (
  echo zip failed, need tar from Windows 10 or later
  exit /b 1
)

echo OK %DIST%\%APP_NAME%\
echo OK %ARCHIVE%
if exist "%DIST%\%APP_NAME%\%CONSOLE_LAUNCHER%.exe" (
  echo Debug: %DIST%\%APP_NAME%\%CONSOLE_LAUNCHER%.exe
)

endlocal
