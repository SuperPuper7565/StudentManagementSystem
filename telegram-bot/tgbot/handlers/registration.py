"""Пошаговая регистрация ученика."""

from __future__ import annotations

import html

from aiogram import F, Router
from aiogram.filters import Command, StateFilter
from aiogram.fsm.context import FSMContext
from aiogram.types import CallbackQuery, Message

from ..api import ApiClient, ApiError, ApiUnavailableError
from ..config import Settings
from ..filters import PLAIN_TEXT
from ..formatters import (
    clean_text,
    format_goal,
    format_lesson_format,
    format_price,
    parse_grade,
)
from ..keyboards import (
    flow_reply_menu,
    formats_menu,
    goal_skip_menu,
    main_reply_menu,
    registration_confirm,
    registration_subjects_menu,
)
from ..states import Registration
from ..storage import Database
from ..ui import edit_prompt, render_screen, send_screen

router = Router(name="registration")

_VALID_FORMATS = {"ONLINE", "OFFLINE"}
_SKIP_GOAL_VALUES = {"-", "пропустить", "skip"}


def _step(number: int, text: str) -> str:
    return f"📝 <b>Регистрация</b> · шаг {number} из 6\n\n{text}"


def _subjects_prompt() -> str:
    return _step(
        3,
        "Выберите один или несколько предметов. Нажмите на предмет, чтобы отметить "
        "его, затем нажмите «Готово».",
    )


def _format_prompt() -> str:
    return _step(6, "Выберите формат занятий.")


def _summary(data: dict) -> str:
    subject_names = data.get("subject_names") or []
    subjects_text = ", ".join(str(name) for name in subject_names) or "—"
    return (
        "📋 <b>Проверьте данные профиля</b>\n\n"
        f"Имя: <b>{html.escape(str(data.get('first_name') or '—'))}</b>\n"
        f"Фамилия: <b>{html.escape(str(data.get('last_name') or '—'))}</b>\n"
        f"Предметы: <b>{html.escape(subjects_text)}</b>\n"
        f"Класс: <b>{html.escape(str(data.get('grade') or '—'))}</b>\n"
        f"Цель: <b>{html.escape(format_goal(data.get('goal')))}</b>\n"
        f"Формат: <b>{html.escape(format_lesson_format(data.get('lesson_format')))}</b>\n\n"
        "Стоимость занятия рассчитает сервис после создания профиля."
    )


async def _start_registration(message: Message, state: FSMContext) -> None:
    await state.clear()
    await state.set_state(Registration.first_name)
    await send_screen(
        message,
        _step(1, "Как вас зовут? Введите имя."),
        reply_keyboard=flow_reply_menu(),
    )


@router.message(Command("register"))
async def cmd_register(message: Message, state: FSMContext) -> None:
    await _start_registration(message, state)


@router.callback_query(F.data == "register")
async def cb_register(callback: CallbackQuery, state: FSMContext) -> None:
    await state.clear()
    await state.set_state(Registration.first_name)
    await send_screen(
        callback.message,
        _step(1, "Как вас зовут? Введите имя."),
        reply_keyboard=flow_reply_menu(),
    )
    await callback.answer()


@router.message(
    StateFilter(
        Registration.first_name,
        Registration.last_name,
        Registration.grade,
        Registration.goal,
        Registration.lesson_format,
        Registration.confirm,
    ),
    ~F.text,
)
async def registration_non_text(message: Message) -> None:
    await message.answer("Пожалуйста, отправьте ответ текстовым сообщением.")


@router.message(Registration.subjects, ~F.text)
async def registration_subjects_non_text(message: Message) -> None:
    await message.answer("Выберите предметы кнопками под сообщением, затем нажмите «Готово».")


# ---------------------------------------------------------------------- #
# Шаги
# ---------------------------------------------------------------------- #
@router.message(Registration.first_name, PLAIN_TEXT)
async def reg_first_name(message: Message, state: FSMContext) -> None:
    prompt = _step(1, "Как вас зовут? Введите имя.")
    try:
        value = clean_text(message.text or "", "Имя", max_length=60)
    except ValueError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(str(exc))}\n\n{prompt}")
        return

    await state.update_data(first_name=value)
    await state.set_state(Registration.last_name)
    await send_screen(
        message,
        _step(2, "Введите фамилию."),
        reply_keyboard=flow_reply_menu(),
    )


@router.message(Registration.last_name, PLAIN_TEXT)
async def reg_last_name(
    message: Message,
    state: FSMContext,
    api: ApiClient,
) -> None:
    prompt = _step(2, "Введите фамилию.")
    try:
        value = clean_text(message.text or "", "Фамилия", max_length=60)
    except ValueError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(str(exc))}\n\n{prompt}")
        return

    try:
        options = await api.get_teacher_subjects()
        if not options:
            options = await api.get_subjects()
    except ApiUnavailableError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(str(exc))}\n\n{prompt}")
        return
    except ApiError as exc:
        await edit_prompt(
            message,
            f"⚠️ Не удалось загрузить предметы: {html.escape(exc.message)}\n\n{prompt}",
        )
        return

    if not options:
        await edit_prompt(
            message,
            "⚠️ Список предметов пуст. Обратитесь к преподавателю.\n\n" + prompt,
        )
        return

    await state.update_data(
        last_name=value,
        subject_options=options,
        selected_subjects=[],
    )
    await state.set_state(Registration.subjects)
    await send_screen(
        message,
        _subjects_prompt(),
        inline_keyboard=registration_subjects_menu(options, set()),
    )


@router.message(Registration.subjects, PLAIN_TEXT)
async def reg_subjects_text(message: Message) -> None:
    await message.answer("Выберите предметы кнопками под сообщением, затем нажмите «Готово».")


@router.callback_query(Registration.subjects, F.data.startswith("regsubj:"))
async def cb_reg_subject(callback: CallbackQuery, state: FSMContext) -> None:
    action = (callback.data or "").split(":", 1)[1]
    data = await state.get_data()
    options = data.get("subject_options") or []
    selected = set(data.get("selected_subjects") or [])

    if action == "done":
        if not selected:
            await callback.answer("Выберите хотя бы один предмет.", show_alert=True)
            return

        names = [
            str(option.get("name") or option.get("code"))
            for option in options
            if str(option.get("code") or "") in selected
        ]
        await state.update_data(subjects=sorted(selected), subject_names=names)
        await state.set_state(Registration.grade)
        await send_screen(
            callback.message,
            _step(4, "Введите класс (число от 1 до 11)."),
            reply_keyboard=flow_reply_menu(),
        )
        await callback.answer()
        return

    known_codes = {str(option.get("code") or "") for option in options}
    if action not in known_codes:
        await callback.answer("Неизвестный предмет.", show_alert=True)
        return

    if action in selected:
        selected.remove(action)
    else:
        selected.add(action)

    await state.update_data(selected_subjects=sorted(selected))
    await render_screen(
        callback,
        _subjects_prompt(),
        inline_keyboard=registration_subjects_menu(options, selected),
    )
    await callback.answer()


@router.message(Registration.grade, PLAIN_TEXT)
async def reg_grade(message: Message, state: FSMContext) -> None:
    prompt = _step(4, "Введите класс (число от 1 до 11).")
    try:
        grade = parse_grade(message.text or "")
    except ValueError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(str(exc))}\n\n{prompt}")
        return

    await state.update_data(grade=grade)
    await state.set_state(Registration.goal)
    await send_screen(
        message,
        _step(
            5,
            "Цель обучения (необязательно).\n\n"
            "Напишите её текстом или нажмите «Пропустить».",
        ),
        inline_keyboard=goal_skip_menu(),
    )


@router.message(Registration.goal, PLAIN_TEXT)
async def reg_goal_text(message: Message, state: FSMContext) -> None:
    prompt = _step(
        5,
        "Цель обучения (необязательно).\n\n"
        "Напишите её текстом или нажмите «Пропустить».",
    )
    raw = (message.text or "").strip()
    goal: str | None = None

    if raw and raw.lower() not in _SKIP_GOAL_VALUES:
        try:
            goal = clean_text(raw, "Цель обучения", max_length=200)
        except ValueError as exc:
            await edit_prompt(
                message,
                f"⚠️ {html.escape(str(exc))}\n\n{prompt}",
                inline_keyboard=goal_skip_menu(),
            )
            return

    await state.update_data(goal=goal)
    await state.set_state(Registration.lesson_format)
    await send_screen(message, _format_prompt(), inline_keyboard=formats_menu())


@router.callback_query(Registration.goal, F.data == "goal:skip")
async def cb_goal_skip(callback: CallbackQuery, state: FSMContext) -> None:
    await state.update_data(goal=None)
    await state.set_state(Registration.lesson_format)
    await render_screen(callback, _format_prompt(), inline_keyboard=formats_menu())
    await callback.answer()


@router.callback_query(Registration.lesson_format, F.data.startswith("format:"))
async def reg_format(callback: CallbackQuery, state: FSMContext) -> None:
    lesson_format = (callback.data or "").split(":", 1)[1]
    if lesson_format not in _VALID_FORMATS:
        await callback.answer("Неизвестный формат занятий.", show_alert=True)
        return

    await state.update_data(lesson_format=lesson_format)
    data = await state.get_data()

    await state.set_state(Registration.confirm)
    await render_screen(callback, _summary(data), inline_keyboard=registration_confirm())
    await callback.answer()


@router.callback_query(Registration.confirm, F.data == "reg:cancel")
async def reg_cancel(
    callback: CallbackQuery,
    state: FSMContext,
    db: Database,
    settings: Settings,
) -> None:
    await state.clear()
    from .common import render_main_menu

    await render_main_menu(callback.message, db, settings, "Регистрация отменена.")
    await callback.answer()


@router.callback_query(Registration.confirm, F.data == "reg:confirm")
async def reg_confirm(
    callback: CallbackQuery,
    state: FSMContext,
    api: ApiClient,
    db: Database,
    settings: Settings,
) -> None:
    data = await state.get_data()
    required = ("first_name", "last_name", "subjects", "grade", "lesson_format")
    if any(key not in data for key in required):
        await state.clear()
        await callback.answer("Данные регистрации потеряны. Начните заново.", show_alert=True)
        return

    await callback.answer("Создаю профиль…")
    payload = {
        "firstName": data["first_name"],
        "lastName": data["last_name"],
        "subjects": data["subjects"],
        "grade": int(data["grade"]),
        "goal": data.get("goal") or None,
        "lessonFormat": data["lesson_format"],
    }

    try:
        student = await api.register_student(payload)
    except ApiUnavailableError as exc:
        await render_screen(
            callback,
            f"⚠️ {html.escape(str(exc))}\n\n{_summary(data)}",
            inline_keyboard=registration_confirm(),
        )
        return
    except ApiError as exc:
        await render_screen(
            callback,
            f"⚠️ Не удалось создать профиль: {html.escape(exc.message)}\n"
            f"Проверьте данные и попробуйте снова.\n\n{_summary(data)}",
            inline_keyboard=registration_confirm(),
        )
        return

    student_id = student.get("id")
    if student_id is None or int(student_id) <= 0:
        await render_screen(
            callback,
            "⚠️ Сервис создал профиль, но не вернул корректный ID. Обратитесь к преподавателю.",
            inline_keyboard=registration_confirm(),
        )
        return

    await db.link_student(callback.from_user.id, int(student_id))
    await state.clear()

    price_line = ""
    try:
        quote = await api.get_student_price(int(student_id))
        price_line = (
            f"\n💰 Стоимость занятия: <b>{format_price(quote.get('finalPrice'))}</b>"
        )
        if quote.get("trial"):
            price_line += " · пробное занятие"
    except (ApiError, ApiUnavailableError):
        pass

    name = html.escape(
        " ".join(
            part
            for part in (str(student.get("name") or ""), str(student.get("surname") or ""))
            if part
        )
        or data["first_name"]
    )
    await send_screen(
        callback.message,
        f"✅ Профиль создан!\n\n"
        f"👤 <b>{name}</b>\n"
        f"🆔 ID ученика: <b>{student_id}</b>\n"
        f"{price_line}\n\n"
        f"Теперь можно выбирать свободные слоты и записываться на занятия.",
        reply_keyboard=main_reply_menu(settings.is_admin(callback.from_user.id)),
    )
