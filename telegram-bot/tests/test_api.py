import asyncio
import json

import httpx
import pytest

from tgbot.api import ApiClient, ApiError, ApiUnavailableError


def _run(coro):
    return asyncio.run(coro)


def test_register_student_posts_payload():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["path"] = request.url.path
        captured["payload"] = json.loads(request.content)
        return httpx.Response(201, json={"id": 7, "name": "Иван"})

    async def scenario():
        api = ApiClient("http://backend:8080", transport=httpx.MockTransport(handler))
        try:
            return await api.register_student(
                {
                    "firstName": "Иван",
                    "lastName": "Петров",
                    "subject": "Математика",
                    "grade": 9,
                    "goal": "OGE",
                    "lessonPrice": "1500.00",
                    "lessonFormat": "ONLINE",
                }
            )
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result["id"] == 7
    assert captured["method"] == "POST"
    assert captured["path"] == "/api/v1/students"
    assert captured["payload"]["goal"] == "OGE"
    assert captured["payload"]["lessonPrice"] == "1500.00"


def test_book_lesson_sends_query_params():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["params"] = dict(request.url.params)
        return httpx.Response(200, json={"id": 42, "slot": {"startTime": "2026-10-25T14:00:00"}})

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.book_lesson(student_id=3, slot_id=9)
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result["id"] == 42
    assert captured["params"] == {"studentId": "3", "slotId": "9"}


def test_mark_attendance_sends_boolean():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["params"] = dict(request.url.params)
        return httpx.Response(204)

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.mark_attendance(5, True)
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result is None
    assert captured["params"] == {"attended": "true"}


def test_error_message_is_extracted():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            409,
            json={"status": 409, "error": "Conflict", "message": "Слот уже забронирован"},
        )

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            await api.book_lesson(1, 2)
        finally:
            await api.aclose()

    with pytest.raises(ApiError) as exc_info:
        _run(scenario())

    assert exc_info.value.status_code == 409
    assert exc_info.value.message == "Слот уже забронирован"


def test_network_error_becomes_unavailable():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            await api.get_available_slots()
        finally:
            await api.aclose()

    with pytest.raises(ApiUnavailableError):
        _run(scenario())
