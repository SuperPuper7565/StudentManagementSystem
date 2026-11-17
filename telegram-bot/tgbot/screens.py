"""Реестр активных экранов чата.

Telegram хранит inline-клавиатуру на сообщении до тех пор, пока её не снимут
или не отредактируют. Бот ведёт «один активный экран» на чат: при показе нового
экрана клавиатуры со старых сообщений снимаются, а stale-guard middleware
отклоняет нажатия внутренних кнопок со старых экранов.

Реестр хранится в памяти процесса. После перезапуска бота он пуст, и это
намеренно: пустой реестр означает «разрешаем всё», иначе у пользователей
перестали бы работать все кнопки до первого рендера.
"""

from __future__ import annotations

from collections.abc import Iterable


class ScreenRegistry:
    def __init__(self) -> None:
        self._message_ids: dict[int, set[int]] = {}
        self._primary_id: dict[int, int] = {}

    def register(
        self,
        chat_id: int,
        message_ids: Iterable[int],
        primary_id: int | None = None,
    ) -> None:
        ids = {int(message_id) for message_id in message_ids}
        self._message_ids[chat_id] = ids
        if primary_id is not None:
            self._primary_id[chat_id] = int(primary_id)
        elif ids:
            self._primary_id[chat_id] = max(ids)
        else:
            self._primary_id.pop(chat_id, None)

    def ids(self, chat_id: int) -> set[int]:
        return set(self._message_ids.get(chat_id, set()))

    def primary_id(self, chat_id: int) -> int | None:
        return self._primary_id.get(chat_id)

    def is_active(self, chat_id: int, message_id: int) -> bool:
        ids = self._message_ids.get(chat_id)
        if not ids:
            # Мягкая деградация: после рестарта реестр пуст.
            return True
        return int(message_id) in ids

    def forget(self, chat_id: int) -> None:
        self._message_ids.pop(chat_id, None)
        self._primary_id.pop(chat_id, None)

    def reset(self) -> None:
        self._message_ids.clear()
        self._primary_id.clear()


registry = ScreenRegistry()
