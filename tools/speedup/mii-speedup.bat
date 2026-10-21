@echo off
rem Mii_OS speed-up for the Xiaomi Mi Box S (2nd gen, Google TV).
rem Turns off background apps with "pm disable-user" (nothing is deleted) and applies a few
rem speed settings. "mii-speedup.bat restore" (or mii-restore.bat) undoes all of it.
setlocal EnableExtensions EnableDelayedExpansion
title Mii_OS speed-up
cd /d "%~dp0"

call :find_adb || goto :done
call :pick_box || goto :done

if /i "%~1"=="restore" goto :restore

echo.
echo  What would you like to do?
echo.
echo    1  Safe cleanup and speed settings   (start here)
echo    2  Full speed-up: also turn off the stock Google TV home, so Mii_OS is the only home
echo    3  Undo everything this script changed
echo    4  Quit
echo.
choice /c 1234 /n /m " Press 1, 2, 3 or 4: "
if errorlevel 4 goto :done
if errorlevel 3 goto :restore
set "FULL=0"
if errorlevel 2 set "FULL=1"

echo.
echo  Turning off apps you don't need:
call :disable_list packages-safe.txt

if "%FULL%"=="1" call :full

echo.
echo  Speed settings:
rem Menus and app switches in Android open twice as fast. animator_duration_scale stays at 1:
rem it would also speed up Mii_OS's own springs, which are tuned to feel right at full length.
adb -s %S% shell settings put global window_animation_scale 0.5
adb -s %S% shell settings put global transition_animation_scale 0.5
echo    OK Faster Android menu and app-switch animations
rem Apps sitting in the background are frozen instead of using the CPU. Applies after a restart.
adb -s %S% shell settings put global cached_apps_freezer enabled
echo    OK Freeze apps waiting in the background
call :installed com.miios.launcher
if not errorlevel 1 (
    echo    .. Pre-compiling Mii_OS so it opens faster ^(takes up to a minute^)
    adb -s %S% shell cmd package compile -m speed -f com.miios.launcher >nul 2>&1
    echo    OK Mii_OS pre-compiled
)
call :ask_reboot
goto :done


:full
call :installed com.miios.launcher
if errorlevel 1 (
    echo.
    echo  Mii_OS isn't installed on this box, so the stock home stays on. Install Mii_OS first,
    echo  then run this again and choose 2.
    exit /b
)
echo.
echo  Turning off the stock Google TV home and its helpers:
call :disable_list packages-full.txt
adb -s %S% shell cmd package set-home-activity com.miios.launcher/.MainActivity >nul 2>&1
echo    OK Mii_OS is now the home screen
exit /b


:restore
echo.
echo  Turning everything back on:
call :enable_list packages-safe.txt
call :enable_list packages-full.txt
for /f "tokens=*" %%c in ('adb -s %S% shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME ^| findstr /c:"launcherx/"') do set "STOCK_HOME=%%c"
if defined STOCK_HOME (
    adb -s %S% shell cmd package set-home-activity !STOCK_HOME! >nul 2>&1
    echo    OK Google TV home is the home screen again
)
adb -s %S% shell settings put global window_animation_scale 1
adb -s %S% shell settings put global transition_animation_scale 1
adb -s %S% shell settings put global animator_duration_scale 1
adb -s %S% shell settings delete global cached_apps_freezer >nul
adb -s %S% shell cmd package compile --reset com.miios.launcher >nul 2>&1
echo    OK Animation and background settings are back to normal
call :ask_reboot
goto :done


rem ---- Helpers -------------------------------------------------------------------------------

:find_adb
rem Use adb from PATH, this folder, Android Studio's SDK or C:\platform-tools.
where adb >nul 2>nul && exit /b 0
for %%d in ("%~dp0" "%LOCALAPPDATA%\Android\Sdk\platform-tools" "C:\platform-tools" "%USERPROFILE%\Downloads\platform-tools") do (
    if exist "%%~d\adb.exe" (
        set "PATH=%%~d;!PATH!"
        exit /b 0
    )
)
echo.
echo  Couldn't find adb. Put this folder inside your platform-tools folder (next to adb.exe)
echo  and run it again.
exit /b 1

:pick_box
adb start-server >nul 2>&1
set "S="
set "N=0"
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do if "%%b"=="device" (
    set /a N+=1
    set "S=%%a"
)
if not "%N%"=="1" (
    echo.
    echo  Type your Mi Box's IP address. You'll find it on the box under
    echo  Settings ^> Network ^& Internet ^> your Wi-Fi network. For example: 192.168.1.20
    set "IP="
    set /p "IP= IP address: "
    if not defined IP exit /b 1
    if "!IP::=!"=="!IP!" set "IP=!IP!:5555"
    adb connect !IP!
    echo  If the TV asks "Allow USB debugging?", tick "Always allow" and press OK.
    set "S=!IP!"
    for /l %%i in (1,1,20) do (
        adb -s !S! get-state 2>nul | find "device" >nul && goto :pick_box_check
        timeout /t 2 /nobreak >nul
    )
    echo.
    echo  Couldn't reach the box. Check that USB debugging is on in Developer options and
    echo  that the laptop and the box are on the same Wi-Fi, then try again.
    exit /b 1
)
:pick_box_check
set "MODEL="
for /f "delims=" %%m in ('adb -s %S% shell getprop ro.product.model') do set "MODEL=%%m"
echo.
echo  Connected to: %MODEL%  (%S%)
choice /c YN /n /m " Is this your Mi Box? Y or N: "
if errorlevel 2 exit /b 1
exit /b 0

:installed
adb -s %S% shell pm path %1 2>nul | findstr /b "package:" >nul
exit /b %errorlevel%

:disable_list
for /f "usebackq eol=# tokens=1,*" %%p in ("%~1") do (
    call :label "%%p" "%%q"
    call :installed %%p
    if errorlevel 1 (
        echo    -  !NAME!  ^(not on this box^)
    ) else (
        adb -s %S% shell pm disable-user --user 0 %%p 2>&1 | findstr /c:"disabled-user" >nul
        if errorlevel 1 (echo    ?  !NAME!  ^(the box wouldn't allow it, skipped^)) else (echo    OK !NAME!)
    )
)
exit /b

:enable_list
for /f "usebackq eol=# tokens=1,*" %%p in ("%~1") do (
    call :label "%%p" "%%q"
    call :installed %%p
    if not errorlevel 1 (
        adb -s %S% shell pm enable %%p >nul 2>&1
        echo    OK !NAME!
    )
)
exit /b

:label
rem The readable name is the text after "#"; fall back to the package name.
set "NAME=%~2"
if "!NAME!"=="" (set "NAME=%~1") else if "!NAME:~0,1!"=="#" set "NAME=!NAME:~2!"
exit /b

:ask_reboot
echo.
choice /c YN /n /m " Restart the box now so every change takes effect? Y or N: "
if errorlevel 2 exit /b
adb -s %S% reboot
echo  The box is restarting.
exit /b

:done
echo.
pause
