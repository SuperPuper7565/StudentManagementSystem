"""Точка входа: python -m tgbot."""

from __future__ import annotations

import asyncio
import logging
import sys

from aiogram import Bot, Dispatcher
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramNetworkError, TelegramUnauthorizedError
from aiogram.fsm.storage.memory import MemoryStorage
from aiogram.types import BotCommand, ErrorEvent

from .api import ApiClient
from .config import ConfigError, Settings
from .handlers import admin, booking, common, fallback, registration
from .storage import Database

logger = logging.getLogger(__name__)


async def _set_commands(bot: Bot) -> None:
    await bot.set_my_commands(
        [
            BotCommand(command="start", description="Главное меню"),
            BotCommand(command="slots", description="Свободные слоты"),
            BotCommand(command="bookings", description="Мои записи"),
            BotCommand(command="profile", description="Мой профиль"),
            BotCommand(command="register", description="Создать профиль ученика"),
            BotCommand(command="link", description="Привязать ID ученика"),
            BotCommand(command="help", description="Помощь"),
            BotCommand(command="admin", description="Панель преподавателя"),
            BotCommand(command="students", description="Список учеников (преподаватель)"),
        ]
    )


async def _on_error(event: ErrorEvent) -> bool:
    logger.error("Необработанная ошибка", exc_info=event.exception)
    text = "⚠️ Что-то пошло не так. Попробуйте ещё раз или откройте /start."

    update = event.update
    try:
        if update.callback_query is not None:
            await update.callback_query.answer(text, show_alert=True)
        elif update.message is not None:
            await update.message.answer(text)
    except Exception:  # noqa: BLE001 - уведомление не должно ронять обработчик
        logger.debug("Не удалось сообщить пользователю об ошибке", exc_info=True)
    return True


async def main() -> None:
    try:
        settings = Settings.load()
    except ConfigError as exc:
        print(f"Ошибка конфигурации: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc

    logging.basicConfig(
        level=getattr(logging, settings.log_level, logging.INFO),
        format="%(asctime)s %(levelname)-8s %(name)s: %(message)s",
    )
    logging.getLogger("aiogram.event").setLevel(logging.WARNING)

    logger.info("Запуск бота. Бэкенд: %s", settings.api_base_url)

    db = Database(settings.database_path)
    await db.init()

    api = ApiClient(
        settings.api_base_url,
        timeout=settings.request_timeout,
        admin_api_key=settings.admin_api_key,
    )
    bot = Bot(
        token=settings.bot_token,
        default=DefaultBotProperties(parse_mode=ParseMode.HTML),
    )
    dispatcher = Dispatcher(
        storage=MemoryStorage(),
        settings=settings,
        api=api,
        db=db,
    )

    dispatcher.include_router(common.router)
    dispatcher.include_router(registration.router)
    dispatcher.include_router(booking.router)
    dispatcher.include_router(admin.build_router(settings))
    dispatcher.include_router(fallback.router)
    dispatcher.errors.register(_on_error)

    try:
        await _set_commands(bot)
        await dispatcher.start_polling(
            bot,
            allowed_updates=dispatcher.resolve_used_update_types(),
        )
    except TelegramUnauthorizedError:
        logger.error("Telegram отклонил BOT_TOKEN. Проверьте значение в .env.")
    except TelegramNetworkError as exc:
        logger.error("Нет связи с Telegram API: %s", exc)
    finally:
        await api.aclose()
        await bot.session.close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Бот остановлен.")
