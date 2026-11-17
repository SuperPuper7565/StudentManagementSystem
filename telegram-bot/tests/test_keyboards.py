from tgbot.keyboards import (
    ADMIN_REPLY_BUTTONS,
    REPLY_ATTENDANCE,
    REPLY_BOOKINGS,
    REPLY_CANCEL,
    REPLY_CREATE_SLOT,
    REPLY_HELP,
    REPLY_PROFILE,
    REPLY_SLOTS,
    STUDENT_REPLY_BUTTONS,
    flow_reply_menu,
    main_reply_menu,
    notification_menu,
)


def _texts(markup):
    return [button.text for row in markup.keyboard for button in row]


def test_main_reply_menu_for_student():
    markup = main_reply_menu(False)
    texts = _texts(markup)

    assert REPLY_SLOTS in texts
    assert REPLY_BOOKINGS in texts
    assert REPLY_PROFILE in texts
    assert REPLY_HELP in texts
    assert REPLY_CREATE_SLOT not in texts
    assert set(texts) == set(STUDENT_REPLY_BUTTONS)
    assert markup.is_persistent is True
    assert markup.resize_keyboard is True


def test_main_reply_menu_for_admin_contains_admin_sections():
    texts = _texts(main_reply_menu(True))

    for label in ADMIN_REPLY_BUTTONS:
        assert label in texts
    for label in STUDENT_REPLY_BUTTONS:
        assert label in texts


def test_flow_reply_menu_has_only_cancel():
    texts = _texts(flow_reply_menu())

    assert texts == [REPLY_CANCEL]
    assert REPLY_SLOTS not in texts
    assert REPLY_ATTENDANCE not in texts


def test_notification_menu_without_booking_id():
    markup = notification_menu()
    callbacks = [
        button.callback_data for row in markup.inline_keyboard for button in row
    ]

    assert not any(data.startswith("notif:booking:") for data in callbacks)
    assert "notif:attendance" in callbacks
    assert "notif:menu" in callbacks


def test_notification_menu_with_booking_id_links_booking():
    markup = notification_menu(42)
    callbacks = [
        button.callback_data for row in markup.inline_keyboard for button in row
    ]
    texts = [button.text for row in markup.inline_keyboard for button in row]

    assert "notif:booking:42" in callbacks
    assert "📋 Запись #42" in texts


def test_callback_data_fits_telegram_limit():
    markup = notification_menu(999_999_999_999)
    callbacks = [
        button.callback_data for row in markup.inline_keyboard for button in row
    ]

    assert callbacks
    assert all(len(data.encode("utf-8")) <= 64 for data in callbacks if data)

