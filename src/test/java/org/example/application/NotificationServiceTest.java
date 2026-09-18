package org.example.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    @Test
    @DisplayName("Уведомления отправляются без исключений")
    void notify_DoesNotThrow() {
        LocalDateTime now = LocalDateTime.now();
        assertDoesNotThrow(() -> notificationService.notifyBookingCreated("Дмитрий", now));
        assertDoesNotThrow(() -> notificationService.notifyBookingCancelled("Дмитрий", now));
    }
}