@echo off
setlocal
cd /d "%~dp0"

echo ==========================================================
echo         WiFi Share - Windows Firewall Rule Config
echo ==========================================================
echo.
echo Adding inbound firewall rule for Port 8080...
echo.

netsh advfirewall firewall add rule name="WiFi Share Server (Port 8080)" dir=in action=allow protocol=TCP localport=8080

if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] Firewall rule added successfully!
    echo Mobile devices on your local Wi-Fi can now connect to port 8080.
) else (
    echo.
    echo [ERROR] Failed to add firewall rule.
    echo Please right-click allow-firewall.bat and choose "Run as administrator".
)

echo.
echo ==========================================================
pause
