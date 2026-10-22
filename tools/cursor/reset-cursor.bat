@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-cursor.ps1" -reset
pause
