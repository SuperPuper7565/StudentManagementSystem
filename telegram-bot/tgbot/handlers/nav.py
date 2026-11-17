"""Навигация по постоянной reply-клавиатуре."""

from __future__ import annotations

from aiogram import F, Router
from aiogram.fsm.context import FSMContext
from aiogram.types import Message

from ..api import ApiClient
from ..config import Settings
from ..keyboards import (
    ADMIN_REPLY_BUTTONS,
    ALL_REPLY_BUTTONS,
    REPLY_ADMIN_PANEL,
    REPLY_ATTENDANCE,
    REPLY_BOOKINGS,
    REPLY_CANCEL,
    REPLY_CREATE_SLOT,
    REPLY_HELP,
    REPLY_PROFILE,
    REPLY_SLOTS,
    REPLY_STUDENTS,
    REPLY_SUBJECTS,
)
from ..storage import Database
from . import admin, booking, common

router = Router(name="nav")


@router.message(F.text.in_(ALL_REPLY_BUTTONS))
async def navigate(
    message: Message,
    state: FSMContext,
    db: Database,
    settings: Settings,
    api: ApiClient,
) -> None:
    text = message.text or ""
    is_admin = settings.is_admin(message.from_user.id)

    if text == REPLY_CANCEL:
        await state.clear()
        await common.render_main_menu(
            message,
            db,
            settings,
            "Действие отменено. Вы снова в главном меню.",
        )
        return

    if await state.get_state() is not None:
        await message.answer(
            "Сначала завершите действие или нажмите «✖️ Отмена»."
        )
        return

    if text in ADMIN_REPLY_BUTTONS and not is_admin:
        await message.answer("⛔ Этот раздел доступен только преподавателю.")
        return

    if text == REPLY_SLOTS:
        await booking.render_slots(message, state, api, db, message.from_user.id)
    elif text == REPLY_BOOKINGS:
        await booking.render_bookings(message, state, db, message.from_user.id)
    elif text == REPLY_PROFILE:
        await common.render_profile(message, db, api)
    elif text == REPLY_HELP:
        await common.render_help(message, settings)
    elif text == REPLY_ADMIN_PANEL:
        await common.render_admin_panel(message)
    elif text == REPLY_CREATE_SLOT:
        await admin.start_slot_flow(message, state)
    elif text == REPLY_SUBJECTS:
        await admin.render_teacher_subjects(message, api)
    elif text == REPLY_STUDENTS:
        await admin.render_students(message, state, api)
    elif text == REPLY_ATTENDANCE:
        await admin.render_attendance(message, state, db)
    else:
        await common.render_main_menu(message, db, settings)
