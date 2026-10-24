"""Состояния FSM."""

from aiogram.fsm.state import State, StatesGroup


class Registration(StatesGroup):
    first_name = State()
    last_name = State()
    subject = State()
    grade = State()
    goal = State()
    price = State()
    lesson_format = State()
    confirm = State()


class LinkStudent(StatesGroup):
    waiting_id = State()


class AdminFlow(StatesGroup):
    slot_time = State()
    attendance_booking_id = State()
