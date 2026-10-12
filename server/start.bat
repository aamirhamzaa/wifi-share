@echo off
setlocal
cd /d "%~dp0"

if exist "venv\Scripts\python.exe" (
    echo [INFO] Running with virtual environment...
    "venv\Scripts\python.exe" main.py %*
) else (
    echo [INFO] Running with system Python...
    python main.py %*
)

pause
