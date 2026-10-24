# Запуск Telegram-бота из корня проекта.
# Использование:  .\run-bot.ps1
$ErrorActionPreference = "Stop"

$botDir = Join-Path $PSScriptRoot "telegram-bot"
$python = Join-Path $botDir ".venv\Scripts\python.exe"

if (-not (Test-Path $python)) {
    Write-Error "Не найдено виртуальное окружение: $python. Выполните: py -m venv telegram-bot\.venv"
    exit 1
}

Push-Location $botDir
try {
    & $python -m tgbot
}
finally {
    Pop-Location
}
