from datetime import datetime
from decimal import Decimal

import pytest

from tgbot.formatters import (
    chunk_text,
    clean_text,
    format_booking_line,
    format_datetime,
    format_goal,
    format_price,
    format_slot_conflict,
    format_student_card,
    format_time_range,
    is_slot_booked,
    parse_grade,
    parse_price,
    parse_slot_datetime,
    slot_button_text,
)


def test_format_goal_supports_free_text_and_legacy_values():
    assert format_goal(None) == "не указана"
    assert format_goal("") == "не указана"
    assert format_goal("Подготовка к ЕГЭ") == "Подготовка к ЕГЭ"
    assert format_goal("EGE") == "ЕГЭ"


def test_parse_slot_datetime_with_year():
    assert parse_slot_datetime("25.10.2026 14:30") == datetime(2026, 10, 25, 14, 30)
    assert parse_slot_datetime("2026-10-25 14:30") == datetime(2026, 10, 25, 14, 30)


def test_parse_slot_datetime_without_year():
    now = datetime(2026, 10, 24, 12, 0)
    assert parse_slot_datetime("25.10 14:30", now=now) == datetime(2026, 10, 25, 14, 30)


def test_parse_slot_datetime_invalid():
    with pytest.raises(ValueError):
        parse_slot_datetime("завтра")


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("1500", Decimal("1500")),
        ("1 500,50", Decimal("1500.50")),
        ("1500 ₽", Decimal("1500")),
        ("1500.00", Decimal("1500.00")),
    ],
)
def test_parse_price(raw, expected):
    assert parse_price(raw) == expected


@pytest.mark.parametrize("raw", ["0", "-10", "abc", "10.123", "2000000"])
def test_parse_price_rejects_invalid(raw):
    with pytest.raises(ValueError):
        parse_price(raw)


def test_parse_grade():
    assert parse_grade("9") == 9
    with pytest.raises(ValueError):
        parse_grade("12")
    with pytest.raises(ValueError):
        parse_grade("девятый")


def test_clean_text_collapses_spaces():
    assert clean_text("  Иван   Иванович ", "Имя") == "Иван Иванович"


def test_format_time_range():
    assert (
        format_time_range("2026-10-25T14:00:00", "2026-10-25T15:30:00")
        == "25.10.2026 14:00–15:30"
    )


def test_format_datetime_without_year():
    assert format_datetime("2026-10-25T14:00:00", with_year=False) == "25.10 14:00"


def test_slot_button_text_uses_weekday():
    slot = {"startTime": "2026-10-26T14:00:00"}
    assert slot_button_text(slot, now=datetime(2026, 10, 24)) == "Пн 26.10 · 14:00"


def test_slot_button_text_includes_format():
    slot = {"startTime": "2026-10-26T14:00:00", "format": "ONLINE"}
    assert (
        slot_button_text(slot, now=datetime(2026, 10, 24))
        == "Пн 26.10 · 14:00 · Онлайн"
    )


def test_slot_booked_accepts_both_names():
    assert is_slot_booked({"booked": True})
    assert is_slot_booked({"isBooked": True})
    assert not is_slot_booked({})


def test_format_price():
    assert format_price(2000) == "2 000 ₽"
    assert format_price("1500.50") == "1 500,50 ₽"
    assert format_price(None) == "—"


def test_format_student_card_escapes_html():
    card = format_student_card(
        {
            "id": 3,
            "fullName": "<Иван>",
            "subject": "Математика",
            "subjects": ["Математика", "Информатика"],
            "grade": 9,
            "goalDescription": "EGE",
            "statusDescription": "Активен (ONLINE)",
            "lessonPrice": 1500,
            "priceDetails": "Индивидуальная цена",
        }
    )
    assert "&lt;Иван&gt;" in card
    assert "онлайн" in card
    assert "ЕГЭ" in card
    assert "1 500 ₽" in card
    assert "Индивидуальная цена" in card
    assert "Математика, Информатика" in card


def test_format_booking_line_includes_subject_and_format():
    line = format_booking_line(
        {
            "booking_id": 7,
            "start_time": "2026-10-25T14:00:00",
            "end_time": "2026-10-25T15:30:00",
            "subject": "Информатика",
            "format": "OFFLINE",
            "status": "active",
        }
    )
    assert "Запись #7" in line
    assert "Информатика" in line
    assert "Офлайн" in line


def test_format_slot_conflict_describes_booked_slot():
    text = format_slot_conflict(
        {
            "startTime": "2026-10-25T14:00:00",
            "endTime": "2026-10-25T15:30:00",
            "overlap": True,
            "conflicts": [
                {
                    "id": 12,
                    "startTime": "2026-10-25T13:30:00",
                    "endTime": "2026-10-25T15:00:00",
                    "format": "OFFLINE",
                    "booked": True,
                }
            ],
        }
    )

    assert "25.10.2026 14:00–15:30" in text
    assert "25.10.2026 13:30–15:00" in text
    assert "Офлайн" in text
    assert "уже забронировано" in text
    assert "Введите другое время." in text


def test_format_slot_conflict_marks_unbooked_slot():
    text = format_slot_conflict(
        {
            "startTime": "2026-10-25T14:00:00",
            "endTime": "2026-10-25T15:30:00",
            "overlap": True,
            "conflicts": [{"startTime": "2026-10-25T14:00:00", "booked": False}],
        }
    )

    assert "слот уже создан" in text
    assert "уже забронировано" not in text


def test_format_slot_conflict_limits_listed_conflicts():
    conflicts = [
        {"startTime": f"2026-10-25T{hour:02d}:00:00", "booked": False}
        for hour in range(14, 19)
    ]

    text = format_slot_conflict(
        {
            "startTime": "2026-10-25T14:00:00",
            "endTime": "2026-10-25T15:30:00",
            "conflicts": conflicts,
        },
        limit=3,
    )

    assert text.count("📅") == 3
    assert "…и ещё 2." in text


def test_format_slot_conflict_handles_missing_conflicts():
    text = format_slot_conflict({"startTime": "2026-10-25T14:00:00"})

    assert "В это время уже есть занятие." in text
    assert "…и ещё" not in text


def test_chunk_text_keeps_content():
    text = "\n".join(f"Строка {i}" for i in range(100))
    chunks = chunk_text(text, limit=300)
    assert len(chunks) > 1
    assert "\n".join(chunks) == text
