"""Панель преподавателя: слоты, ученики, посещаемость."""

from __future__ import annotations

import html
import logging
import math
from datetime import datetime

from aiogram import F, Router
from aiogram.filters import Command, StateFilter
from aiogram.fsm.context import FSMContext
from aiogram.types import CallbackQuery, InlineKeyboardMarkup, Message

from ..api import ApiClient, ApiError, ApiUnavailableError
from ..config import Settings
from ..filters import IsAdmin, PLAIN_TEXT
from ..formatters import (
    format_datetime,
    format_lesson_format,
    format_slot_conflict,
    format_student_card,
    format_time_range,
    parse_slot_datetime,
)
from ..keyboards import (
    admin_panel,
    attendance_confirm,
    attendance_menu,
    back_menu,
    cancel_flow,
    flow_reply_menu,
    main_reply_menu,
    slot_formats_menu,
    teacher_subjects_menu,
)
from ..states import AdminFlow
from ..storage import Database
from ..ui import edit_prompt, render_screen, send_screen

logger = logging.getLogger(__name__)

ATTENDANCE_PAGE_SIZE = 5

SLOT_TIME_PROMPT = (
    "➕ <b>Новый слот</b>\n\n"
    "Введите дату и время начала занятия. Длительность — 90 минут.\n\n"
    "Форматы: <code>25.10.2026 14:00</code> или <code>2026-10-25 14:00</code>."
)

ATTENDANCE_MANUAL_PROMPT = (
    "⌨️ Введите числовой ID бронирования, чтобы отметить посещаемость."
)


# ---------------------------------------------------------------------- #
# Общие представления (используются и callback-хендлерами, и навигацией)
# ---------------------------------------------------------------------- #
async def _students_view(api: ApiClient) -> tuple[str, InlineKeyboardMarkup]:
    students = await api.list_students()
    if not students:
        return "👥 <b>Ученики</b>\n\nСписок пуст.", admin_panel()

    students.sort(key=lambda student: int(student.get("id") or 0))
    cards = [format_student_card(student) for student in students]
    text = "👥 <b>Ученики</b>\n\n" + "\n\n".join(cards)
    return text, admin_panel()


async def _teacher_subjects_view(
    api: ApiClient,
) -> tuple[str, InlineKeyboardMarkup]:
    options = await api.get_subjects()
    selected = {
        str(item.get("code") or "")
        for item in await api.get_teacher_subjects()
        if str(item.get("code") or "").strip()
    }
    names = [
        str(option.get("name") or option.get("code"))
        for option in options
        if str(option.get("code") or "") in selected
    ]
    selected_text = ", ".join(names) if names else "—"
    text = (
        "📚 <b>Мои предметы</b>\n\n"
        "Нажмите на предмет, чтобы включить или выключить его. "
        "Изменения сохраняются сразу.\n\n"
        f"Сейчас выбрано: <b>{html.escape(selected_text)}</b>"
    )
    return text, teacher_subjects_menu(options, selected)


def _attendance_view(
    rows: list[dict],
    page: int,
) -> tuple[str, InlineKeyboardMarkup]:
    total_pages = max(1, math.ceil(len(rows) / ATTENDANCE_PAGE_SIZE))
    page = max(0, min(page, total_pages - 1))

    if not rows:
        text = (
            "✅ <b>Посещаемость</b>\n\n"
            "Записей, созданных через бота, пока нет.\n"
            "Можно ввести ID бронирования вручную."
        )
    else:
        lines = [
            "✅ <b>Посещаемость</b>",
            "",
            f"Страница <b>{page + 1}</b> из <b>{total_pages}</b>",
            "",
        ]
        start = page * ATTENDANCE_PAGE_SIZE
        for row in rows[start : start + ATTENDANCE_PAGE_SIZE]:
            if row.get("attended") == 1:
                mark = "✅ посещён"
            elif row.get("attended") == 0:
                mark = "❌ не пришёл"
            else:
                mark = "— не отмечен"
            lines.append(
                f"#{row.get('booking_id')} · {format_datetime(row.get('start_time'))} · "
                f"ученик {row.get('student_id')} · {mark}"
            )
        lines.extend(
            [
                "",
                "Нажмите ✅ или ❌ рядом с записью. Кнопки внизу позволяют обновить "
                "список или отметить запись по ID.",
            ]
        )
        text = "\n".join(lines)

    keyboard = attendance_menu(
        rows,
        page,
        ATTENDANCE_PAGE_SIZE,
        has_prev=page > 0,
        has_next=page + 1 < total_pages,
    )
    return text, keyboard


async def _attendance_payload(
    db: Database,
    page: int,
) -> tuple[str, InlineKeyboardMarkup]:
    rows = await db.list_bookings(include_cancelled=False)
    return _attendance_view(rows, page)


# ---------------------------------------------------------------------- #
# Хелперы для навигации по reply-клавиатуре
# ---------------------------------------------------------------------- #
async def render_students(message: Message, state: FSMContext, api: ApiClient) -> None:
    await state.clear()
    try:
        text, keyboard = await _students_view(api)
    except (ApiError, ApiUnavailableError) as exc:
        await send_screen(
            message,
            f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
            inline_keyboard=admin_panel(),
        )
        return
    await send_screen(message, text, inline_keyboard=keyboard)


async def render_teacher_subjects(message: Message, api: ApiClient) -> None:
    try:
        text, keyboard = await _teacher_subjects_view(api)
    except (ApiError, ApiUnavailableError) as exc:
        await send_screen(
            message,
            f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
            inline_keyboard=admin_panel(),
        )
        return
    await send_screen(message, text, inline_keyboard=keyboard)


async def render_attendance(
    message: Message,
    state: FSMContext,
    db: Database,
    page: int = 0,
) -> None:
    await state.clear()
    text, keyboard = await _attendance_payload(db, page)
    await send_screen(message, text, inline_keyboard=keyboard)


async def start_slot_flow(message: Message, state: FSMContext) -> None:
    await state.set_state(AdminFlow.slot_time)
    await send_screen(
        message,
        SLOT_TIME_PROMPT,
        reply_keyboard=flow_reply_menu(),
    )


async def start_attendance_manual(message: Message, state: FSMContext) -> None:
    await state.set_state(AdminFlow.attendance_booking_id)
    await send_screen(
        message,
        ATTENDANCE_MANUAL_PROMPT,
        inline_keyboard=cancel_flow(),
    )


def build_router(settings: Settings) -> Router:
    router = Router(name="admin")
    router.message.filter(IsAdmin(settings.admin_ids))
    router.callback_query.filter(IsAdmin(settings.admin_ids))

    async def _refuse_if_in_flow(callback: CallbackQuery, state: FSMContext) -> bool:
        if await state.get_state() is not None:
            await callback.answer(
                "Сначала завершите действие или нажмите «✖️ Отмена».",
                show_alert=True,
            )
            return True
        return False

    # ------------------------------------------------------------------ #
    # Создание слота
    # ------------------------------------------------------------------ #
    @router.callback_query(F.data == "admin:slot")
    async def cb_create_slot(callback: CallbackQuery, state: FSMContext) -> None:
        await start_slot_flow(callback.message, state)
        await callback.answer()

    @router.message(AdminFlow.slot_time, PLAIN_TEXT)
    async def process_slot_time(message: Message, state: FSMContext, api: ApiClient) -> None:
        try:
            start_time = parse_slot_datetime(message.text or "")
        except ValueError as exc:
            await edit_prompt(
                message,
                f"⚠️ {html.escape(str(exc))}\n\n{SLOT_TIME_PROMPT}",
            )
            return

        if start_time <= datetime.now():
            await edit_prompt(
                message,
                "⚠️ Время начала должно быть в будущем.\n\n" + SLOT_TIME_PROMPT,
            )
            return

        try:
            overlap = await api.check_slot_overlap(start_time)
        except ApiError as exc:
            await edit_prompt(
                message,
                f"⚠️ Не удалось проверить время: {html.escape(exc.message)}\n\n"
                f"{SLOT_TIME_PROMPT}",
            )
            return
        except ApiUnavailableError as exc:
            await edit_prompt(
                message,
                f"⚠️ {html.escape(str(exc))}\n\n{SLOT_TIME_PROMPT}",
            )
            return

        if overlap.get("overlap"):
            await edit_prompt(
                message,
                f"{format_slot_conflict(overlap)}\n\n{SLOT_TIME_PROMPT}",
            )
            return

        await state.update_data(slot_start_time=start_time.isoformat(timespec="seconds"))
        await state.set_state(AdminFlow.slot_format)
        await send_screen(
            message,
            f"📅 Время: <b>{start_time:%d.%m.%Y %H:%M}</b>\n\n"
            "Выберите формат занятия.",
            inline_keyboard=slot_formats_menu(),
        )

    @router.message(AdminFlow.slot_format)
    async def slot_format_wait(message: Message) -> None:
        await message.answer("Выберите формат занятия кнопками под сообщением.")

    @router.callback_query(AdminFlow.slot_format, F.data.startswith("slotfmt:"))
    async def cb_slot_format(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        lesson_format = (callback.data or "").split(":", 1)[1]
        if lesson_format not in {"ONLINE", "OFFLINE"}:
            await callback.answer("Неизвестный формат занятия.", show_alert=True)
            return

        data = await state.get_data()
        raw_start = data.get("slot_start_time")
        if not raw_start:
            await state.clear()
            await callback.answer("Время слота потеряно. Начните заново.", show_alert=True)
            return

        try:
            start_time = datetime.fromisoformat(str(raw_start))
        except ValueError:
            await state.clear()
            await callback.answer("Некорректное время слота. Начните заново.", show_alert=True)
            return

        try:
            slot = await api.create_slot(start_time, lesson_format)
        except ApiError as exc:
            await callback.answer("Не удалось создать слот", show_alert=True)
            if exc.status_code == 400 and "пересек" in exc.message.lower():
                await state.set_state(AdminFlow.slot_time)
                await send_screen(
                    callback.message,
                    f"⚠️ {html.escape(exc.message)}\n\n{SLOT_TIME_PROMPT}",
                    reply_keyboard=flow_reply_menu(),
                )
                return
            await render_screen(
                callback,
                f"⚠️ Не удалось создать слот: {html.escape(exc.message)}",
                inline_keyboard=slot_formats_menu(),
            )
            return
        except ApiUnavailableError as exc:
            await callback.answer("Сервис недоступен", show_alert=True)
            await render_screen(
                callback,
                f"⚠️ {html.escape(str(exc))}",
                inline_keyboard=slot_formats_menu(),
            )
            return

        await state.clear()
        await send_screen(
            callback.message,
            f"✅ <b>Слот создан</b>\n\n"
            f"📅 <b>{format_time_range(slot.get('startTime'), slot.get('endTime'))}</b>\n"
            f"🎓 Формат: <b>{format_lesson_format(slot.get('format') or lesson_format)}</b>\n"
            f"🆔 ID слота: <b>{slot.get('id', '—')}</b>",
            reply_keyboard=main_reply_menu(True),
        )
        await callback.answer("Слот создан")

    # ------------------------------------------------------------------ #
    # Ученики
    # ------------------------------------------------------------------ #
    @router.message(Command("students"))
    async def cmd_students(message: Message, state: FSMContext, api: ApiClient) -> None:
        await render_students(message, state, api)

    @router.callback_query(F.data == "admin:students")
    async def cb_students(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        await state.clear()
        try:
            text, keyboard = await _students_view(api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer("Не удалось загрузить учеников", show_alert=True)
            await render_screen(
                callback,
                f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
                inline_keyboard=admin_panel(),
            )
            return

        await render_screen(callback, text, inline_keyboard=keyboard)
        await callback.answer()

    # ------------------------------------------------------------------ #
    # Предметы преподавателя
    # ------------------------------------------------------------------ #
    @router.callback_query(F.data == "admin:subjects")
    async def cb_teacher_subjects(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        await state.clear()
        try:
            text, keyboard = await _teacher_subjects_view(api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer("Не удалось загрузить предметы", show_alert=True)
            await render_screen(
                callback,
                f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
                inline_keyboard=admin_panel(),
            )
            return
        await render_screen(callback, text, inline_keyboard=keyboard)
        await callback.answer()

    @router.callback_query(F.data.startswith("tsubj:"))
    async def cb_toggle_teacher_subject(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        await state.clear()
        code = (callback.data or "").split(":", 1)[1]

        try:
            options = await api.get_subjects()
            selected = {
                str(item.get("code") or "")
                for item in await api.get_teacher_subjects()
                if str(item.get("code") or "").strip()
            }
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer(
                f"Не удалось загрузить предметы: {str(getattr(exc, 'message', exc))}"[:200],
                show_alert=True,
            )
            return

        known_codes = {str(option.get("code") or "") for option in options}
        if code not in known_codes:
            await callback.answer("Неизвестный предмет.", show_alert=True)
            return

        if code in selected:
            if len(selected) <= 1:
                await callback.answer(
                    "Должен остаться хотя бы один предмет.",
                    show_alert=True,
                )
                return
            selected.remove(code)
        else:
            selected.add(code)

        try:
            await api.set_teacher_subjects(sorted(selected))
        except ApiError as exc:
            await callback.answer(
                f"Не удалось сохранить: {exc.message}"[:200],
                show_alert=True,
            )
            return
        except ApiUnavailableError:
            await callback.answer("Сервис недоступен", show_alert=True)
            return

        try:
            text, keyboard = await _teacher_subjects_view(api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer(
                f"Сохранено, но не удалось обновить список: "
                f"{str(getattr(exc, 'message', exc))}"[:200],
                show_alert=True,
            )
            return
        await render_screen(callback, text, inline_keyboard=keyboard)
        await callback.answer("Сохранено")

    # ------------------------------------------------------------------ #
    # Посещаемость
    # ------------------------------------------------------------------ #
    @router.callback_query(F.data == "admin:attendance")
    async def cb_attendance(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        await state.clear()
        text, keyboard = await _attendance_payload(db, 0)
        await render_screen(callback, text, inline_keyboard=keyboard)
        await callback.answer()

    @router.callback_query(F.data.startswith("att_page:"))
    async def cb_attendance_page(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        await state.clear()
        try:
            page = int((callback.data or "att_page:0").split(":", 1)[1])
        except (ValueError, IndexError):
            page = 0
        text, keyboard = await _attendance_payload(db, page)
        await render_screen(callback, text, inline_keyboard=keyboard)
        await callback.answer()

    @router.callback_query(F.data == "att_manual")
    async def cb_attendance_manual(callback: CallbackQuery, state: FSMContext) -> None:
        await start_attendance_manual(callback.message, state)
        await callback.answer()

    @router.message(AdminFlow.attendance_booking_id, PLAIN_TEXT)
    async def process_manual_booking_id(
        message: Message,
        state: FSMContext,
        db: Database,
    ) -> None:
        raw = (message.text or "").strip()
        if not raw.isdigit():
            await edit_prompt(
                message,
                "ID должен состоять только из цифр.\n\n" + ATTENDANCE_MANUAL_PROMPT,
                inline_keyboard=cancel_flow(),
            )
            return

        booking_id = int(raw)
        row = await db.get_booking(booking_id)
        await state.clear()

        if row is not None:
            text = (
                f"Запись <b>#{booking_id}</b>\n"
                f"📅 {format_time_range(row.get('start_time'), row.get('end_time'))}\n"
                f"👤 Ученик ID: {row.get('student_id')}\n\n"
                "Отметьте результат занятия."
            )
        else:
            text = (
                f"Запись <b>#{booking_id}</b> не найдена в локальной базе бота.\n"
                "Отметка будет отправлена в сервис напрямую."
            )

        await send_screen(message, text, inline_keyboard=attendance_confirm(booking_id))

    @router.callback_query(F.data.startswith("att:"))
    async def cb_mark_attendance(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
        db: Database,
    ) -> None:
        await state.clear()
        parts = (callback.data or "").split(":")
        try:
            booking_id = int(parts[1])
            attended = parts[2] == "1"
        except (ValueError, IndexError):
            await callback.answer("Некорректные данные.", show_alert=True)
            return

        try:
            await api.mark_attendance(booking_id, attended)
        except ApiError as exc:
            await callback.answer(
                f"Не удалось отметить: {exc.message}"[:200], show_alert=True
            )
            return
        except ApiUnavailableError as exc:
            await callback.answer("Сервис недоступен", show_alert=True)
            await render_screen(
                callback,
                f"⚠️ {html.escape(str(exc))}",
                inline_keyboard=back_menu(),
            )
            return

        await db.set_attendance(booking_id, attended)
        result = "✅ Посетил" if attended else "❌ Не пришёл"
        await render_screen(
            callback,
            f"<b>Запись #{booking_id}</b>\n\n"
            f"Отмечено: <b>{result}</b>.\n"
            "Можно изменить отметку или вернуться к списку.",
            inline_keyboard=attendance_confirm(booking_id),
        )
        await callback.answer("Сохранено")

    # ------------------------------------------------------------------ #
    # Кнопки в уведомлениях
    # ------------------------------------------------------------------ #
    @router.callback_query(F.data.startswith("notif:booking:"))
    async def cb_notif_booking(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        if await _refuse_if_in_flow(callback, state):
            return

        raw_id = (callback.data or "").rsplit(":", 1)[-1]
        try:
            booking_id = int(raw_id)
        except ValueError:
            await callback.answer("Некорректная запись.", show_alert=True)
            return

        row = await db.get_booking(booking_id)
        if row is None:
            await send_screen(
                callback.message,
                f"Запись <b>#{booking_id}</b> не найдена в локальной базе бота.",
                inline_keyboard=back_menu(),
            )
            await callback.answer()
            return

        text = (
            f"🎟 <b>Запись #{booking_id}</b>\n"
            f"📅 {format_time_range(row.get('start_time'), row.get('end_time'))}\n"
            f"👤 Ученик ID: {row.get('student_id')}\n"
        )
        subject = str(row.get("subject") or "").strip()
        if subject:
            text += f"📚 {html.escape(subject)}\n"
        lesson_format = format_lesson_format(row.get("format"))
        if lesson_format != "—":
            text += f"🎓 {html.escape(lesson_format)}\n"
        text += "\nОтметьте результат занятия."

        await send_screen(
            callback.message,
            text,
            inline_keyboard=attendance_confirm(booking_id),
        )
        await callback.answer()

    @router.callback_query(F.data == "notif:attendance")
    async def cb_notif_attendance(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        if await _refuse_if_in_flow(callback, state):
            return
        text, keyboard = await _attendance_payload(db, 0)
        await send_screen(callback.message, text, inline_keyboard=keyboard)
        await callback.answer()

    @router.callback_query(F.data == "notif:menu")
    async def cb_notif_menu(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        if await _refuse_if_in_flow(callback, state):
            return
        from .common import render_main_menu

        await render_main_menu(callback.message, db, settings)
        await callback.answer()

    # ------------------------------------------------------------------ #
    # Подсказка при неверном типе сообщения
    # ------------------------------------------------------------------ #
    @router.message(
        StateFilter(AdminFlow.slot_time, AdminFlow.attendance_booking_id),
        ~F.text,
    )
    async def admin_non_text(message: Message) -> None:
        await message.answer("Пожалуйста, отправьте ответ текстовым сообщением.")

    return router
