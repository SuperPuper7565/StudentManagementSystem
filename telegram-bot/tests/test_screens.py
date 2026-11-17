import asyncio
from datetime import datetime

from aiogram.methods import AnswerCallbackQuery
from aiogram.types import CallbackQuery, Chat, Message, User

from tgbot.middlewares import StaleCallbackMiddleware
from tgbot.screens import ScreenRegistry


def _callback(message_id: int, data: str, chat_id: int = 7) -> CallbackQuery:
    return CallbackQuery(
        id="callback-1",
        from_user=User(id=1, is_bot=False, first_name="Тест"),
        chat_instance="chat-instance",
        data=data,
        message=Message(
            message_id=message_id,
            date=datetime(2026, 10, 25, 12, 0),
            chat=Chat(id=chat_id, type="private"),
        ),
    )


class FakeBot:
    def __init__(self) -> None:
        self.methods: list[object] = []
        self.edits: list[dict] = []

    async def __call__(self, method):
        self.methods.append(method)
        return True

    async def edit_message_reply_markup(self, **kwargs):
        self.edits.append(kwargs)
        return True


def _run_middleware(middleware: StaleCallbackMiddleware, callback: CallbackQuery, bot: FakeBot):
    calls: list[object] = []

    async def handler(event, data):
        calls.append(event)
        return "handled"

    callback.as_(bot)
    result = asyncio.run(middleware(handler, callback, {}))
    return result, calls, bot


def test_registry_allows_everything_when_empty():
    registry = ScreenRegistry()

    assert registry.is_active(1, 10) is True
    assert registry.primary_id(1) is None


def test_registry_tracks_active_screen():
    registry = ScreenRegistry()
    registry.register(1, [10, 11], primary_id=11)

    assert registry.ids(1) == {10, 11}
    assert registry.primary_id(1) == 11
    assert registry.is_active(1, 11) is True
    assert registry.is_active(1, 12) is False
    assert registry.is_active(2, 10) is True  # другой чат не знает экранов


def test_registry_primary_defaults_to_max_id():
    registry = ScreenRegistry()
    registry.register(1, [3, 9, 5])

    assert registry.primary_id(1) == 9


def test_registry_forget_and_reset():
    registry = ScreenRegistry()
    registry.register(1, [10])
    registry.register(2, [20])

    registry.forget(1)
    assert registry.is_active(1, 10) is True  # пусто = мягкая деградация
    assert registry.is_active(2, 20) is True

    registry.reset()
    assert registry.ids(2) == set()


def test_middleware_blocks_stale_in_screen_button():
    registry = ScreenRegistry()
    registry.register(7, [11])
    middleware = StaleCallbackMiddleware(registry)
    callback = _callback(message_id=10, data="book:5")

    result, calls, bot = _run_middleware(middleware, callback, FakeBot())

    assert result is None
    assert calls == []
    assert isinstance(bot.methods[0], AnswerCallbackQuery)
    assert bot.methods[0].show_alert is True
    assert bot.edits == [{"chat_id": 7, "message_id": 10, "reply_markup": None}]


def test_middleware_allows_active_in_screen_button():
    registry = ScreenRegistry()
    registry.register(7, [10])
    middleware = StaleCallbackMiddleware(registry)

    result, calls, bot = _run_middleware(
        middleware, _callback(message_id=10, data="book:5"), FakeBot()
    )

    assert result == "handled"
    assert len(calls) == 1
    assert bot.methods == []


def test_middleware_allows_navigation_from_old_message():
    registry = ScreenRegistry()
    registry.register(7, [11])
    middleware = StaleCallbackMiddleware(registry)

    result, calls, bot = _run_middleware(
        middleware, _callback(message_id=10, data="my_bookings"), FakeBot()
    )

    assert result == "handled"
    assert len(calls) == 1
    assert bot.methods == []


def test_middleware_allows_non_screen_callback():
    registry = ScreenRegistry()
    registry.register(7, [11])
    middleware = StaleCallbackMiddleware(registry)

    result, calls, _ = _run_middleware(
        middleware, _callback(message_id=10, data="some:other"), FakeBot()
    )

    assert result == "handled"
    assert len(calls) == 1


def test_middleware_blocks_stale_registration_format_button():
    registry = ScreenRegistry()
    registry.register(7, [11])
    middleware = StaleCallbackMiddleware(registry)

    result, calls, _ = _run_middleware(
        middleware, _callback(message_id=10, data="format:ONLINE"), FakeBot()
    )

    assert result is None
    assert calls == []


def test_middleware_ignores_messages():
    middleware = StaleCallbackMiddleware(ScreenRegistry())
    message = Message(
        message_id=1,
        date=datetime(2026, 10, 25, 12, 0),
        chat=Chat(id=7, type="private"),
        text="привет",
    )
    calls: list[object] = []

    async def handler(event, data):
        calls.append(event)
        return "handled"

    result = asyncio.run(middleware(handler, message, {}))

    assert result == "handled"
    assert calls == [message]
