import asyncio

from tgbot.handlers.booking import _notify_tutors


class FakeBot:
    def __init__(self, fail_for: int | None = None) -> None:
        self.fail_for = fail_for
        self.sent: list[tuple[int, str]] = []

    async def send_message(self, chat_id: int, text: str) -> None:
        if chat_id == self.fail_for:
            raise RuntimeError("telegram is unavailable")
        self.sent.append((chat_id, text))


def test_notify_tutors_sends_to_all_admins_and_survives_errors():
    bot = FakeBot(fail_for=2)

    asyncio.run(_notify_tutors(bot, {1, 2, 3}, "hello"))

    assert sorted(chat_id for chat_id, _ in bot.sent) == [1, 3]
    assert all(text == "hello" for _, text in bot.sent)
