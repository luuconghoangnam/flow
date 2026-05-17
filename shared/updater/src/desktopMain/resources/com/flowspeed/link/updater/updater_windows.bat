@echo off
rem Flow Download Manager — Windows updater script
rem Called by the app to apply an update while it is not running.

set APP_NAME=Flow
call :main "%1" "%2"
goto :eof

:stopApp
echo Stopping %APP_NAME%...
taskkill /IM %APP_NAME%.exe /F 2>nul
call :waitForTermination
echo %APP_NAME% stopped.
goto :eof

:waitForTermination
tasklist /FI "IMAGENAME eq %APP_NAME%.exe" | find /I "%APP_NAME%.exe" >nul 2>&1
if errorlevel 1 goto :eof
ping 127.0.0.1 -n 2 >nul 2>&1
goto waitForTermination

:removeCurrentInstallation
setlocal
  set installationFolder=%~1
  set filesToRemove=("app" "runtime" "Flow.exe" "Flow.ico")
  for %%f in %filesToRemove% do (
    if exist "%installationFolder%\%%f" (
      if exist "%installationFolder%\%%f\*" (
        rmdir /S /Q "%installationFolder%\%%f"
      ) else (
        del /F /Q "%installationFolder%\%%f"
      )
    )
  )
endlocal
goto :eof

:copyUpdateToInstallationFolder
setlocal
  set updateFile=%1
  set installationFolder=%2
  xcopy /E /I /Y %updateFile% %installationFolder%
endlocal
goto :eof

:removeUpdateFolder
setlocal
  set updateFolder=%1
  rmdir /S /Q "%updateFolder%"
endlocal
goto :eof

:executeProgram
setlocal
  set installationFolder=%~1
  start "" "%installationFolder%\%APP_NAME%.exe"
endlocal
goto :eof

:main
setlocal
  set updateFile=%1
  set installationFolder=%2
  call :stopApp
  call :removeCurrentInstallation %installationFolder%
  call :copyUpdateToInstallationFolder %updateFile% %installationFolder%
  call :removeUpdateFolder %updateFile%
  call :executeProgram %installationFolder%
endlocal
goto :eof
