"""Просмотр слотов, запись на урок и отмена записей."""

from __future__ import annotations

import html
import logging
import math
from datetime import datetime

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.fsm.context import FSMContext
from aiogram.types import CallbackQuery, InlineKeyboardMarkup, Message

from ..api import ApiClient, ApiError, ApiUnavailableError
from ..formatters import (
    format_booking_line,
    format_price,
    format_time_range,
    is_slot_booked,
    parse_iso_datetime,
)
from ..keyboards import (
    after_booking,
    back_menu,
    bookings_menu,
    cancel_confirm,
    onboarding_menu,
    slots_menu,
)
from ..storage import Database
from ..ui import edit_or_send

logger = logging.getLogger(__name__)

router = Router(name="booking")

PAGE_SIZE = 5
MAX_BOOKINGS_IN_MESSAGE = 15


def _total_pages(count: int) -> int:
    return max(1, math.ceil(count / PAGE_SIZE))


async def _available_slots(api: ApiClient) -> list[dict]:
    slots = [slot for slot in await api.get_available_slots() if not is_slot_booked(slot)]
    slots.sort(key=lambda slot: parse_iso_datetime(slot.get("startTime")) or datetime.max)
    return slots


async def _student_price_text(
    api: ApiClient,
    db: Database,
    telegram_id: int,
) -> str | None:
    student_id = await db.get_student_id(telegram_id)
    if student_id is None:
        return None
    try:
        quote = await api.get_student_price(student_id)
    except (ApiError, ApiUnavailableError):
        return None
    return format_price(quote.get("finalPrice"))


def _slots_view(
    slots: list[dict],
    page: int,
    price_text: str | None = None,
) -> tuple[str, InlineKeyboardMarkup]:
    total_pages = _total_pages(len(slots))
    page = max(0, min(page, total_pages - 1))

    if not slots:
        text = (
            "📅 <b>Свободные слоты</b>\n\n"
            "Пока свободных слотов нет. Загляните позже или попросите преподавателя "
            "добавить время."
        )
    else:
        text = (
            "📅 <b>Свободные слоты</b>\n\n"
            f"Страница <b>{page + 1}</b> из <b>{total_pages}</b> · всего {len(slots)}\n\n"
            "Нажмите на удобное время — запись создастся сразу."
        )

    if price_text:
        text += f"\n\n💰 Стоимость занятия: <b>{html.escape(price_text)}</b>"

    keyboard = slots_menu(
        slots=slots,
        page=page,
        page_size=PAGE_SIZE,
        has_prev=page > 0,
        has_next=page + 1 < total_pages,
    )
    return text, keyboard


# ---------------------------------------------------------------------- #
# Свободные слоты
# ---------------------------------------------------------------------- #
@router.message(Command("slots"))
async def cmd_slots(message: Message, state: FSMContext, api: ApiClient, db: Database) -> None:
    await state.clear()
    try:
        slots = await _available_slots(api)
    except (ApiError, ApiUnavailableError) as exc:
        await message.answer(f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}")
        return

    price_text = await _student_price_text(api, db, message.from_user.id)
    text, keyboard = _slots_view(slots, 0, price_text)
    await message.answer(text, reply_markup=keyboard)


@router.callback_query(F.data.startswith("slots:"))
async def cb_slots(
    callback: CallbackQuery,
    state: FSMContext,
    api: ApiClient,
    db: Database,
) -> None:
    await state.clear()
    try:
        page = int((callback.data or "slots:0").split(":", 1)[1])
    except (ValueError, IndexError):
        page = 0

    try:
        slots = await _available_slots(api)
    except (ApiError, ApiUnavailableError) as exc:
        await callback.answer("Не удалось загрузить слоты", show_alert=True)
        await edit_or_send(
            callback,
            f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
            back_menu(),
        )
        return

    price_text = await _student_price_text(api, db, callback.from_user.id)
    text, keyboard = _slots_view(slots, page, price_text)
    await edit_or_send(callback, text, keyboard)
    await callback.answer()


# ---------------------------------------------------------------------- #
# Запись на слот
# ---------------------------------------------------------------------- #
@router.callback_query(F.data.startswith("book:"))
async def cb_book(
    callback: CallbackQuery,
    api: ApiClient,
    db: Database,
) -> None:
    try:
        slot_id = int((callback.data or "").split(":", 1)[1])
    except (ValueError, IndexError):
        await callback.answer("Некорректный слот.", show_alert=True)
        return

    student_id = await db.get_student_id(callback.from_user.id)
    if student_id is None:
        await callback.answer("Сначала создайте профиль или привяжите ID.", show_alert=True)
        await edit_or_send(
            callback,
            "Чтобы записаться, создайте профиль ученика или привяжите существующий ID.",
            onboarding_menu(),
        )
        return

    try:
        slots = await _available_slots(api)
    except (ApiError, ApiUnavailableError) as exc:
        await callback.answer("Сервис недоступен", show_alert=True)
        await edit_or_send(
            callback,
            f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
            back_menu(),
        )
        return

    slot = next(
        (item for item in slots if int(item.get("id", -1)) == slot_id),
        None,
    )
    if slot is None:
        await callback.answer("Этот слот уже занят или недоступен.", show_alert=True)
        text, keyboard = _slots_view(slots, 0)
        await edit_or_send(callback, text, keyboard)
        return

    try:
        booking = await api.book_lesson(student_id, slot_id)
    except ApiError as exc:
        await callback.answer(f"Не удалось записаться: {exc.message}"[:200], show_alert=True)
        try:
            fresh_slots = await _available_slots(api)
        except (ApiError, ApiUnavailableError):
            return
        text, keyboard = _slots_view(fresh_slots, 0)
        await edit_or_send(callback, text, keyboard)
        return
    except ApiUnavailableError as exc:
        await callback.answer("Сервис недоступен", show_alert=True)
        await edit_or_send(callback, f"⚠️ {html.escape(str(exc))}", back_menu())
        return

    try:
        await db.add_booking(booking, callback.from_user.id, student_id, slot)
    except ValueError:
        logger.exception("Не удалось сохранить бронирование в локальной базе")

    booking_id = booking.get("id", "—")
    slot_data = booking.get("slot") or slot
    time_range = format_time_range(slot_data.get("startTime"), slot_data.get("endTime"))
    price = format_price(booking.get("price"))
    price_line = f"💰 Стоимость: <b>{price}</b>\n" if price != "—" else ""

    await edit_or_send(
        callback,
        f"✅ <b>Вы записаны!</b>\n\n"
        f"📅 <b>{time_range}</b>\n"
        f"🎟 Номер записи: <b>{booking_id}</b>\n"
        f"{price_line}"
        f"👤 ID ученика: {student_id}\n\n"
        f"Отменить запись можно в разделе «Мои записи».",
        after_booking(),
    )
    await callback.answer("Готово!")


# ---------------------------------------------------------------------- #
# Мои записи
# ---------------------------------------------------------------------- #
def _bookings_text(rows: list[dict]) -> str:
    if not rows:
        return (
            "🎟 <b>Мои записи</b>\n\n"
            "У вас нет активных записей. Выберите свободный слот, чтобы записаться."
        )

    shown = rows[:MAX_BOOKINGS_IN_MESSAGE]
    lines = ["🎟 <b>Мои записи</b>", ""]
    lines.extend(format_booking_line(row) for row in shown)
    if len(rows) > len(shown):
        lines.extend(["", f"…и ещё {len(rows) - len(shown)} записей."])
    lines.extend(["", "Нажмите на запись ниже, чтобы отменить её."])
    return "\n\n".join(lines)


@router.message(Command("bookings"))
async def cmd_bookings(message: Message, state: FSMContext, db: Database) -> None:
    await state.clear()
    rows = await db.list_bookings(message.from_user.id)
    await message.answer(
        _bookings_text(rows),
        reply_markup=bookings_menu(rows[:MAX_BOOKINGS_IN_MESSAGE]),
    )


@router.callback_query(F.data == "my_bookings")
async def cb_my_bookings(callback: CallbackQuery, state: FSMContext, db: Database) -> None:
    await state.clear()
    rows = await db.list_bookings(callback.from_user.id)
    await edit_or_send(
        callback,
        _bookings_text(rows),
        bookings_menu(rows[:MAX_BOOKINGS_IN_MESSAGE]),
    )
    await callback.answer()


@router.callback_query(F.data.startswith("cancel:"))
async def cb_cancel(callback: CallbackQuery, db: Database) -> None:
    try:
        booking_id = int((callback.data or "").split(":", 1)[1])
    except (ValueError, IndexError):
        await callback.answer("Некорректная запись.", show_alert=True)
        return

    row = await db.get_booking(booking_id)
    if row is None or row.get("telegram_id") != callback.from_user.id:
        await callback.answer("Запись не найдена.", show_alert=True)
        return

    await edit_or_send(
        callback,
        f"❓ Отменить запись?\n\n{format_booking_line(row)}\n\n"
        "Слот снова станет доступен другим ученикам.",
        cancel_confirm(booking_id),
    )
    await callback.answer()


@router.callback_query(F.data.startswith("cancel_yes:"))
async def cb_cancel_confirm(
    callback: CallbackQuery,
    api: ApiClient,
    db: Database,
) -> None:
    try:
        booking_id = int((callback.data or "").split(":", 1)[1])
    except (ValueError, IndexError):
        await callback.answer("Некорректная запись.", show_alert=True)
        return

    row = await db.get_booking(booking_id)
    if row is None or row.get("telegram_id") != callback.from_user.id:
        await callback.answer("Запись не найдена.", show_alert=True)
        return

    try:
        await api.cancel_booking(booking_id)
    except ApiError as exc:
        if exc.status_code == 404 or "уже отменена" in exc.message.lower():
            await db.mark_cancelled(booking_id)
            await callback.answer("Запись уже была отменена.", show_alert=True)
            await edit_or_send(
                callback,
                f"ℹ️ Запись <b>#{booking_id}</b> уже отменена.",
                after_booking(),
            )
            return
        await callback.answer(f"Не удалось отменить: {exc.message}"[:200], show_alert=True)
        return
    except ApiUnavailableError as exc:
        await callback.answer("Сервис недоступен", show_alert=True)
        await edit_or_send(callback, f"⚠️ {html.escape(str(exc))}", back_menu())
        return

    await db.mark_cancelled(booking_id)
    await edit_or_send(
        callback,
        f"✅ Запись <b>#{booking_id}</b> отменена.\n\n"
        f"{format_time_range(row.get('start_time'), row.get('end_time'))}\n"
        "Слот снова свободен.",
        after_booking(),
    )
    await callback.answer("Отменено")
