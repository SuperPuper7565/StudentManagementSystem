"""Middleware, отсекающий нажатия кнопок со старых экранов."""

from __future__ import annotations

import logging
from typing import Any, Awaitable, Callable

from aiogram import BaseMiddleware
from aiogram.exceptions import TelegramBadRequest
from aiogram.types import CallbackQuery, TelegramObject

from .screens import ScreenRegistry, registry

logger = logging.getLogger(__name__)

# Кнопки, которые имеют смысл только на текущем экране.
IN_SCREEN_PREFIXES = (
    "book:",
    "booksubj:",
    "cancel:",
    "cancel_yes:",
    "att:",
    "att_page:",
    "tsubj:",
    "regsubj:",
    "format:",
    "slotfmt:",
    "goal:skip",
    "reg:confirm",
    "reg:cancel",
    "att_manual",
    "slots:",
)

# Навигационные действия разрешены с любого сообщения.
NAVIGATION_PREFIXES = (
    "menu",
    "help",
    "profile",
    "my_bookings",
    "admin:",
    "register",
    "link",
    "notif:",
)


def _is_in_screen(data: str | None) -> bool:
    if not data:
        return False
    return data.startswith(IN_SCREEN_PREFIXES)


def _is_navigation(data: str | None) -> bool:
    if not data:
        return False
    return data.startswith(NAVIGATION_PREFIXES)


class StaleCallbackMiddleware(BaseMiddleware):
    """Не даёт сработать внутренним кнопкам со старых сообщений."""

    def __init__(self, screens: ScreenRegistry | None = None) -> None:
        self._screens = screens or registry

    async def __call__(
        self,
        handler: Callable[[TelegramObject, dict[str, Any]], Awaitable[Any]],
        event: TelegramObject,
        data: dict[str, Any],
    ) -> Any:
        callback = event if isinstance(event, CallbackQuery) else None
        if callback is None or callback.message is None:
            return await handler(event, data)

        callback_data = callback.data
        if _is_navigation(callback_data) or not _is_in_screen(callback_data):
            return await handler(event, data)

        chat_id = callback.message.chat.id
        if self._screens.is_active(chat_id, callback.message.message_id):
            return await handler(event, data)

        logger.info(
            "Отклонена устаревшая кнопка %r в чате %s (сообщение %s)",
            callback_data,
            chat_id,
            callback.message.message_id,
        )
        try:
            await callback.answer(
                "Кнопка устарела. Откройте меню заново.",
                show_alert=True,
            )
        except Exception:  # noqa: BLE001 - ответ на callback не критичен
            logger.debug("Не удалось ответить на устаревший callback", exc_info=True)

        try:
            await callback.bot.edit_message_reply_markup(
                chat_id=chat_id,
                message_id=callback.message.message_id,
                reply_markup=None,
            )
        except TelegramBadRequest:
            pass
        except Exception:  # noqa: BLE001
            logger.debug("Не удалось снять клавиатуру с устаревшего экрана", exc_info=True)

        return None
