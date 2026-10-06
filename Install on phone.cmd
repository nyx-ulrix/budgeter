@echo off
rem Double-click to build Budgeter and install it on every Android device plugged into this PC.
rem The phone needs USB debugging on (Settings > About > tap Build number 7 times > Developer options).
setlocal enabledelayedexpansion
cd /d "%~dp0"
title Budgeter - install on phone

if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"

echo.
echo  BUDGETER ^| building...
echo.
call "%~dp0gradlew.bat" :app:assembleRelease -q
if errorlevel 1 goto fail
set "APK=%~dp0app\build\outputs\apk\release\app-release.apk"

:wait
set COUNT=0
set UNAUTH=0
for /f "skip=1 tokens=1,2" %%a in ('call "%ADB%" devices') do (
  if "%%b"=="device" set /a COUNT+=1
  if "%%b"=="unauthorized" set /a UNAUTH+=1
)
if !COUNT!==0 (
  if !UNAUTH! gtr 0 (echo  Unlock the phone and tap "Allow" on the USB debugging prompt...) else (echo  Plug in an Android phone with USB debugging on...)
  timeout /t 3 /nobreak >nul
  goto wait
)

set FAILED=0
for /f "skip=1 tokens=1,2" %%a in ('call "%ADB%" devices') do if "%%b"=="device" (
  for /f "delims=" %%m in ('call "%ADB%" -s %%a shell getprop ro.product.model') do set "MODEL=%%m"
  echo  Installing on !MODEL! [%%a]...
  call "%ADB%" -s %%a install -r "%APK%" > "%TEMP%\budgeter-install.txt" 2>&1
  findstr /c:"Success" "%TEMP%\budgeter-install.txt" >nul
  if errorlevel 1 (
    type "%TEMP%\budgeter-install.txt"
    findstr /c:"UPDATE_INCOMPATIBLE" "%TEMP%\budgeter-install.txt" >nul && echo  The phone has a copy signed with a different key. Uninstall Budgeter on the phone, then run this again.
    set FAILED=1
  ) else (
    call "%ADB%" -s %%a shell am start -n com.nyxulrix.budgeter/.MainActivity >nul 2>&1
    echo  Done. Budgeter is open on !MODEL!.
  )
)
echo.
if !FAILED!==1 goto fail
timeout /t 5
exit /b 0

:fail
echo.
echo  Something went wrong. Read the messages above.
pause
exit /b 1
