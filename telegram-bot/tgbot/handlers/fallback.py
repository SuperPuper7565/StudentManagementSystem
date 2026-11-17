"""Обработчики для устаревших кнопок и неизвестных сообщений."""

from __future__ import annotations

import html
import logging

from aiogram import Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.types import CallbackQuery, Message

from ..config import Settings
from ..keyboards import main_reply_menu
from ..ui import send_screen

logger = logging.getLogger(__name__)

router = Router(name="fallback")


@router.callback_query()
async def unknown_callback(callback: CallbackQuery) -> None:
    if callback.message is not None:
        try:
            await callback.bot.edit_message_reply_markup(
                chat_id=callback.message.chat.id,
                message_id=callback.message.message_id,
                reply_markup=None,
            )
        except TelegramBadRequest:
            pass
        except Exception:  # noqa: BLE001 - очистка не критична
            logger.debug("Не удалось снять клавиатуру с устаревшей кнопки", exc_info=True)

    await callback.answer(
        "Кнопка устарела или недоступна. Откройте меню командой /start.",
        show_alert=True,
    )


@router.message()
async def unknown_message(message: Message, settings: Settings) -> None:
    is_admin = settings.is_admin(message.from_user.id)
    name = html.escape(message.from_user.first_name or "друг")

    await send_screen(
        message,
        f"🤔 {name}, не понял сообщение.\n\n"
        "Используйте кнопки внизу или команду /help.",
        reply_keyboard=main_reply_menu(is_admin),
    )
