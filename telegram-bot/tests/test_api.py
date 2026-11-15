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
                    "subjects": ["MATH", "INFORMATICS"],
                    "grade": 9,
                    "goal": "Подготовка к ЕГЭ",
                    "lessonFormat": "ONLINE",
                }
            )
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result["id"] == 7
    assert captured["method"] == "POST"
    assert captured["path"] == "/api/v1/students"
    assert captured["payload"]["subjects"] == ["MATH", "INFORMATICS"]
    assert captured["payload"]["goal"] == "Подготовка к ЕГЭ"
    assert "lessonPrice" not in captured["payload"]


def test_get_student_price():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["path"] = request.url.path
        return httpx.Response(
            200,
            json={"basePrice": 2000, "finalPrice": 1500, "details": "Индивидуальная цена"},
        )

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.get_student_price(5)
        finally:
            await api.aclose()

    result = _run(scenario())

    assert captured["method"] == "GET"
    assert captured["path"] == "/api/v1/students/5/price"
    assert result["finalPrice"] == 1500


def test_get_student_subjects():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["path"] = request.url.path
        return httpx.Response(200, json=["Математика", "Информатика"])

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.get_student_subjects(5)
        finally:
            await api.aclose()

    result = _run(scenario())

    assert captured["method"] == "GET"
    assert captured["path"] == "/api/v1/students/5/subjects"
    assert result == ["Математика", "Информатика"]


def test_get_subjects_returns_catalog():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["path"] = request.url.path
        return httpx.Response(
            200,
            json=[
                {"code": "MATH", "name": "Математика"},
                {"code": "INFORMATICS", "name": "Информатика"},
            ],
        )

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.get_subjects()
        finally:
            await api.aclose()

    result = _run(scenario())

    assert captured["method"] == "GET"
    assert captured["path"] == "/api/v1/subjects"
    assert [item["code"] for item in result] == ["MATH", "INFORMATICS"]


def test_set_teacher_subjects_sends_admin_key():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["method"] = request.method
        captured["path"] = request.url.path
        captured["admin_key"] = request.headers.get("X-Admin-Key")
        captured["payload"] = json.loads(request.content)
        return httpx.Response(204)

    async def scenario():
        api = ApiClient(
            "http://backend",
            transport=httpx.MockTransport(handler),
            admin_api_key="secret",
        )
        try:
            return await api.set_teacher_subjects(["MATH"])
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result is None
    assert captured["method"] == "PUT"
    assert captured["path"] == "/api/v1/teacher/subjects"
    assert captured["admin_key"] == "secret"
    assert captured["payload"] == {"subjectCodes": ["MATH"]}


def test_book_lesson_sends_query_params():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["params"] = dict(request.url.params)
        return httpx.Response(200, json={"id": 42, "slot": {"startTime": "2026-10-25T14:00:00"}})

    async def scenario():
        api = ApiClient("http://backend", transport=httpx.MockTransport(handler))
        try:
            return await api.book_lesson(student_id=3, slot_id=9, subject="Математика")
        finally:
            await api.aclose()

    result = _run(scenario())

    assert result["id"] == 42
    assert captured["params"] == {
        "studentId": "3",
        "slotId": "9",
        "subject": "Математика",
    }


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
            await api.book_lesson(1, 2, "Математика")
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
