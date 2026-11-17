"""Общие UI-хелперы: один активный экран на чат, безопасное редактирование."""

from __future__ import annotations

import logging
from collections.abc import Iterable

from aiogram.exceptions import TelegramBadRequest
from aiogram.types import (
    CallbackQuery,
    InlineKeyboardMarkup,
    Message,
    ReplyKeyboardMarkup,
)

from .formatters import chunk_text
from .screens import registry

logger = logging.getLogger(__name__)

Keyboard = InlineKeyboardMarkup | ReplyKeyboardMarkup | None


def _is_not_modified(exc: TelegramBadRequest) -> bool:
    return "message is not modified" in str(exc).lower()


async def clear_markup(bot, chat_id: int, message_ids: Iterable[int]) -> None:
    """Best-effort снимает inline-клавиатуру со старых сообщений."""
    for message_id in message_ids:
        try:
            await bot.edit_message_reply_markup(
                chat_id=chat_id,
                message_id=message_id,
                reply_markup=None,
            )
        except TelegramBadRequest:
            # Сообщение старше 48 часов, уже без клавиатуры и т.п.
            continue
        except Exception:  # noqa: BLE001 - очистка не должна ломать сценарий
            logger.debug(
                "Не удалось снять клавиатуру с сообщения %s в чате %s",
                message_id,
                chat_id,
                exc_info=True,
            )


async def _clear_old_screens(bot, chat_id: int, keep: int | None = None) -> None:
    ids = registry.ids(chat_id)
    if keep is not None:
        ids.discard(int(keep))
    if ids:
        await clear_markup(bot, chat_id, ids)


def _markup(
    inline_keyboard: InlineKeyboardMarkup | None,
    reply_keyboard: ReplyKeyboardMarkup | None,
) -> Keyboard:
    if inline_keyboard is not None and reply_keyboard is not None:
        logger.warning(
            "На одно сообщение переданы inline- и reply-клавиатура; "
            "использую inline, reply-клавиатуру нужно отправить отдельно"
        )
    if inline_keyboard is not None:
        return inline_keyboard
    return reply_keyboard


async def send_screen(
    message: Message,
    text: str,
    *,
    inline_keyboard: InlineKeyboardMarkup | None = None,
    reply_keyboard: ReplyKeyboardMarkup | None = None,
) -> None:
    """Отправляет новый экран, снимая клавиатуры со старых сообщений."""
    chat_id = message.chat.id
    await _clear_old_screens(message.bot, chat_id)

    chunks = chunk_text(text)
    markup = _markup(inline_keyboard, reply_keyboard)
    sent_ids: list[int] = []

    for index, chunk in enumerate(chunks):
        is_last = index == len(chunks) - 1
        sent = await message.answer(
            chunk,
            reply_markup=markup if is_last else None,
        )
        sent_ids.append(sent.message_id)

    registry.register(chat_id, sent_ids, primary_id=sent_ids[-1] if sent_ids else None)


async def render_screen(
    callback: CallbackQuery,
    text: str,
    *,
    inline_keyboard: InlineKeyboardMarkup | None = None,
    reply_keyboard: ReplyKeyboardMarkup | None = None,
) -> None:
    """Обновляет текущий экран (или отправляет новый, если редактировать нельзя)."""
    if callback.message is None:
        return

    # Reply-клавиатуру нельзя установить редактированием сообщения.
    if reply_keyboard is not None:
        await send_screen(callback.message, text, reply_keyboard=reply_keyboard)
        return

    chat_id = callback.message.chat.id
    current_id = callback.message.message_id
    await _clear_old_screens(callback.bot, chat_id, keep=current_id)

    chunks = chunk_text(text)
    if len(chunks) > 1:
        try:
            await callback.message.edit_text(chunks[0])
        except TelegramBadRequest as exc:
            if not _is_not_modified(exc):
                await send_screen(
                    callback.message,
                    text,
                    inline_keyboard=inline_keyboard,
                )
                return
        except Exception:  # noqa: BLE001
            await send_screen(callback.message, text, inline_keyboard=inline_keyboard)
            return

        sent_ids = [current_id]
        for index, chunk in enumerate(chunks[1:]):
            is_last = index == len(chunks) - 2
            sent = await callback.message.answer(
                chunk,
                reply_markup=inline_keyboard if is_last else None,
            )
            sent_ids.append(sent.message_id)
        registry.register(chat_id, sent_ids, primary_id=sent_ids[-1])
        return

    try:
        await callback.message.edit_text(text, reply_markup=inline_keyboard)
    except TelegramBadRequest as exc:
        if _is_not_modified(exc):
            registry.register(chat_id, [current_id], primary_id=current_id)
            return
        await send_screen(callback.message, text, inline_keyboard=inline_keyboard)
        return
    except Exception:  # noqa: BLE001
        await send_screen(callback.message, text, inline_keyboard=inline_keyboard)
        return

    registry.register(chat_id, [current_id], primary_id=current_id)


async def edit_prompt(
    message: Message,
    text: str,
    *,
    inline_keyboard: InlineKeyboardMarkup | None = None,
) -> None:
    """Обновляет сообщение-подсказку, не создавая новое рядом со старым."""
    chat_id = message.chat.id
    primary_id = registry.primary_id(chat_id)

    if primary_id is None:
        await send_screen(message, text, inline_keyboard=inline_keyboard)
        return

    try:
        await message.bot.edit_message_text(
            chat_id=chat_id,
            message_id=primary_id,
            text=text,
            reply_markup=inline_keyboard,
        )
    except TelegramBadRequest as exc:
        if _is_not_modified(exc):
            return
        await send_screen(message, text, inline_keyboard=inline_keyboard)
        return
    except Exception:  # noqa: BLE001
        await send_screen(message, text, inline_keyboard=inline_keyboard)
        return

    registry.register(chat_id, [primary_id], primary_id=primary_id)


async def show_error(
    target: CallbackQuery | Message,
    text: str,
    *,
    inline_keyboard: InlineKeyboardMarkup | None = None,
) -> None:
    """Единая точка показа ошибки: обновляет текущий экран."""
    if isinstance(target, CallbackQuery):
        await render_screen(target, text, inline_keyboard=inline_keyboard)
    else:
        await send_screen(target, text, inline_keyboard=inline_keyboard)


async def restore_reply_keyboard(
    message: Message,
    reply_keyboard: ReplyKeyboardMarkup,
    text: str = "Используйте кнопки внизу 👇",
) -> None:
    """Возвращает дефолтную reply-клавиатуру, не меняя активный экран.

    Нужна после сценария, который закончился inline-экраном: reply-клавиатуру
    нельзя установить редактированием, поэтому отправляем короткое сообщение.
    """
    try:
        await message.answer(text, reply_markup=reply_keyboard)
    except Exception:  # noqa: BLE001 - клавиатура не критична для сценария
        logger.debug("Не удалось вернуть reply-клавиатуру", exc_info=True)
