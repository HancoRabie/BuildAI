@echo off
setlocal
set "GRADLE_VERSION=8.10.2"
set "BOOTSTRAP_DIR=%~dp0.gradle-bootstrap"
set "GRADLE_HOME=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VERSION%...
  if not exist "%BOOTSTRAP_DIR%" mkdir "%BOOTSTRAP_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_FILE%' '%BOOTSTRAP_DIR%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
