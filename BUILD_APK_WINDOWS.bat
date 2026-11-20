@echo off
setlocal
echo ============================================
echo   LUMORIE Pic a Box - Windows APK build
echo ============================================
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle command not found.
  echo Install Android Studio and Gradle 8.13, then reopen this window.
  echo Or use the GitHub Actions method in README_TH.md.
  pause
  exit /b 1
)
gradle --no-daemon assembleDebug
if errorlevel 1 (
  echo Build failed. Please send the full error text.
  pause
  exit /b 1
)
echo.
echo APK output:
echo app\build\outputs\apk\debug\app-debug.apk
pause
