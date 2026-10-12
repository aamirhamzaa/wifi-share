@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ==========================================================
echo          WiFi Share - Windows Setup and Installation
echo ==========================================================

:: 1. Detect Python command (python or py)
set "PY_CMD="
where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    set "PY_CMD=python"
) else (
    where py >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        set "PY_CMD=py"
    )
)

if "%PY_CMD%"=="" (
    echo.
    echo [ERROR] Python was not found on your system!
    echo.
    echo How to fix this:
    echo 1. Download Python from: https://www.python.org/downloads/
    echo 2. During installation, CHECK the box: "Add Python to PATH"
    echo 3. Re-run this install.bat after installing Python.
    echo.
    pause
    exit /b 1
)

echo [INFO] Detected Python command: %PY_CMD%

:: 2. Create virtual environment
if not exist "venv\Scripts\python.exe" (
    echo [INFO] Creating Python virtual environment in .\venv...
    %PY_CMD% -m venv venv
    if %ERRORLEVEL% neq 0 (
        echo.
        echo [ERROR] Failed to create virtual environment.
        echo Please ensure Python is properly installed.
        pause
        exit /b 1
    )
)

:: 3. Install dependencies
echo [INFO] Installing required libraries from requirements.txt...
call venv\Scripts\activate.bat
python -m pip install --upgrade pip --quiet
pip install -r requirements.txt
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] An error occurred while installing dependencies.
    pause
    exit /b 1
)

echo.
echo ==========================================================
echo [OK] Setup completed successfully!
echo ==========================================================
echo.
echo Press any key to start WiFi Share right now...
pause >nul

call start.bat
