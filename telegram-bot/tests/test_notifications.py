import asyncio

from tgbot.handlers.booking import _notify_tutors


class FakeBot:
    def __init__(self, fail_for: int | None = None) -> None:
        self.fail_for = fail_for
        self.sent: list[tuple[int, str, object]] = []

    async def send_message(self, chat_id: int, text: str, reply_markup=None) -> None:
        if chat_id == self.fail_for:
            raise RuntimeError("telegram is unavailable")
        self.sent.append((chat_id, text, reply_markup))


def test_notify_tutors_sends_to_all_admins_and_survives_errors():
    bot = FakeBot(fail_for=2)

    asyncio.run(_notify_tutors(bot, {1, 2, 3}, "hello"))

    assert sorted(chat_id for chat_id, _, _ in bot.sent) == [1, 3]
    assert all(text == "hello" for _, text, _ in bot.sent)
    assert all(markup is None for _, _, markup in bot.sent)


def test_notify_tutors_passes_keyboard():
    bot = FakeBot()
    keyboard = object()

    asyncio.run(_notify_tutors(bot, {1}, "hello", keyboard=keyboard))

    assert len(bot.sent) == 1
    assert bot.sent[0][2] is keyboard
