"""Панель преподавателя: слоты, ученики, посещаемость."""

from __future__ import annotations

import html
import logging
import math
from datetime import datetime

from aiogram import F, Router
from aiogram.filters import Command, StateFilter
from aiogram.fsm.context import FSMContext
from aiogram.types import CallbackQuery, Message

from ..api import ApiClient, ApiError, ApiUnavailableError
from ..config import Settings
from ..filters import IsAdmin, PLAIN_TEXT
from ..formatters import (
    chunk_text,
    format_datetime,
    format_student_card,
    format_time_range,
    parse_slot_datetime,
)
from ..keyboards import (
    admin_panel,
    after_slot_created,
    attendance_confirm,
    attendance_menu,
    back_menu,
    cancel_flow,
    teacher_subjects_menu,
)
from ..states import AdminFlow
from ..storage import Database
from ..ui import edit_or_send

logger = logging.getLogger(__name__)

ATTENDANCE_PAGE_SIZE = 5


def build_router(settings: Settings) -> Router:
    router = Router(name="admin")
    router.message.filter(IsAdmin(settings.admin_ids))
    router.callback_query.filter(IsAdmin(settings.admin_ids))

    # ------------------------------------------------------------------ #
    # Создание слота
    # ------------------------------------------------------------------ #
    @router.callback_query(F.data == "admin:slot")
    async def cb_create_slot(callback: CallbackQuery, state: FSMContext) -> None:
        await state.set_state(AdminFlow.slot_time)
        await edit_or_send(
            callback,
            "➕ <b>Новый слот</b>\n\n"
            "Введите дату и время начала занятия. Длительность — 90 минут.\n\n"
            "Форматы: <code>25.10.2026 14:00</code> или <code>2026-10-25 14:00</code>.",
            cancel_flow(),
        )
        await callback.answer()

    @router.message(AdminFlow.slot_time, PLAIN_TEXT)
    async def process_slot_time(message: Message, state: FSMContext, api: ApiClient) -> None:
        try:
            start_time = parse_slot_datetime(message.text or "")
        except ValueError as exc:
            await message.answer(f"⚠️ {html.escape(str(exc))}")
            return

        if start_time <= datetime.now():
            await message.answer("⚠️ Время начала должно быть в будущем. Попробуйте снова.")
            return

        try:
            slot = await api.create_slot(start_time)
        except ApiError as exc:
            await message.answer(f"⚠️ Не удалось создать слот: {html.escape(exc.message)}")
            return
        except ApiUnavailableError as exc:
            await message.answer(f"⚠️ {html.escape(str(exc))}")
            return

        await state.clear()
        await message.answer(
            f"✅ <b>Слот создан</b>\n\n"
            f"📅 <b>{format_time_range(slot.get('startTime'), slot.get('endTime'))}</b>\n"
            f"🆔 ID слота: <b>{slot.get('id', '—')}</b>",
            reply_markup=after_slot_created(),
        )

    # ------------------------------------------------------------------ #
    # Ученики
    # ------------------------------------------------------------------ #
    async def _students_text(api: ApiClient) -> str:
        students = await api.list_students()
        if not students:
            return "👥 <b>Ученики</b>\n\nСписок пуст."

        students.sort(key=lambda student: int(student.get("id") or 0))
        cards = [format_student_card(student) for student in students]
        return "👥 <b>Ученики</b>\n\n" + "\n\n".join(cards)

    @router.message(Command("students"))
    async def cmd_students(message: Message, state: FSMContext, api: ApiClient) -> None:
        await state.clear()
        try:
            text = await _students_text(api)
        except (ApiError, ApiUnavailableError) as exc:
            await message.answer(f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}")
            return

        chunks = chunk_text(text)
        for chunk in chunks[:-1]:
            await message.answer(chunk)
        await message.answer(chunks[-1], reply_markup=admin_panel())

    @router.callback_query(F.data == "admin:students")
    async def cb_students(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        await state.clear()
        try:
            text = await _students_text(api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer("Не удалось загрузить учеников", show_alert=True)
            await edit_or_send(
                callback,
                f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
                admin_panel(),
            )
            return

        chunks = chunk_text(text)
        if len(chunks) == 1:
            await edit_or_send(callback, chunks[0], admin_panel())
        else:
            await edit_or_send(callback, chunks[0])
            for chunk in chunks[1:-1]:
                await callback.message.answer(chunk)
            await callback.message.answer(chunks[-1], reply_markup=admin_panel())
        await callback.answer()

    # ------------------------------------------------------------------ #
    # Предметы преподавателя
    # ------------------------------------------------------------------ #
    async def _teacher_subjects_payload(
        api: ApiClient,
    ) -> tuple[list[dict], set[str]]:
        options = await api.get_subjects()
        selected = {
            str(item.get("code") or "")
            for item in await api.get_teacher_subjects()
            if str(item.get("code") or "").strip()
        }
        return options, selected

    def _teacher_subjects_text(options: list[dict], selected: set[str]) -> str:
        names = [
            str(option.get("name") or option.get("code"))
            for option in options
            if str(option.get("code") or "") in selected
        ]
        selected_text = ", ".join(names) if names else "—"
        return (
            "📚 <b>Мои предметы</b>\n\n"
            "Нажмите на предмет, чтобы включить или выключить его. "
            "Изменения сохраняются сразу.\n\n"
            f"Сейчас выбрано: <b>{html.escape(selected_text)}</b>"
        )

    async def _show_teacher_subjects(callback: CallbackQuery, api: ApiClient) -> None:
        options, selected = await _teacher_subjects_payload(api)
        await edit_or_send(
            callback,
            _teacher_subjects_text(options, selected),
            teacher_subjects_menu(options, selected),
        )

    @router.callback_query(F.data == "admin:subjects")
    async def cb_teacher_subjects(
        callback: CallbackQuery,
        state: FSMContext,
        api: ApiClient,
    ) -> None:
        await state.clear()
        try:
            await _show_teacher_subjects(callback, api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer("Не удалось загрузить предметы", show_alert=True)
            await edit_or_send(
                callback,
                f"⚠️ {html.escape(str(getattr(exc, 'message', exc)))}",
                admin_panel(),
            )
            return
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
            options, selected = await _teacher_subjects_payload(api)
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
            await _show_teacher_subjects(callback, api)
        except (ApiError, ApiUnavailableError) as exc:
            await callback.answer(
                f"Сохранено, но не удалось обновить список: "
                f"{str(getattr(exc, 'message', exc))}"[:200],
                show_alert=True,
            )
            return
        await callback.answer("Сохранено")

    # ------------------------------------------------------------------ #
    # Посещаемость
    # ------------------------------------------------------------------ #
    def _attendance_text(rows: list[dict], page: int) -> str:
        total_pages = max(1, math.ceil(len(rows) / ATTENDANCE_PAGE_SIZE))
        page = max(0, min(page, total_pages - 1))

        if not rows:
            return (
                "✅ <b>Посещаемость</b>\n\n"
                "Записей, созданных через бота, пока нет.\n"
                "Можно ввести ID бронирования вручную."
            )

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
        return "\n".join(lines)

    async def _show_attendance(
        callback: CallbackQuery,
        db: Database,
        page: int,
    ) -> None:
        rows = await db.list_bookings(include_cancelled=False)
        total_pages = max(1, math.ceil(len(rows) / ATTENDANCE_PAGE_SIZE))
        page = max(0, min(page, total_pages - 1))
        await edit_or_send(
            callback,
            _attendance_text(rows, page),
            attendance_menu(
                rows,
                page,
                ATTENDANCE_PAGE_SIZE,
                has_prev=page > 0,
                has_next=page + 1 < total_pages,
            ),
        )

    @router.callback_query(F.data == "admin:attendance")
    async def cb_attendance(
        callback: CallbackQuery,
        state: FSMContext,
        db: Database,
    ) -> None:
        await state.clear()
        await _show_attendance(callback, db, 0)
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
        await _show_attendance(callback, db, page)
        await callback.answer()

    @router.callback_query(F.data == "att_manual")
    async def cb_attendance_manual(callback: CallbackQuery, state: FSMContext) -> None:
        await state.set_state(AdminFlow.attendance_booking_id)
        await edit_or_send(
            callback,
            "⌨️ Введите числовой ID бронирования, чтобы отметить посещаемость.",
            cancel_flow(),
        )
        await callback.answer()

    @router.message(AdminFlow.attendance_booking_id, PLAIN_TEXT)
    async def process_manual_booking_id(
        message: Message,
        state: FSMContext,
        db: Database,
    ) -> None:
        raw = (message.text or "").strip()
        if not raw.isdigit():
            await message.answer("ID должен состоять только из цифр. Попробуйте ещё раз.")
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

        await message.answer(text, reply_markup=attendance_confirm(booking_id))

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
            await edit_or_send(callback, f"⚠️ {html.escape(str(exc))}", back_menu())
            return

        await db.set_attendance(booking_id, attended)
        result = "✅ Посетил" if attended else "❌ Не пришёл"
        await edit_or_send(
            callback,
            f"<b>Запись #{booking_id}</b>\n\n"
            f"Отмечено: <b>{result}</b>.\n"
            "Можно изменить отметку или вернуться к списку.",
            attendance_confirm(booking_id),
        )
        await callback.answer("Сохранено")

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
