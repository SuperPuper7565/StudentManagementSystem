"""Фильтры доступа."""

from __future__ import annotations

from aiogram import F
from aiogram.filters import BaseFilter
from aiogram.types import TelegramObject

# Обычный текст, но не команда: команды должны обрабатываться своими хендлерами
# даже если пользователь находится внутри FSM-сценария.
PLAIN_TEXT = F.text & ~F.text.startswith("/")


class IsAdmin(BaseFilter):
    def __init__(self, admin_ids: frozenset[int] | set[int] | list[int]) -> None:
        self.admin_ids = frozenset(admin_ids)

    async def __call__(self, event: TelegramObject) -> bool:
        user = getattr(event, "from_user", None)
        return bool(user is not None and user.id in self.admin_ids)
