"""Обработчики для устаревших кнопок и неизвестных сообщений."""

from __future__ import annotations

import html

from aiogram import Router
from aiogram.types import CallbackQuery, Message

from ..config import Settings
from ..keyboards import main_menu
from ..storage import Database

router = Router(name="fallback")


@router.callback_query()
async def unknown_callback(callback: CallbackQuery) -> None:
    await callback.answer(
        "Кнопка устарела или недоступна. Откройте меню командой /start.",
        show_alert=True,
    )


@router.message()
async def unknown_message(message: Message, db: Database, settings: Settings) -> None:
    student_id = await db.get_student_id(message.from_user.id)
    is_admin = settings.is_admin(message.from_user.id)
    name = html.escape(message.from_user.first_name or "друг")

    await message.answer(
        f"🤔 {name}, не понял сообщение.\n\n"
        "Используйте меню ниже или команду /help.",
        reply_markup=main_menu(is_admin, student_id is not None),
    )
