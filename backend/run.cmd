@echo off
setlocal EnableExtensions
cd /d "%~dp0"

for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "MVN=%ROOT%\.tools\apache-maven-3.9.9\bin\mvn.cmd"

if not exist "%MVN%" (
  echo Missing Maven at "%MVN%"
  exit /b 1
)

rem Prefer a real JDK (Maven needs javac). Fall back to common Windows installs.
if not defined JAVA_HOME (
  for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do set "JAVA_HOME=%%~fD"
)
if not defined JAVA_HOME (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%~fD"
)
if not defined JAVA_HOME (
  for /d %%D in ("C:\Program Files\Java\jdk-21*") do set "JAVA_HOME=%%~fD"
)
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\javac.exe" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  ) else (
    echo JAVA_HOME is set but javac was not found at "%JAVA_HOME%\bin\javac.exe"
    echo Install JDK 21 and set JAVA_HOME to that JDK folder.
    exit /b 1
  )
) else (
  where javac >nul 2>&1
  if errorlevel 1 (
    echo No JDK found. Install Microsoft OpenJDK 21, then re-run:
    echo   winget install --id Microsoft.OpenJDK.21 -e
    exit /b 1
  )
)

rem Clear any leftover SQL Server auth DLL path from older setups
set "JAVA_TOOL_OPTIONS="

echo Starting SelfSync API with embedded H2 database (./data/selfsync)...
echo Using JAVA_HOME=%JAVA_HOME%
call "%MVN%" spring-boot:run
