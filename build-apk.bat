@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1"
if errorlevel 1 (
  echo.
  echo APK build did not complete. Read the message above.
  pause
  exit /b 1
)
echo.
pause
