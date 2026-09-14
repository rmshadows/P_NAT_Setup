@echo off
REM Runnable JAR. Target machine needs a matching JDK.
setlocal EnableExtensions

call "%~dp0common.bat" || exit /b 1

if not defined MVN_CMD (
  echo Missing mvn.cmd. Add Apache Maven to PATH.
  echo Conda base often has no Maven. Install Maven, or: conda install -c conda-forge maven
  exit /b 1
)

if not exist "%DIST%" mkdir "%DIST%"

if "%SKIP_JAR%"=="1" if exist "%FAT_JAR%" (
  echo Skip Maven, jar exists: %FAT_JAR%
  exit /b 0
)

echo ==^> Maven package
if defined MAVEN_EXTRA_ARGS echo     extra: %MAVEN_EXTRA_ARGS%
echo     mvn %MVN_CMD%
echo     pom %ROOT%\pom.xml
echo     out %MAVEN_JAR%
call "%MVN_CMD%" -q -DskipTests %MAVEN_EXTRA_ARGS% -f "%ROOT%\pom.xml" package
if errorlevel 1 (
  echo mvn package failed
  exit /b 1
)

if not exist "%MAVEN_JAR%" (
  echo Missing after Maven: %MAVEN_JAR%
  echo Check MAVEN_JAR in app.conf
  exit /b 1
)

if not exist "%DIST%" mkdir "%DIST%"
copy /Y "%MAVEN_JAR%" "%FAT_JAR%"
if errorlevel 1 (
  echo copy failed: %MAVEN_JAR% -^> %FAT_JAR%
  exit /b 1
)
if not exist "%FAT_JAR%" (
  echo fat JAR missing after copy: %FAT_JAR%
  exit /b 1
)

echo OK %FAT_JAR%
echo Run: java -jar "%FAT_JAR%"

endlocal
exit /b 0
