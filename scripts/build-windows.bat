@echo off
rem Flow Download Manager — Windows build script
rem Builds the app + Inno Setup installer.
rem
rem Requirements:
rem   - JDK 21 (set JAVA_HOME)
rem   - Inno Setup 7 (in PATH or at default location)
rem
rem Usage:
rem   scripts\build-windows.bat
rem
rem Output:
rem   build\installer\Flow-Setup-1.0.0.exe

echo === Flow Download Manager — Windows Build ===
echo.

if "%JAVA_HOME%"=="" (
    echo Error: JAVA_HOME is not set.
    echo Download JDK 21 from: https://adoptium.net/
    exit /b 1
)

echo JAVA_HOME: %JAVA_HOME%
echo.

echo ^>^>^> Building release distributable...
call gradlew.bat :desktop:app:createReleaseDistributable
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)

echo.
echo ^>^>^> Building Inno Setup installer...

set ISCC="C:\Program Files\Inno Setup 7\ISCC.exe"
if not exist %ISCC% set ISCC="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist %ISCC% (
    echo Warning: Inno Setup not found. Skipping installer.
    echo Download from: https://jrsoftware.org/isdl.php
    goto :done
)

%ISCC% installer\flow-setup.iss
if errorlevel 1 (
    echo Installer build failed!
    exit /b 1
)

:done
echo.
echo === Build complete ===
echo.
echo App:       desktop\app\build\compose\binaries\main-release\app\Flow\Flow.exe
echo Installer: build\installer\Flow-Setup-1.0.0.exe
echo.
