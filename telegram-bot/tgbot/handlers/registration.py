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
    cancel_flow,
    formats_menu,
    goals_menu,
    main_menu,
    registration_confirm,
)
from ..states import Registration
from ..storage import Database
from ..ui import edit_or_send

router = Router(name="registration")

_VALID_GOALS = {"OGE", "EGE", "OTHER"}
_VALID_FORMATS = {"ONLINE", "OFFLINE"}


def _step(number: int, text: str) -> str:
    return f"📝 <b>Регистрация</b> · шаг {number} из 6\n\n{text}"


async def _start_registration(
    message: Message,
    state: FSMContext,
) -> None:
    await state.clear()
    await state.set_state(Registration.first_name)
    await message.answer(
        _step(1, "Как вас зовут? Введите имя."),
        reply_markup=cancel_flow(),
    )


@router.message(Command("register"))
async def cmd_register(message: Message, state: FSMContext) -> None:
    await _start_registration(message, state)


@router.callback_query(F.data == "register")
async def cb_register(callback: CallbackQuery, state: FSMContext) -> None:
    await state.clear()
    await state.set_state(Registration.first_name)
    await edit_or_send(
        callback,
        _step(1, "Как вас зовут? Введите имя."),
        cancel_flow(),
    )
    await callback.answer()


@router.message(
    StateFilter(
        Registration.first_name,
        Registration.last_name,
        Registration.subject,
        Registration.grade,
        Registration.goal,
        Registration.lesson_format,
        Registration.confirm,
    ),
    ~F.text,
)
async def registration_non_text(message: Message) -> None:
    await message.answer("Пожалуйста, отправьте ответ текстовым сообщением.")


# ---------------------------------------------------------------------- #
# Шаги
# ---------------------------------------------------------------------- #
@router.message(Registration.first_name, PLAIN_TEXT)
async def reg_first_name(message: Message, state: FSMContext) -> None:
    try:
        value = clean_text(message.text or "", "Имя", max_length=60)
    except ValueError as exc:
        await message.answer(f"⚠️ {html.escape(str(exc))}\nПопробуйте ещё раз.")
        return

    await state.update_data(first_name=value)
    await state.set_state(Registration.last_name)
    await message.answer(_step(2, "Введите фамилию."), reply_markup=cancel_flow())


@router.message(Registration.last_name, PLAIN_TEXT)
async def reg_last_name(message: Message, state: FSMContext) -> None:
    try:
        value = clean_text(message.text or "", "Фамилия", max_length=60)
    except ValueError as exc:
        await message.answer(f"⚠️ {html.escape(str(exc))}\nПопробуйте ещё раз.")
        return

    await state.update_data(last_name=value)
    await state.set_state(Registration.subject)
    await message.answer(
        _step(3, "Какой предмет изучаете? Например: «Математика»."),
        reply_markup=cancel_flow(),
    )


@router.message(Registration.subject, PLAIN_TEXT)
async def reg_subject(message: Message, state: FSMContext) -> None:
    try:
        value = clean_text(message.text or "", "Предмет", max_length=80)
    except ValueError as exc:
        await message.answer(f"⚠️ {html.escape(str(exc))}\nПопробуйте ещё раз.")
        return

    await state.update_data(subject=value)
    await state.set_state(Registration.grade)
    await message.answer(
        _step(4, "Введите класс (число от 1 до 11)."),
        reply_markup=cancel_flow(),
    )


@router.message(Registration.grade, PLAIN_TEXT)
async def reg_grade(message: Message, state: FSMContext) -> None:
    try:
        grade = parse_grade(message.text or "")
    except ValueError as exc:
        await message.answer(f"⚠️ {html.escape(str(exc))}")
        return

    await state.update_data(grade=grade)
    await state.set_state(Registration.goal)
    await message.answer(
        _step(5, "Выберите цель обучения."),
        reply_markup=goals_menu(),
    )


@router.callback_query(Registration.goal, F.data.startswith("goal:"))
async def reg_goal(callback: CallbackQuery, state: FSMContext) -> None:
    goal = (callback.data or "").split(":", 1)[1]
    if goal not in _VALID_GOALS:
        await callback.answer("Неизвестная цель обучения.", show_alert=True)
        return

    await state.update_data(goal=goal)
    await state.set_state(Registration.lesson_format)
    await callback.message.edit_text(
        _step(6, "Выберите формат занятий."),
        reply_markup=formats_menu(),
    )
    await callback.answer()


@router.callback_query(Registration.lesson_format, F.data.startswith("format:"))
async def reg_format(callback: CallbackQuery, state: FSMContext) -> None:
    lesson_format = (callback.data or "").split(":", 1)[1]
    if lesson_format not in _VALID_FORMATS:
        await callback.answer("Неизвестный формат занятий.", show_alert=True)
        return

    await state.update_data(lesson_format=lesson_format)
    data = await state.get_data()

    summary = (
        "📋 <b>Проверьте данные профиля</b>\n\n"
        f"Имя: <b>{html.escape(data['first_name'])}</b>\n"
        f"Фамилия: <b>{html.escape(data['last_name'])}</b>\n"
        f"Предмет: <b>{html.escape(data['subject'])}</b>\n"
        f"Класс: <b>{data['grade']}</b>\n"
        f"Цель: <b>{html.escape(format_goal(data['goal']))}</b>\n"
        f"Формат: <b>{html.escape(format_lesson_format(lesson_format))}</b>\n\n"
        "Стоимость занятия рассчитает сервис после создания профиля."
    )
    await state.set_state(Registration.confirm)
    await edit_or_send(callback, summary, registration_confirm())
    await callback.answer()


@router.callback_query(Registration.confirm, F.data == "reg:cancel")
async def reg_cancel(callback: CallbackQuery, state: FSMContext, db: Database, settings: Settings) -> None:
    await state.clear()
    student_id = await db.get_student_id(callback.from_user.id)
    await edit_or_send(
        callback,
        "Регистрация отменена.",
        main_menu(settings.is_admin(callback.from_user.id), student_id is not None),
    )
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
    required = ("first_name", "last_name", "subject", "grade", "goal", "lesson_format")
    if any(key not in data for key in required):
        await state.clear()
        await callback.answer("Данные регистрации потеряны. Начните заново.", show_alert=True)
        return

    await callback.answer("Создаю профиль…")
    payload = {
        "firstName": data["first_name"],
        "lastName": data["last_name"],
        "subject": data["subject"],
        "grade": int(data["grade"]),
        "goal": data["goal"],
        "lessonFormat": data["lesson_format"],
    }

    try:
        student = await api.register_student(payload)
    except ApiUnavailableError as exc:
        await callback.message.answer(f"⚠️ {html.escape(str(exc))}")
        return
    except ApiError as exc:
        await callback.message.answer(
            f"⚠️ Не удалось создать профиль: {html.escape(exc.message)}\n"
            "Проверьте данные и попробуйте снова.",
            reply_markup=registration_confirm(),
        )
        return

    student_id = student.get("id")
    if student_id is None or int(student_id) <= 0:
        await callback.message.answer(
            "⚠️ Сервис создал профиль, но не вернул корректный ID. Обратитесь к преподавателю."
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
    await edit_or_send(
        callback,
        f"✅ Профиль создан!\n\n"
        f"👤 <b>{name}</b>\n"
        f"🆔 ID ученика: <b>{student_id}</b>\n"
        f"{price_line}\n\n"
        f"Теперь можно выбирать свободные слоты и записываться на занятия.",
        main_menu(settings.is_admin(callback.from_user.id), True),
    )
