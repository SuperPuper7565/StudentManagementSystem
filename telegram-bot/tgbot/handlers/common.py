"""Общие команды: меню, помощь, профиль, привязка studentId."""

from __future__ import annotations

import html

from aiogram import F, Router
from aiogram.filters import Command, CommandStart
from aiogram.fsm.context import FSMContext
from aiogram.types import CallbackQuery, InlineKeyboardMarkup, Message

from ..api import ApiClient, ApiError, ApiUnavailableError
from ..config import Settings
from ..filters import PLAIN_TEXT
from ..formatters import format_student_profile
from ..keyboards import (
    back_menu,
    flow_reply_menu,
    main_reply_menu,
    onboarding_menu,
    profile_menu,
)
from ..states import LinkStudent
from ..storage import Database
from ..ui import edit_prompt, render_screen, send_screen

router = Router(name="common")


def _welcome_text(first_name: str | None, registered: bool, is_admin: bool) -> str:
    name = html.escape(first_name or "друг")
    if registered:
        lines = [
            f"👋 Привет, <b>{name}</b>!",
            "",
            "Выберите действие в меню ниже.",
        ]
        if is_admin:
            lines.append("")
            lines.append("🛠 Доступна панель преподавателя.")
        return "\n".join(lines)

    return (
        f"👋 Привет, <b>{name}</b>!\n\n"
        "Это бот записи на занятия. Чтобы начать, создайте профиль ученика "
        "или привяжите существующий ID из сервиса.\n\n"
        "После регистрации вы сможете смотреть свободные слоты и записываться на уроки."
    )


def _help_text(is_admin: bool) -> str:
    lines = [
        "ℹ️ <b>Помощь</b>",
        "",
        "👤 <b>Для ученика</b>",
        "/start — главное меню",
        "/register — создать профиль ученика",
        "/link — привязать существующий ID ученика",
        "/slots — свободные слоты",
        "/bookings — мои записи",
        "/profile — мой профиль",
        "/cancel — сбросить текущее действие",
        "",
        "Запись создаётся нажатием на слот. Отменить её можно в «Мои записи».",
    ]
    if is_admin:
        lines.extend(
            [
                "",
                "🛠 <b>Для преподавателя</b>",
                "/admin — панель управления",
                "/students — список учеников",
                "Создание слотов, список учеников и отметка посещаемости.",
            ]
        )
    return "\n".join(lines)


async def render_main_menu(
    message: Message,
    db: Database,
    settings: Settings,
    text: str | None = None,
) -> None:
    """Главное меню с постоянной reply-клавиатурой."""
    telegram_id = message.from_user.id
    student_id = await db.get_student_id(telegram_id)
    is_admin = settings.is_admin(telegram_id)
    await send_screen(
        message,
        text
        or _welcome_text(
            message.from_user.first_name,
            student_id is not None,
            is_admin,
        ),
        reply_keyboard=main_reply_menu(is_admin),
    )


async def render_help(message: Message, settings: Settings) -> None:
    is_admin = settings.is_admin(message.from_user.id)
    await send_screen(message, _help_text(is_admin), inline_keyboard=back_menu())


async def render_admin_panel(message: Message) -> None:
    await send_screen(
        message,
        "🛠 <b>Панель преподавателя</b>\n\n"
        "Создавайте слоты, просматривайте учеников и отмечайте посещаемость.",
        reply_keyboard=main_reply_menu(True),
    )


# ---------------------------------------------------------------------- #
# Команды
# ---------------------------------------------------------------------- #
@router.message(CommandStart())
async def cmd_start(message: Message, state: FSMContext, db: Database, settings: Settings) -> None:
    await state.clear()
    await render_main_menu(message, db, settings)


@router.message(Command("menu"))
async def cmd_menu(message: Message, state: FSMContext, db: Database, settings: Settings) -> None:
    await state.clear()
    await render_main_menu(message, db, settings)


@router.message(Command("cancel"))
async def cmd_cancel(message: Message, state: FSMContext, db: Database, settings: Settings) -> None:
    await state.clear()
    await render_main_menu(message, db, settings, "Действие отменено. Вы снова в главном меню.")


@router.message(Command("help"))
async def cmd_help(message: Message, state: FSMContext, settings: Settings) -> None:
    await state.clear()
    await render_help(message, settings)


@router.message(Command("admin"))
async def cmd_admin(message: Message, state: FSMContext, settings: Settings) -> None:
    await state.clear()
    if not settings.is_admin(message.from_user.id):
        await message.answer("⛔ Эта команда доступна только преподавателю.")
        return

    await render_admin_panel(message)


@router.callback_query(F.data == "menu")
async def cb_menu(
    callback: CallbackQuery,
    state: FSMContext,
    db: Database,
    settings: Settings,
) -> None:
    await state.clear()
    student_id = await db.get_student_id(callback.from_user.id)
    is_admin = settings.is_admin(callback.from_user.id)
    await send_screen(
        callback.message,
        _welcome_text(callback.from_user.first_name, student_id is not None, is_admin),
        reply_keyboard=main_reply_menu(is_admin),
    )
    await callback.answer()


@router.callback_query(F.data == "help")
async def cb_help(callback: CallbackQuery, state: FSMContext, settings: Settings) -> None:
    await state.clear()
    is_admin = settings.is_admin(callback.from_user.id)
    await render_screen(callback, _help_text(is_admin), inline_keyboard=back_menu())
    await callback.answer()


# ---------------------------------------------------------------------- #
# Профиль
# ---------------------------------------------------------------------- #
async def _profile_payload(
    telegram_id: int,
    db: Database,
    api: ApiClient,
) -> tuple[str, InlineKeyboardMarkup]:
    student_id = await db.get_student_id(telegram_id)
    if student_id is None:
        return (
            "Профиль ещё не создан. Зарегистрируйтесь или привяжите существующий ID.",
            onboarding_menu(),
        )

    try:
        student = await api.find_student(student_id)
    except ApiUnavailableError as exc:
        return (f"⚠️ {html.escape(str(exc))}", profile_menu())
    except ApiError as exc:
        return (f"⚠️ Не удалось загрузить профиль: {html.escape(exc.message)}", profile_menu())

    if student is None:
        return (
            f"⚠️ Профиль с ID <b>{student_id}</b> не найден в сервисе.\n"
            "Возможно, база бэкенда была перезапущена. Привяжите актуальный ID.",
            profile_menu(),
        )

    return (format_student_profile(student_id, student), profile_menu())


async def render_profile(message: Message, db: Database, api: ApiClient) -> None:
    text, keyboard = await _profile_payload(message.from_user.id, db, api)
    await send_screen(message, text, inline_keyboard=keyboard)


@router.message(Command("profile"))
async def cmd_profile(message: Message, state: FSMContext, db: Database, api: ApiClient) -> None:
    await state.clear()
    await render_profile(message, db, api)


@router.callback_query(F.data == "profile")
async def cb_profile(callback: CallbackQuery, db: Database, api: ApiClient) -> None:
    text, keyboard = await _profile_payload(callback.from_user.id, db, api)
    await render_screen(callback, text, inline_keyboard=keyboard)
    await callback.answer()


# ---------------------------------------------------------------------- #
# Привязка существующего ID
# ---------------------------------------------------------------------- #
def _link_prompt(student_id: int | None) -> str:
    hint = f"Текущий привязанный ID: <b>{student_id}</b>.\n\n" if student_id else ""
    return (
        f"{hint}Введите числовой ID ученика из сервиса.\n"
        "Его можно посмотреть в списке учеников у преподавателя."
    )


@router.message(Command("link"))
async def cmd_link(message: Message, state: FSMContext, db: Database) -> None:
    student_id = await db.get_student_id(message.from_user.id)
    await state.set_state(LinkStudent.waiting_id)
    await send_screen(
        message,
        _link_prompt(student_id),
        reply_keyboard=flow_reply_menu(),
    )


@router.callback_query(F.data == "link")
async def cb_link(callback: CallbackQuery, state: FSMContext, db: Database) -> None:
    student_id = await db.get_student_id(callback.from_user.id)
    await state.set_state(LinkStudent.waiting_id)
    await send_screen(
        callback.message,
        _link_prompt(student_id),
        reply_keyboard=flow_reply_menu(),
    )
    await callback.answer()


@router.message(LinkStudent.waiting_id, PLAIN_TEXT)
async def process_link_id(
    message: Message,
    state: FSMContext,
    api: ApiClient,
    db: Database,
    settings: Settings,
) -> None:
    current_id = await db.get_student_id(message.from_user.id)
    prompt = _link_prompt(current_id)
    raw = (message.text or "").strip()
    if not raw.isdigit():
        await edit_prompt(message, f"⚠️ ID должен состоять только из цифр.\n\n{prompt}")
        return

    student_id = int(raw)
    try:
        student = await api.find_student(student_id)
    except ApiUnavailableError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(str(exc))}\n\n{prompt}")
        return
    except ApiError as exc:
        await edit_prompt(message, f"⚠️ {html.escape(exc.message)}\n\n{prompt}")
        return

    if student is None:
        await edit_prompt(
            message,
            f"⚠️ Ученик с ID <b>{student_id}</b> не найден. "
            f"Проверьте ID и попробуйте снова.\n\n{prompt}",
        )
        return

    await db.link_student(message.from_user.id, student_id)
    await state.clear()

    name = html.escape(str(student.get("fullName") or f"ID {student_id}"))
    await send_screen(
        message,
        f"✅ Аккаунт привязан к ученику <b>{name}</b>.",
        reply_keyboard=main_reply_menu(settings.is_admin(message.from_user.id)),
    )


@router.message(LinkStudent.waiting_id, ~F.text)
async def process_link_non_text(message: Message) -> None:
    await message.answer("Отправьте числовой ID ученика текстовым сообщением.")
