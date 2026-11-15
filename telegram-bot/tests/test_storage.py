import asyncio

import aiosqlite

from tgbot.storage import Database


def _run(coro):
    return asyncio.run(coro)


def test_link_student_is_upserted(tmp_path):
    db = Database(tmp_path / "bot.sqlite3")

    async def scenario():
        await db.init()
        await db.link_student(100, 1)
        assert await db.get_student_id(100) == 1
        await db.link_student(100, 2)
        assert await db.get_student_id(100) == 2
        await db.unlink_student(100)
        assert await db.get_student_id(100) is None

    _run(scenario())


def test_booking_lifecycle(tmp_path):
    db = Database(tmp_path / "bot.sqlite3")
    booking = {
        "id": 42,
        "subject": "Математика",
        "slot": {
            "id": 7,
            "startTime": "2026-10-25T14:00:00",
            "endTime": "2026-10-25T15:30:00",
            "format": "ONLINE",
        },
    }

    async def scenario():
        await db.init()
        await db.add_booking(booking, telegram_id=100, student_id=3)

        rows = await db.list_bookings(100)
        assert len(rows) == 1
        assert rows[0]["booking_id"] == 42
        assert rows[0]["student_id"] == 3
        assert rows[0]["subject"] == "Математика"
        assert rows[0]["format"] == "ONLINE"
        assert rows[0]["status"] == "active"

        await db.set_attendance(42, True)
        row = await db.get_booking(42)
        assert row["attended"] == 1

        await db.mark_cancelled(42)
        assert await db.list_bookings(100) == []
        assert len(await db.list_bookings(100, include_cancelled=True)) == 1

    _run(scenario())


def test_init_adds_new_columns_to_existing_database(tmp_path):
    path = tmp_path / "old.sqlite3"

    async def scenario():
        async with aiosqlite.connect(path) as db:
            await db.execute(
                """
                CREATE TABLE bookings (
                    booking_id INTEGER PRIMARY KEY,
                    telegram_id INTEGER NOT NULL,
                    student_id  INTEGER NOT NULL,
                    slot_id     INTEGER,
                    start_time  TEXT    NOT NULL,
                    end_time    TEXT,
                    status      TEXT    NOT NULL DEFAULT 'active',
                    attended    INTEGER,
                    created_at  TEXT    NOT NULL
                )
                """
            )
            await db.commit()

        await Database(path).init()

        async with aiosqlite.connect(path) as db:
            async with db.execute("PRAGMA table_info(bookings)") as cursor:
                columns = {row[1] for row in await cursor.fetchall()}

        assert "subject" in columns
        assert "format" in columns

    _run(scenario())


def test_bookings_are_isolated_by_telegram_id(tmp_path):
    db = Database(tmp_path / "bot.sqlite3")

    async def scenario():
        await db.init()
        await db.add_booking({"id": 1, "slot": {"startTime": "2026-10-25T14:00:00"}}, 10, 1)
        await db.add_booking({"id": 2, "slot": {"startTime": "2026-10-26T14:00:00"}}, 20, 2)

        assert [row["booking_id"] for row in await db.list_bookings(10)] == [1]
        assert [row["booking_id"] for row in await db.list_bookings(20)] == [2]
        assert len(await db.list_bookings()) == 2

    _run(scenario())
