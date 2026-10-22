@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push-logos.ps1" -reset
pause
