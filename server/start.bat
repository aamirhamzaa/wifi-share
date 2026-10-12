@echo off
setlocal
cd /d "%~dp0"

echo ==========================================================
echo               Starting WiFi Share Server
echo ==========================================================

if exist "venv\Scripts\python.exe" (
    echo [INFO] Running with virtual environment...
    "venv\Scripts\python.exe" main.py %*
) else (
    echo [INFO] Checking system Python...
    where python >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        python main.py %*
    ) else (
        where py >nul 2>nul
        if %ERRORLEVEL% equ 0 (
            py main.py %*
        ) else (
            echo.
            echo [ERROR] Python not found. Please run install.bat first!
            echo.
            pause
            exit /b 1
        )
    )
)

echo.
echo [INFO] Server stopped.
pause
