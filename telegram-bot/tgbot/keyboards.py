"""Inline-клавиатуры бота."""

from __future__ import annotations

from datetime import datetime
from typing import Any

from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup

from .formatters import format_datetime, slot_button_text

Button = InlineKeyboardButton


def _kb(*rows: list[Button]) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=list(rows))


def main_menu(is_admin: bool, registered: bool) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []

    if registered:
        rows.append(
            [
                Button(text="📅 Свободные слоты", callback_data="slots:0"),
                Button(text="🎟 Мои записи", callback_data="my_bookings"),
            ]
        )
        rows.append(
            [
                Button(text="👤 Профиль", callback_data="profile"),
                Button(text="ℹ️ Помощь", callback_data="help"),
            ]
        )
    else:
        rows.append(
            [
                Button(text="📝 Зарегистрироваться", callback_data="register"),
                Button(text="🔗 У меня есть ID", callback_data="link"),
            ]
        )
        rows.append([Button(text="ℹ️ Помощь", callback_data="help")])

    if is_admin:
        rows.append(
            [
                Button(text="➕ Создать слот", callback_data="admin:slot"),
                Button(text="📚 Мои предметы", callback_data="admin:subjects"),
            ]
        )
        rows.append(
            [
                Button(text="👥 Ученики", callback_data="admin:students"),
                Button(text="✅ Посещаемость", callback_data="admin:attendance"),
            ]
        )

    return _kb(*rows)


def onboarding_menu() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="📝 Зарегистрироваться", callback_data="register"),
            Button(text="🔗 У меня есть ID", callback_data="link"),
        ],
        [Button(text="ℹ️ Помощь", callback_data="help")],
    )


def back_menu() -> InlineKeyboardMarkup:
    return _kb([Button(text="🏠 В меню", callback_data="menu")])


def cancel_flow() -> InlineKeyboardMarkup:
    return _kb([Button(text="✖️ Отмена", callback_data="menu")])


def goal_skip_menu() -> InlineKeyboardMarkup:
    return _kb(
        [Button(text="⏭ Пропустить", callback_data="goal:skip")],
        [Button(text="✖️ Отмена", callback_data="menu")],
    )


def registration_subjects_menu(
    options: list[dict[str, Any]],
    selected: set[str],
) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []
    for option in options:
        code = str(option.get("code") or "").strip()
        if not code:
            continue
        name = str(option.get("name") or code)
        mark = "✅" if code in selected else "▫️"
        rows.append(
            [Button(text=f"{mark} {name}", callback_data=f"regsubj:{code}")]
        )
    rows.append([Button(text="✅ Готово", callback_data="regsubj:done")])
    rows.append([Button(text="✖️ Отмена", callback_data="menu")])
    return _kb(*rows)


def formats_menu() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="💻 Онлайн", callback_data="format:ONLINE"),
            Button(text="🏫 Офлайн", callback_data="format:OFFLINE"),
        ],
        [Button(text="✖️ Отмена", callback_data="menu")],
    )


def registration_confirm() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="✅ Подтвердить", callback_data="reg:confirm"),
            Button(text="✖️ Отмена", callback_data="reg:cancel"),
        ]
    )


def slots_menu(
    slots: list[dict[str, Any]],
    page: int,
    page_size: int,
    has_prev: bool,
    has_next: bool,
    now: datetime | None = None,
) -> InlineKeyboardMarkup:
    now = now or datetime.now()
    rows: list[list[Button]] = []

    start = page * page_size
    for slot in slots[start : start + page_size]:
        slot_id = slot.get("id")
        if slot_id is None:
            continue
        rows.append(
            [
                Button(
                    text=slot_button_text(slot, now),
                    callback_data=f"book:{int(slot_id)}",
                )
            ]
        )

    nav: list[Button] = []
    if has_prev:
        nav.append(Button(text="◀️", callback_data=f"slots:{page - 1}"))
    nav.append(Button(text="🔄 Обновить", callback_data=f"slots:{page}"))
    if has_next:
        nav.append(Button(text="▶️", callback_data=f"slots:{page + 1}"))
    if nav:
        rows.append(nav)

    rows.append([Button(text="🏠 В меню", callback_data="menu")])
    return _kb(*rows)


def subjects_menu(slot_id: int, subjects: list[str]) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []
    for index, subject in enumerate(subjects):
        label = subject if len(subject) <= 55 else subject[:54] + "…"
        rows.append(
            [
                Button(
                    text=f"📚 {label}",
                    callback_data=f"booksubj:{int(slot_id)}:{index}",
                )
            ]
        )
    rows.append([Button(text="↩️ Назад", callback_data="slots:0")])
    rows.append([Button(text="🏠 В меню", callback_data="menu")])
    return _kb(*rows)


def bookings_menu(rows_data: list[dict[str, Any]]) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []
    for booking in rows_data:
        booking_id = booking.get("booking_id")
        if booking_id is None:
            continue
        label = f"❌ Отменить #{booking_id} · {format_datetime(booking.get('start_time'), with_year=False)}"
        rows.append(
            [Button(text=label, callback_data=f"cancel:{int(booking_id)}")]
        )
    rows.append(
        [
            Button(text="🔄 Обновить", callback_data="my_bookings"),
            Button(text="🏠 В меню", callback_data="menu"),
        ]
    )
    return _kb(*rows)


def cancel_confirm(booking_id: int) -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="✅ Да, отменить", callback_data=f"cancel_yes:{booking_id}"),
            Button(text="↩️ Назад", callback_data="my_bookings"),
        ]
    )


def after_booking() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="🎟 Мои записи", callback_data="my_bookings"),
            Button(text="📅 Свободные слоты", callback_data="slots:0"),
        ],
        [Button(text="🏠 В меню", callback_data="menu")],
    )


def profile_menu() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="🔄 Обновить", callback_data="profile"),
            Button(text="🔗 Другой ID", callback_data="link"),
        ],
        [Button(text="🏠 В меню", callback_data="menu")],
    )


def admin_panel() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="➕ Создать слот", callback_data="admin:slot"),
            Button(text="📚 Мои предметы", callback_data="admin:subjects"),
        ],
        [
            Button(text="👥 Ученики", callback_data="admin:students"),
            Button(text="✅ Посещаемость", callback_data="admin:attendance"),
        ],
        [Button(text="🏠 В меню", callback_data="menu")],
    )


def teacher_subjects_menu(
    options: list[dict[str, Any]],
    selected: set[str],
) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []
    for option in options:
        code = str(option.get("code") or "").strip()
        if not code:
            continue
        name = str(option.get("name") or code)
        mark = "✅" if code in selected else "▫️"
        rows.append(
            [Button(text=f"{mark} {name}", callback_data=f"tsubj:{code}")]
        )
    rows.append([Button(text="🏠 В меню", callback_data="menu")])
    return _kb(*rows)


def after_slot_created() -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="➕ Ещё слот", callback_data="admin:slot"),
            Button(text="🏠 В меню", callback_data="menu"),
        ]
    )


def attendance_menu(
    rows_data: list[dict[str, Any]],
    page: int,
    page_size: int,
    has_prev: bool,
    has_next: bool,
) -> InlineKeyboardMarkup:
    rows: list[list[Button]] = []
    start = page * page_size

    for booking in rows_data[start : start + page_size]:
        booking_id = booking.get("booking_id")
        if booking_id is None:
            continue
        short = format_datetime(booking.get("start_time"), with_year=False)
        rows.append(
            [
                Button(text=f"✅ #{booking_id} · {short}", callback_data=f"att:{booking_id}:1"),
                Button(text=f"❌ #{booking_id} · {short}", callback_data=f"att:{booking_id}:0"),
            ]
        )

    nav: list[Button] = []
    if has_prev:
        nav.append(Button(text="◀️", callback_data=f"att_page:{page - 1}"))
    nav.append(Button(text="🔄 Обновить", callback_data=f"att_page:{page}"))
    if has_next:
        nav.append(Button(text="▶️", callback_data=f"att_page:{page + 1}"))
    if nav:
        rows.append(nav)

    rows.append([Button(text="⌨️ Ввести ID вручную", callback_data="att_manual")])
    rows.append([Button(text="🏠 В меню", callback_data="menu")])
    return _kb(*rows)


def attendance_confirm(booking_id: int) -> InlineKeyboardMarkup:
    return _kb(
        [
            Button(text="✅ Посетил", callback_data=f"att:{booking_id}:1"),
            Button(text="❌ Не пришёл", callback_data=f"att:{booking_id}:0"),
        ],
        [Button(text="↩️ Назад", callback_data="admin:attendance")],
    )
