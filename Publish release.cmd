@echo off
rem Publishes a new version: runs the tests, tags it and pushes the tag.
rem GitHub Actions then builds the signed APK and creates the release; installed apps offer the update.
setlocal
cd /d "%~dp0"
title Budgeter - publish release
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

for /f "delims=" %%t in ('git describe --tags --abbrev^=0 2^>nul') do set "LAST=%%t"
if not defined LAST set "LAST=none"
echo  Last release: %LAST%
set /p VERSION= New version (like 0.3.0):
if "%VERSION%"=="" exit /b 1
set "VERSION=%VERSION:v=%"

git diff --quiet && git diff --cached --quiet
if errorlevel 1 (echo  Commit your changes first. & pause & exit /b 1)

echo  Running tests...
call "%~dp0gradlew.bat" :app:testDebugUnitTest -q
if errorlevel 1 (echo  Tests failed, nothing published. & pause & exit /b 1)

git tag -a v%VERSION% -m "Budgeter %VERSION%" || (pause & exit /b 1)
git push origin HEAD v%VERSION% || (pause & exit /b 1)
echo.
echo  Pushed v%VERSION%. GitHub is building it now:
echo  https://github.com/nyx-ulrix/budgeter/actions
pause
