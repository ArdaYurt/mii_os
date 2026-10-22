@echo off
rem Mii_OS speed-up for the Xiaomi Mi Box S (2nd gen, Google TV).
rem Installs Mii_OS, turns off background apps with "pm disable-user" (nothing is deleted)
rem and applies a few speed settings. "mii-speedup.bat restore" (or mii-restore.bat) undoes all of it.
setlocal EnableExtensions EnableDelayedExpansion
title Mii_OS speed-up
cd /d "%~dp0"

call :find_adb || goto :done
call :pick_box || goto :done

if /i "%~1"=="restore" goto :restore

echo.
echo  What would you like to do?
echo.
echo    1  Install or update Mii_OS on the box
echo    2  Safe cleanup   (turns off apps you don't need)
echo    3  Full speed-up: also turn off the stock Google TV home, so Mii_OS is the only home
echo    4  Undo everything this script changed
echo    5  Quit
echo.
choice /c 12345 /n /m " Press 1, 2, 3, 4 or 5: "
if errorlevel 5 goto :done
if errorlevel 4 goto :restore
if errorlevel 3 (set "FULL=1") else set "FULL=0"
if errorlevel 2 goto :cleanup
call :install_mii
goto :done

:cleanup
echo.
echo  Keeping the screensaver on ^(without one the box goes to sleep when idle^):
call :enable_list packages-keep.txt
echo.
echo  Turning off apps you don't need:
call :disable_list packages-safe.txt

if "%FULL%"=="1" call :full

echo.
echo  Settings:
rem Android's own animations stay at normal speed. Half speed (an earlier version of this
rem script) skips frames and looks choppy, which is the opposite of what we want.
adb -s %S% shell settings put global window_animation_scale 1
adb -s %S% shell settings put global transition_animation_scale 1
adb -s %S% shell settings put global animator_duration_scale 1
echo    OK Smooth animations at normal speed
rem Apps sitting in the background are frozen instead of using the CPU. Applies after a restart.
adb -s %S% shell settings put global cached_apps_freezer enabled
echo    OK Freeze apps waiting in the background
adb -s %S% shell pm trim-caches 999G >nul 2>&1
echo    OK Cleared app caches
call :installed com.miios.launcher
if not errorlevel 1 call :compile_mii
call :ask_reboot
goto :done


:install_mii
rem Uses mii-os.apk from this folder if there is one, otherwise the latest release from GitHub.
set "APK=%~dp0mii-os.apk"
if not exist "%APK%" (
    set "APK=%TEMP%\mii-os.apk"
    echo.
    echo  Downloading the latest Mii_OS...
    curl -fsSL -o "!APK!" https://github.com/ArdaYurt/mii_os/releases/latest/download/mii-os.apk
    if errorlevel 1 (
        echo  The download didn't work. Download mii-os.apk from the Releases page, put it in
        echo  this folder and try again.
        exit /b 1
    )
)
echo  Installing Mii_OS on the box ^(about 30 seconds^)...
adb -s %S% install -r "%APK%" > "%TEMP%\mii-install.txt" 2>&1
findstr /c:"Success" "%TEMP%\mii-install.txt" >nul && goto :install_ok
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%TEMP%\mii-install.txt" >nul || goto :install_failed
echo.
echo  The Mii_OS already on the box was built with a different key, so it can't be updated.
choice /c YN /n /m " Remove the old Mii_OS and install the new one? Y or N: "
if errorlevel 2 exit /b 1
adb -s %S% uninstall com.miios.launcher >nul 2>&1
adb -s %S% install "%APK%" > "%TEMP%\mii-install.txt" 2>&1
findstr /c:"Success" "%TEMP%\mii-install.txt" >nul && goto :install_ok
rem Mii_OS is gone now; if it was the only home screen, bring the stock one back so the box isn't left without one.
adb -s %S% shell pm enable com.google.android.apps.tv.launcherx >nul 2>&1
:install_failed
echo.
echo  The install didn't work. This is what the box said:
type "%TEMP%\mii-install.txt"
exit /b 1
:install_ok
echo    OK Mii_OS installed
rem Lets Mii_OS read your own logos and sounds from /sdcard/Mii_OS.
adb -s %S% shell pm grant com.miios.launcher android.permission.READ_EXTERNAL_STORAGE >nul 2>&1
rem After a full speed-up Mii_OS is the only home; an uninstall above drops that choice, so set it again.
adb -s %S% shell pm list packages -d 2>nul | findstr /c:"launcherx" >nul && adb -s %S% shell cmd package set-home-activity com.miios.launcher/.MainActivity >nul 2>&1
call :compile_mii
adb -s %S% shell am start -n com.miios.launcher/.MainActivity >nul 2>&1
echo    OK Mii_OS is open on the TV
echo.
echo  To make the Home button open Mii_OS, use its Home setup button, or run this script
echo  again and choose 3.
exit /b 0

:compile_mii
echo    .. Pre-compiling Mii_OS so it opens and scrolls faster ^(up to a minute^)
adb -s %S% shell cmd package compile -m speed -f com.miios.launcher >nul 2>&1
echo    OK Mii_OS pre-compiled
exit /b 0


:full
call :installed com.miios.launcher
if errorlevel 1 (
    echo.
    echo  Mii_OS isn't installed on this box, so the stock home stays on. Install Mii_OS first,
    echo  then run this again and choose 3.
    exit /b
)
echo.
echo  Turning off the stock Google TV home and its helpers:
call :disable_list packages-full.txt
adb -s %S% shell cmd package set-home-activity com.miios.launcher/.MainActivity >nul 2>&1
echo    OK Mii_OS is now the home screen
call :home_service
exit /b

:home_service
rem Turn on Mii_OS's Home button service (Home opens Mii_OS and its HOME Menu), keeping any other
rem accessibility services already on. Undo leaves it on: it's part of Mii_OS's normal setup.
set "HB=com.miios.launcher/com.miios.launcher.service.HomeButtonService"
set "A11Y="
for /f "delims=" %%v in ('adb -s %S% shell settings get secure enabled_accessibility_services') do set "A11Y=%%v"
if "%A11Y%"=="null" set "A11Y="
if not defined A11Y (
    set "A11Y=%HB%"
) else (
    if "!A11Y:HomeButtonService=!"=="!A11Y!" set "A11Y=!A11Y!:%HB%"
)
adb -s %S% shell settings put secure enabled_accessibility_services "%A11Y%"
adb -s %S% shell settings put secure accessibility_enabled 1
echo    OK Home button service on
exit /b


:restore
echo.
echo  Turning everything back on:
call :enable_list packages-safe.txt
call :enable_list packages-full.txt
call :enable_list packages-keep.txt
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
rem One device already connected: use it if it's the Mi Box (it may be the emulator instead).
if "%N%"=="1" call :confirm_box && exit /b 0

echo.
echo  Type your Mi Box's IP address. You'll find it on the box under
echo  Settings ^> Network ^& Internet ^> your Wi-Fi network. For example: 192.168.1.20
set "IP="
set /p "IP= IP address: "
if not defined IP exit /b 1
if "%IP::=%"=="%IP%" set "IP=%IP%:5555"
adb connect %IP%
echo  If the TV asks "Allow USB debugging?", tick "Always allow" and press OK.
set "S=%IP%"
set "TRIES=0"
:pick_box_wait
adb -s %S% get-state 2>nul | find "device" >nul && goto :pick_box_found
set /a TRIES+=1
if %TRIES% GEQ 20 (
    echo.
    echo  Couldn't reach the box. Check that USB debugging is on in Developer options and
    echo  that the laptop and the box are on the same Wi-Fi, then try again.
    exit /b 1
)
timeout /t 2 /nobreak >nul
goto :pick_box_wait
:pick_box_found
call :confirm_box
exit /b %errorlevel%

:confirm_box
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
