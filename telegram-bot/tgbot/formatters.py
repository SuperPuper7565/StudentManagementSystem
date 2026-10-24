"""Форматирование данных и разбор пользовательского ввода."""

from __future__ import annotations

import html
import re
from datetime import datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any

WEEKDAYS = ("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

GOAL_TITLES = {
    "OGE": "ОГЭ",
    "EGE": "ЕГЭ",
    "OTHER": "Другое / успеваемость",
}

FORMAT_TITLES = {
    "ONLINE": "Онлайн",
    "OFFLINE": "Офлайн",
}

_DATETIME_FORMATS = (
    "%d.%m.%Y %H:%M",
    "%d.%m.%Y %H.%M",
    "%Y-%m-%d %H:%M",
    "%d.%m.%Y",
)


# ---------------------------------------------------------------------- #
# Даты
# ---------------------------------------------------------------------- #
def parse_iso_datetime(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None


def format_datetime(value: str | datetime | None, with_year: bool = True) -> str:
    if value is None:
        return "—"
    dt = value if isinstance(value, datetime) else parse_iso_datetime(value)
    if dt is None:
        return str(value)
    return dt.strftime("%d.%m.%Y %H:%M" if with_year else "%d.%m %H:%M")


def format_time_range(start: str | None, end: str | None = None) -> str:
    start_dt = parse_iso_datetime(start)
    if start_dt is None:
        return "—"
    end_dt = parse_iso_datetime(end) or (start_dt + timedelta(minutes=90))
    return f"{start_dt:%d.%m.%Y %H:%M}–{end_dt:%H:%M}"


def slot_button_text(slot: dict[str, Any], now: datetime | None = None) -> str:
    start_dt = parse_iso_datetime(slot.get("startTime"))
    if start_dt is None:
        return f"Слот #{slot.get('id', '?')}"
    with_year = now is None or start_dt.year != now.year
    date_part = start_dt.strftime("%d.%m.%Y" if with_year else "%d.%m")
    return f"{WEEKDAYS[start_dt.weekday()]} {date_part} · {start_dt:%H:%M}"


def format_slot_line(slot: dict[str, Any]) -> str:
    start = slot.get("startTime")
    end = slot.get("endTime")
    return f"📅 <b>{format_time_range(start, end)}</b>"


def format_booking_line(row: dict[str, Any]) -> str:
    booking_id = row.get("booking_id", "?")
    line = f"🎟 <b>Запись #{booking_id}</b> — {format_time_range(row.get('start_time'), row.get('end_time'))}"
    if row.get("status") == "cancelled":
        line += " <i>(отменена)</i>"
    elif row.get("attended") == 1:
        line += " ✅"
    elif row.get("attended") == 0:
        line += " ❌"
    return line


def parse_slot_datetime(text: str, now: datetime | None = None) -> datetime:
    """Разбирает дату/время слота из сообщения пользователя."""
    now = now or datetime.now()
    raw = " ".join(text.strip().split())

    for fmt in _DATETIME_FORMATS:
        try:
            return datetime.strptime(raw, fmt)
        except ValueError:
            continue

    # Формат без года: 25.10 14:00
    try:
        dt = datetime.strptime(f"{raw} {now.year}", "%d.%m %H:%M %Y")
    except ValueError as exc:
        raise ValueError(
            "Не удалось распознать дату. Используйте формат "
            "«25.10.2026 14:00» или «2026-10-25 14:00»."
        ) from exc

    # Если указана дата из далёкого прошлого, вероятно, имелся в виду следующий год.
    if dt < now and (now - dt).days > 180:
        dt = dt.replace(year=now.year + 1)
    return dt


# ---------------------------------------------------------------------- #
# Пользовательский ввод
# ---------------------------------------------------------------------- #
def clean_text(value: str, field: str, max_length: int = 80) -> str:
    cleaned = " ".join(value.strip().split())
    if not cleaned:
        raise ValueError(f"{field} не может быть пустым.")
    if len(cleaned) > max_length:
        raise ValueError(f"{field} слишком длинное (максимум {max_length} символов).")
    return cleaned


def parse_grade(text: str) -> int:
    try:
        grade = int(text.strip())
    except ValueError as exc:
        raise ValueError("Класс должен быть числом от 1 до 11.") from exc
    if not 1 <= grade <= 11:
        raise ValueError("Класс должен быть числом от 1 до 11.")
    return grade


def parse_price(text: str) -> Decimal:
    raw = text.strip().lower()
    raw = re.sub(r"(руб\.?|р\.?|₽)\s*$", "", raw)
    raw = raw.replace("\u00a0", "").replace(" ", "").replace(",", ".")
    try:
        value = Decimal(raw)
    except InvalidOperation as exc:
        raise ValueError("Цена должна быть числом, например 1500 или 1500,50.") from exc

    if not value.is_finite() or value <= 0:
        raise ValueError("Цена должна быть больше нуля.")
    if value > Decimal("1000000"):
        raise ValueError("Цена выглядит слишком большой (максимум 1 000 000).")
    if value.as_tuple().exponent < -2:
        raise ValueError("Цена может содержать не больше двух знаков после запятой.")
    return value


# ---------------------------------------------------------------------- #
# Студенты
# ---------------------------------------------------------------------- #
def format_goal(goal: Any) -> str:
    if not goal:
        return "не указана"
    return GOAL_TITLES.get(str(goal), str(goal))


def format_lesson_format(lesson_format: Any) -> str:
    if not lesson_format:
        return "—"
    return FORMAT_TITLES.get(str(lesson_format), str(lesson_format))


def format_student_card(student: dict[str, Any]) -> str:
    """Карточка из StudentAdminDTO (GET /students/admin-list)."""
    full_name = html.escape(str(student.get("fullName") or "Без имени"))
    subject = html.escape(str(student.get("subject") or "—"))
    grade = student.get("grade", "—")
    goal = html.escape(format_goal(student.get("goalDescription")))
    status = html.escape(str(student.get("statusDescription") or "—"))
    status = status.replace("ONLINE", "онлайн").replace("OFFLINE", "офлайн")

    return (
        f"👤 <b>{full_name}</b> (ID: {student.get('id', '?')})\n"
        f"📚 {subject} · {grade} класс\n"
        f"🎯 Цель: {goal}\n"
        f"📌 Статус: {status}"
    )


def format_student_profile(student_id: int, student: dict[str, Any]) -> str:
    return "🪪 <b>Мой профиль</b>\n\n" + format_student_card(
        {**student, "id": student.get("id", student_id)}
    )


# ---------------------------------------------------------------------- #
# Служебное
# ---------------------------------------------------------------------- #
def is_slot_booked(slot: dict[str, Any]) -> bool:
    """Jackson может отдать как booked, так и isBooked."""
    return bool(slot.get("booked", slot.get("isBooked", False)))


def is_booking_cancelled(booking: dict[str, Any]) -> bool:
    return bool(booking.get("cancelled", booking.get("isCancelled", False)))


def is_booking_attended(booking: dict[str, Any]) -> bool:
    return bool(booking.get("attended", booking.get("isAttended", False)))


def chunk_text(text: str, limit: int = 3800) -> list[str]:
    """Делит длинный текст на сообщения, не разрывая строки без нужды."""
    if len(text) <= limit:
        return [text]

    chunks: list[str] = []
    current: list[str] = []
    current_length = 0

    for line in text.splitlines():
        line_length = len(line) + 1
        if line_length > limit:
            if current:
                chunks.append("\n".join(current))
                current, current_length = [], 0
            chunks.extend(line[i : i + limit] for i in range(0, len(line), limit))
            continue
        if current_length + line_length > limit and current:
            chunks.append("\n".join(current))
            current, current_length = [], 0
        current.append(line)
        current_length += line_length

    if current:
        chunks.append("\n".join(current))
    return chunks
