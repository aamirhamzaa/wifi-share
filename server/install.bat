@echo off
setlocal
cd /d "%~dp0"

echo ==========================================================
echo          WiFi Share - Windows Setup and Installation
echo ==========================================================

where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python is not found in your PATH.
    echo Please install Python 3.10+ from https://www.python.org/downloads/
    echo (Make sure to check "Add Python to PATH" during installation)
    pause
    exit /b 1
)

if not exist "venv\Scripts\python.exe" (
    echo [INFO] Creating Python virtual environment...
    python -m venv venv
)

echo [INFO] Installing dependencies from requirements.txt...
call venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt

echo.
echo [OK] Setup complete!
echo To start the server, double-click start.bat or run: start.bat
echo ==========================================================
pause
