@echo off
REM One-click pack for Windows: JAR + portable dir + .exe
REM .exe goes through pack-win-choose.bat (auto-detect WiX 3 / 4-5, ask if both).
REM Backup: pack-win.bat (WiX 3 only), pack-win-wix5.bat (WiX 4/5 only)
REM
REM Usage:
REM   pack.bat              all Windows artifacts (choose WiX if both installed)
REM   pack.bat jar          cross-platform fat JAR only
REM   pack.bat 3            force WiX 3 for .exe
REM   pack.bat 5            force WiX 4/5 for .exe
REM   pack.bat wix3|wix5    same
setlocal EnableExtensions

call "%~dp0common.bat" || exit /b 1

if /i not "%OS%"=="Windows_NT" (
  echo pack.bat is for Windows. On Linux or macOS use pack.sh
  exit /b 1
)

set "WIX_PICK="
if /i "%~1"=="jar" (
  call "%~dp0pack-jar.bat" || exit /b 1
  goto :done
)
if /i "%~1"=="3" set "WIX_PICK=3"
if /i "%~1"=="wix3" set "WIX_PICK=3"
if /i "%~1"=="old" set "WIX_PICK=3"
if /i "%~1"=="5" set "WIX_PICK=5"
if /i "%~1"=="4" set "WIX_PICK=5"
if /i "%~1"=="wix5" set "WIX_PICK=5"
if /i "%~1"=="wix4" set "WIX_PICK=5"
if /i "%~1"=="new" set "WIX_PICK=5"
if not "%~1"=="" if not defined WIX_PICK (
  echo Unknown: %~1
  echo Usage: pack.bat [jar^|3^|5^|wix3^|wix5]
  exit /b 1
)

echo.
echo ==^> 1/3 fat JAR
call "%~dp0pack-jar.bat" || exit /b 1
if not exist "%FAT_JAR%" (
  echo pack-jar.bat reported OK but %FAT_JAR% is missing
  exit /b 1
)
set SKIP_JAR=1
echo.
echo ==^> 2/3 portable app-image
call "%~dp0pack-appimage.bat" || exit /b 1
echo.
echo ==^> 3/3 Windows .exe ^(WiX^)
call "%~dp0pack-win-choose.bat" %WIX_PICK%
if errorlevel 1 (
  echo.
  echo Warning: .exe installer skipped. JAR and portable zip are in %DIST%
)

echo.
echo All three platforms:
echo   JAR (already built, runs on win/linux/mac): %FAT_JAR%
echo   Linux .deb: run ./pack.sh on Linux
echo   macOS .dmg: run ./pack.sh on macOS
echo   Windows .exe: this script ^(WiX via pack-win-choose.bat^)
echo Backup: pack-win.bat / pack-win-wix5.bat

:done
echo.
echo ==^> Done. Output in %DIST%:
dir /b "%DIST%"
if exist "%DIST%\%APP_NAME%\%CONSOLE_LAUNCHER%.exe" (
  echo.
  echo Daily:   "%DIST%\%APP_NAME%\%APP_NAME%.exe"
  echo Debug:   "%DIST%\%APP_NAME%\%CONSOLE_LAUNCHER%.exe"
)

endlocal
