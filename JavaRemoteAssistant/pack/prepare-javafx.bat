@echo off
REM Copy this-platform JavaFX modular JARs. Sets JAVAFX_JMODS for jpackage --module-path.
REM Called from pack-*.bat that invoke jpackage. Do not use setlocal.

echo %JP_MODULES% | findstr /i /c:"javafx." >nul
if errorlevel 1 exit /b 0

if defined JAVAFX_JMODS if exist "%JAVAFX_JMODS%" exit /b 0

set "JAVAFX_DEST=%ROOT%\target\javafx-runtime"
if exist "%JAVAFX_DEST%" rmdir /s /q "%JAVAFX_DEST%"
mkdir "%JAVAFX_DEST%"

if not defined MVN_CMD (
  echo Missing mvn.cmd. Add Apache Maven to PATH.
  exit /b 1
)

echo ==^> copy JavaFX module JARs for this platform
call "%MVN_CMD%" -q -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeGroupIds=org.openjfx -DoutputDirectory="%JAVAFX_DEST%" -DoverWriteIfNewer=true
if errorlevel 1 (
  echo Failed to copy org.openjfx JARs
  exit /b 1
)

dir /b "%JAVAFX_DEST%\javafx-*.jar" >nul 2>&1
if errorlevel 1 (
  echo No JavaFX JARs in %JAVAFX_DEST%. Check org.openjfx in pom.xml
  exit /b 1
)

set "JAVAFX_JMODS=%JAVAFX_DEST%"
echo JavaFX modules: %JAVAFX_JMODS%
exit /b 0
