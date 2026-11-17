import asyncio
from types import SimpleNamespace

import pytest
from aiogram.exceptions import TelegramBadRequest
from aiogram.types import Chat, InlineKeyboardMarkup, InlineKeyboardButton, ReplyKeyboardMarkup, KeyboardButton

from tgbot.screens import registry
from tgbot.ui import edit_prompt, render_screen, send_screen


@pytest.fixture(autouse=True)
def _reset_registry():
    registry.reset()
    yield
    registry.reset()


def _inline(text: str = "ok") -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(
        inline_keyboard=[[InlineKeyboardButton(text=text, callback_data="menu")]]
    )


def _reply(text: str = "Меню") -> ReplyKeyboardMarkup:
    return ReplyKeyboardMarkup(
        keyboard=[[KeyboardButton(text=text)]],
        resize_keyboard=True,
    )


class FakeBot:
    def __init__(self) -> None:
        self.cleared: list[dict] = []
        self.edited: list[dict] = []
        self.fail_edit_text: Exception | None = None

    async def edit_message_reply_markup(self, **kwargs):
        self.cleared.append(kwargs)
        return True

    async def edit_message_text(self, **kwargs):
        if self.fail_edit_text is not None:
            raise self.fail_edit_text
        self.edited.append(kwargs)
        return True


class FakeMessage:
    def __init__(self, chat_id: int = 1, message_id: int = 10) -> None:
        self.chat = Chat(id=chat_id, type="private")
        self.message_id = message_id
        self.bot = FakeBot()
        self.answers: list[tuple[str, object]] = []
        self.edits: list[tuple[str, object]] = []
        self.fail_edit_text: Exception | None = None
        self._next_id = 100

    async def answer(self, text: str, reply_markup=None):
        self.answers.append((text, reply_markup))
        self._next_id += 1
        return SimpleNamespace(message_id=self._next_id)

    async def edit_text(self, text: str, reply_markup=None):
        if self.fail_edit_text is not None:
            raise self.fail_edit_text
        self.edits.append((text, reply_markup))
        return True


def test_send_screen_sends_message_and_registers_it():
    message = FakeMessage()
    keyboard = _inline()

    asyncio.run(send_screen(message, "Привет", inline_keyboard=keyboard))

    assert message.answers == [("Привет", keyboard)]
    assert registry.ids(1) == {101}
    assert registry.primary_id(1) == 101


def test_send_screen_passes_reply_keyboard():
    message = FakeMessage()
    keyboard = _reply()

    asyncio.run(send_screen(message, "Меню", reply_keyboard=keyboard))

    assert message.answers[0][1] is keyboard


def test_send_screen_chunks_long_text_and_keeps_keyboard_on_last_chunk():
    message = FakeMessage()
    text = "\n".join(f"Строка {i}" for i in range(1000))
    keyboard = _inline()

    asyncio.run(send_screen(message, text, inline_keyboard=keyboard))

    assert len(message.answers) > 1
    assert all(markup is None for _, markup in message.answers[:-1])
    assert message.answers[-1][1] is keyboard
    assert len(registry.ids(1)) == len(message.answers)
    assert registry.primary_id(1) == 100 + len(message.answers)


def test_send_screen_clears_previous_keyboards():
    message = FakeMessage()
    registry.register(1, [3, 4])

    asyncio.run(send_screen(message, "Новый экран", inline_keyboard=_inline()))

    cleared_ids = {call["message_id"] for call in message.bot.cleared}
    assert cleared_ids == {3, 4}
    assert all(call["chat_id"] == 1 for call in message.bot.cleared)


def test_render_screen_edits_current_message():
    message = FakeMessage(message_id=10)
    keyboard = _inline()

    callback = SimpleNamespace(message=message, bot=message.bot)
    asyncio.run(render_screen(callback, "Экран", inline_keyboard=keyboard))

    assert message.edits == [("Экран", keyboard)]
    assert message.answers == []
    assert registry.ids(1) == {10}
    assert registry.primary_id(1) == 10


def test_render_screen_with_reply_keyboard_sends_new_message():
    message = FakeMessage(message_id=10)
    keyboard = _reply()

    callback = SimpleNamespace(message=message, bot=message.bot)
    asyncio.run(render_screen(callback, "Меню", reply_keyboard=keyboard))

    assert message.edits == []
    assert message.answers == [("Меню", keyboard)]
    assert registry.primary_id(1) == 101


def test_render_screen_falls_back_to_sending_when_edit_fails():
    message = FakeMessage(message_id=10)
    message.fail_edit_text = TelegramBadRequest(None, "message can't be edited")
    keyboard = _inline()

    callback = SimpleNamespace(message=message, bot=message.bot)
    asyncio.run(render_screen(callback, "Новый", inline_keyboard=keyboard))

    assert message.answers == [("Новый", keyboard)]
    assert registry.primary_id(1) == 101


def test_render_screen_ignores_not_modified():
    message = FakeMessage(message_id=10)
    message.fail_edit_text = TelegramBadRequest(None, "message is not modified")

    callback = SimpleNamespace(message=message, bot=message.bot)
    asyncio.run(render_screen(callback, "Тот же экран", inline_keyboard=_inline()))

    assert message.answers == []
    assert registry.ids(1) == {10}


def test_edit_prompt_edits_primary_screen():
    message = FakeMessage()
    registry.register(1, [42], primary_id=42)
    keyboard = _inline()

    asyncio.run(edit_prompt(message, "Ошибка ввода", inline_keyboard=keyboard))

    assert message.bot.edited == [
        {
            "chat_id": 1,
            "message_id": 42,
            "text": "Ошибка ввода",
            "reply_markup": keyboard,
        }
    ]
    assert registry.primary_id(1) == 42


def test_edit_prompt_sends_when_no_screen_yet():
    message = FakeMessage()

    asyncio.run(edit_prompt(message, "Первая подсказка", inline_keyboard=_inline()))

    assert len(message.answers) == 1
    assert registry.primary_id(1) == 101


def test_edit_prompt_falls_back_to_new_message_on_error():
    message = FakeMessage()
    registry.register(1, [42], primary_id=42)
    message.bot.fail_edit_text = TelegramBadRequest(None, "message can't be edited")
    keyboard = _inline()

    asyncio.run(edit_prompt(message, "Подсказка", inline_keyboard=keyboard))

    assert len(message.answers) == 1
    assert message.answers[0][0] == "Подсказка"
    assert message.answers[0][1] is keyboard
    assert registry.primary_id(1) == 101
