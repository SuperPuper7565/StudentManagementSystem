"""HTTP-клиент для Student Management System."""

from __future__ import annotations

import logging
from datetime import datetime
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class ApiError(RuntimeError):
    """Бэкенд ответил ошибкой (4xx/5xx)."""

    def __init__(self, message: str, status_code: int | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code


class ApiUnavailableError(RuntimeError):
    """Бэкенд недоступен (сеть, таймаут, некорректный URL)."""


class ApiClient:
    """Тонкая обёртка над REST API сервиса.

    Все методы возвращают уже разобранный JSON (dict/list) или None для 204.
    """

    def __init__(
        self,
        base_url: str,
        timeout: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
            transport=transport,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    # ------------------------------------------------------------------ #
    # Студенты
    # ------------------------------------------------------------------ #
    async def register_student(self, payload: dict[str, Any]) -> dict[str, Any]:
        return await self._request("POST", "/api/v1/students", json=payload)

    async def list_students(self) -> list[dict[str, Any]]:
        data = await self._request("GET", "/api/v1/students/admin-list")
        return data if isinstance(data, list) else []

    async def get_student_price(self, student_id: int) -> dict[str, Any]:
        data = await self._request("GET", f"/api/v1/students/{student_id}/price")
        return data if isinstance(data, dict) else {}

    async def find_student(self, student_id: int) -> dict[str, Any] | None:
        for student in await self.list_students():
            try:
                if int(student.get("id", -1)) == student_id:
                    return student
            except (TypeError, ValueError):
                continue
        return None

    # ------------------------------------------------------------------ #
    # Расписание
    # ------------------------------------------------------------------ #
    async def create_slot(self, start_time: datetime) -> dict[str, Any]:
        return await self._request(
            "POST",
            "/api/v1/schedule/slots",
            params={"startTime": start_time.isoformat(timespec="seconds")},
        )

    async def get_available_slots(self) -> list[dict[str, Any]]:
        data = await self._request("GET", "/api/v1/schedule/slots/available")
        return data if isinstance(data, list) else []

    # ------------------------------------------------------------------ #
    # Бронирования
    # ------------------------------------------------------------------ #
    async def book_lesson(self, student_id: int, slot_id: int) -> dict[str, Any]:
        return await self._request(
            "POST",
            "/api/v1/bookings",
            params={"studentId": student_id, "slotId": slot_id},
        )

    async def cancel_booking(self, booking_id: int) -> None:
        await self._request("POST", f"/api/v1/bookings/{booking_id}/cancel")

    async def mark_attendance(self, booking_id: int, attended: bool) -> None:
        await self._request(
            "POST",
            f"/api/v1/bookings/{booking_id}/attendance",
            params={"attended": str(attended).lower()},
        )

    # ------------------------------------------------------------------ #
    # Транспорт
    # ------------------------------------------------------------------ #
    async def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        try:
            response = await self._client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            logger.warning("Ошибка запроса %s %s: %s", method, path, exc)
            raise ApiUnavailableError(
                "Сервис временно недоступен. Проверьте, запущен ли бэкенд, и попробуйте ещё раз."
            ) from exc

        if response.status_code >= 400:
            message = self._extract_error_message(response)
            logger.info(
                "Бэкенд вернул %s на %s %s: %s",
                response.status_code,
                method,
                path,
                message,
            )
            raise ApiError(message, response.status_code)

        if response.status_code == 204 or not response.content:
            return None

        try:
            return response.json()
        except ValueError as exc:
            raise ApiError(
                "Сервис вернул некорректный ответ. Попробуйте позже.",
                response.status_code,
            ) from exc

    @staticmethod
    def _extract_error_message(response: httpx.Response) -> str:
        try:
            payload = response.json()
        except ValueError:
            payload = None

        if isinstance(payload, dict):
            for key in ("message", "error", "detail"):
                value = payload.get(key)
                if isinstance(value, str) and value.strip():
                    return value.strip()

        text = response.text.strip()
        if text:
            return text[:500]

        defaults = {
            400: "Некорректный запрос.",
            404: "Запись не найдена.",
            409: "Действие невозможно в текущем состоянии.",
            500: "Внутренняя ошибка сервиса.",
        }
        return defaults.get(response.status_code, f"Ошибка сервиса (HTTP {response.status_code}).")
