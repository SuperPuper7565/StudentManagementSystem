@echo off
cd /d "%~dp0telegram-bot"

if exist ".venv\Scripts\python.exe" (
    ".venv\Scripts\python.exe" -m tgbot
) else (
    py -m tgbot
)

echo.
echo Bot stopped. Press any key to close.
pause >nul
