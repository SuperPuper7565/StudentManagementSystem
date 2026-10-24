"""Настройки бота, читаемые из переменных окружения / .env."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


class ConfigError(RuntimeError):
    """Некорректная конфигурация приложения."""


def _parse_admin_ids(raw: str) -> frozenset[int]:
    ids: set[int] = set()
    for chunk in raw.replace(";", ",").split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        try:
            ids.add(int(chunk))
        except ValueError as exc:
            raise ConfigError(
                f"ADMIN_IDS должен содержать числовые Telegram ID, получено: {chunk!r}"
            ) from exc
    return frozenset(ids)


def _parse_timeout(raw: str) -> float:
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"REQUEST_TIMEOUT должен быть числом, получено: {raw!r}") from exc
    if value <= 0:
        raise ConfigError("REQUEST_TIMEOUT должен быть больше нуля")
    return value


@dataclass(frozen=True, slots=True)
class Settings:
    bot_token: str
    api_base_url: str
    admin_ids: frozenset[int]
    database_path: Path
    request_timeout: float = 10.0
    log_level: str = "INFO"

    @classmethod
    def load(cls, env_file: str | Path | None = None) -> "Settings":
        load_dotenv(dotenv_path=env_file)

        token = os.getenv("BOT_TOKEN", "").strip()
        if not token:
            raise ConfigError(
                "BOT_TOKEN не задан. Скопируйте .env.example в .env и укажите токен от @BotFather."
            )
        if "REPLACE_ME" in token or token == "123456789:REPLACE_ME":
            raise ConfigError(
                "BOT_TOKEN всё ещё содержит заглушку из .env.example. "
                "Откройте telegram-bot/.env и укажите настоящий токен от @BotFather."
            )

        base_url = os.getenv("API_BASE_URL", "http://localhost:8080").strip().rstrip("/")
        if not base_url.startswith(("http://", "https://")):
            raise ConfigError("API_BASE_URL должен начинаться с http:// или https://")

        database_path = Path(os.getenv("DATABASE_PATH", "data/bot.sqlite3")).expanduser()

        return cls(
            bot_token=token,
            api_base_url=base_url,
            admin_ids=_parse_admin_ids(os.getenv("ADMIN_IDS", "")),
            database_path=database_path,
            request_timeout=_parse_timeout(os.getenv("REQUEST_TIMEOUT", "10")),
            log_level=os.getenv("LOG_LEVEL", "INFO").strip().upper() or "INFO",
        )

    def is_admin(self, telegram_id: int | None) -> bool:
        return telegram_id is not None and telegram_id in self.admin_ids
