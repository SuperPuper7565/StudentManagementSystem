"""Локальное хранилище бота (SQLite).

Бэкенд не умеет искать студента по Telegram ID и не отдаёт список бронирований,
поэтому бот хранит у себя:
  * связь telegram_id -> student_id;
  * записи, созданные через бота (чтобы показать «Мои записи» и отменять их).
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import Any

import aiosqlite

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    telegram_id INTEGER PRIMARY KEY,
    student_id  INTEGER NOT NULL,
    created_at  TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS bookings (
    booking_id INTEGER PRIMARY KEY,
    telegram_id INTEGER NOT NULL,
    student_id  INTEGER NOT NULL,
    slot_id     INTEGER,
    start_time  TEXT    NOT NULL,
    end_time    TEXT,
    subject     TEXT,
    format      TEXT,
    status      TEXT    NOT NULL DEFAULT 'active',
    attended    INTEGER,
    created_at  TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bookings_telegram_id
    ON bookings (telegram_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status
    ON bookings (status);
"""


class Database:
    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)

    async def init(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        async with aiosqlite.connect(self.path) as db:
            await db.execute("PRAGMA journal_mode=WAL")
            await db.executescript(SCHEMA)

            async with db.execute("PRAGMA table_info(bookings)") as cursor:
                columns = {row[1] for row in await cursor.fetchall()}
            if "subject" not in columns:
                await db.execute("ALTER TABLE bookings ADD COLUMN subject TEXT")
            if "format" not in columns:
                await db.execute("ALTER TABLE bookings ADD COLUMN format TEXT")

            await db.commit()

    # ------------------------------------------------------------------ #
    # Пользователи
    # ------------------------------------------------------------------ #
    async def link_student(self, telegram_id: int, student_id: int) -> None:
        async with aiosqlite.connect(self.path) as db:
            await db.execute(
                """
                INSERT INTO users (telegram_id, student_id, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT (telegram_id) DO UPDATE SET student_id = excluded.student_id
                """,
                (telegram_id, student_id, _now_iso()),
            )
            await db.commit()

    async def get_student_id(self, telegram_id: int) -> int | None:
        async with aiosqlite.connect(self.path) as db:
            async with db.execute(
                "SELECT student_id FROM users WHERE telegram_id = ?", (telegram_id,)
            ) as cursor:
                row = await cursor.fetchone()
        return int(row[0]) if row else None

    async def unlink_student(self, telegram_id: int) -> None:
        async with aiosqlite.connect(self.path) as db:
            await db.execute("DELETE FROM users WHERE telegram_id = ?", (telegram_id,))
            await db.commit()

    # ------------------------------------------------------------------ #
    # Бронирования
    # ------------------------------------------------------------------ #
    async def add_booking(
        self,
        booking: dict[str, Any],
        telegram_id: int,
        student_id: int,
        slot: dict[str, Any] | None = None,
    ) -> None:
        booking_id = _as_int(booking.get("id"))
        if booking_id is None or booking_id <= 0:
            raise ValueError("В ответе сервиса отсутствует корректный id бронирования")

        slot_data = booking.get("slot") or slot or {}
        start_time = slot_data.get("startTime")
        end_time = slot_data.get("endTime")
        if not start_time:
            raise ValueError("В ответе сервиса отсутствует время слота")

        subject = booking.get("subject")
        if subject is None:
            subject = slot_data.get("subject")
        lesson_format = slot_data.get("format") or booking.get("format")

        async with aiosqlite.connect(self.path) as db:
            await db.execute(
                """
                INSERT INTO bookings (
                    booking_id, telegram_id, student_id, slot_id,
                    start_time, end_time, subject, format, status, attended, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', NULL, ?)
                ON CONFLICT (booking_id) DO UPDATE SET
                    telegram_id = excluded.telegram_id,
                    student_id  = excluded.student_id,
                    slot_id     = excluded.slot_id,
                    start_time  = excluded.start_time,
                    end_time    = excluded.end_time,
                    subject     = excluded.subject,
                    format      = excluded.format
                """,
                (
                    booking_id,
                    telegram_id,
                    student_id,
                    _as_int(slot_data.get("id")),
                    str(start_time),
                    str(end_time) if end_time else None,
                    str(subject) if subject else None,
                    str(lesson_format) if lesson_format else None,
                    _now_iso(),
                ),
            )
            await db.commit()

    async def get_booking(self, booking_id: int) -> dict[str, Any] | None:
        async with aiosqlite.connect(self.path) as db:
            db.row_factory = aiosqlite.Row
            async with db.execute(
                "SELECT * FROM bookings WHERE booking_id = ?", (booking_id,)
            ) as cursor:
                row = await cursor.fetchone()
        return dict(row) if row else None

    async def list_bookings(
        self,
        telegram_id: int | None = None,
        include_cancelled: bool = False,
    ) -> list[dict[str, Any]]:
        query = "SELECT * FROM bookings"
        conditions: list[str] = []
        params: list[Any] = []

        if telegram_id is not None:
            conditions.append("telegram_id = ?")
            params.append(telegram_id)
        if not include_cancelled:
            conditions.append("status = 'active'")
        if conditions:
            query += " WHERE " + " AND ".join(conditions)
        query += " ORDER BY start_time ASC, booking_id ASC"

        async with aiosqlite.connect(self.path) as db:
            db.row_factory = aiosqlite.Row
            async with db.execute(query, params) as cursor:
                rows = await cursor.fetchall()
        return [dict(row) for row in rows]

    async def mark_cancelled(self, booking_id: int) -> None:
        async with aiosqlite.connect(self.path) as db:
            await db.execute(
                "UPDATE bookings SET status = 'cancelled' WHERE booking_id = ?",
                (booking_id,),
            )
            await db.commit()

    async def set_attendance(self, booking_id: int, attended: bool) -> None:
        async with aiosqlite.connect(self.path) as db:
            await db.execute(
                "UPDATE bookings SET attended = ? WHERE booking_id = ?",
                (int(attended), booking_id),
            )
            await db.commit()


def _now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _as_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None
