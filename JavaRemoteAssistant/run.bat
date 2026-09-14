@echo off
setlocal EnableExtensions
cd /d "%~dp0" || exit /b 1

set "JRA_DIR=%CD%"
for %%I in ("%JRA_DIR%\..") do set "PKG_ROOT=%%~fI"
if not exist "%PKG_ROOT%\conf\app.conf" if exist "%JRA_DIR%\..\..\conf\app.conf" (
  for %%I in ("%JRA_DIR%\..\..") do set "PKG_ROOT=%%~fI"
)

REM Java reads these. Do not echo %%PNAT_ROOT%% in this file (%%P is eaten).
set "PNAT_ROOT=%PKG_ROOT%"
set "PNAT_CONF=%PKG_ROOT%\conf"
set "PNAT_RES=%PKG_ROOT%\res"

echo JRA dir:   %JRA_DIR%
echo PKG root:  %PKG_ROOT%
echo.

where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: java not found. Install JDK 17+ and add it to PATH.
  goto fail
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo ERROR: mvn not found. Install Maven and add it to PATH.
  goto fail
)

echo Starting: mvn -DskipTests javafx:run
echo Close this window to stop JRA.
echo.
call mvn -DskipTests javafx:run
if errorlevel 1 goto fail
exit /b 0

:fail
echo.
echo Failed. Check JDK 17+ / Maven / PATH.
pause
exit /b 1
