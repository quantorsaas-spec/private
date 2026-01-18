@echo off
setlocal
REM Quick verify: checks that quantor-domain has Java sources
if not exist "quantor-domain\src\main\java\com\quantor\domain" (
  echo FAIL: quantor-domain sources not found.
  echo Tip: Extract the zip with 7-Zip into a non-OneDrive folder like C:\Quantor\
  exit /b 1
)
echo OK: quantor-domain sources exist.
echo For full checksum verification run PowerShell:
echo   powershell -ExecutionPolicy Bypass -File verify.ps1 -Root .
exit /b 0
