package org.example.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationService {

    public void notifyBookingCreated(String studentName, LocalDateTime lessonTime) {
        log.info("УВЕДОМЛЕНИЕ: Ученик {} успешно записан на урок {}", studentName, lessonTime);
    }

    public void notifyBookingCancelled(String studentName, LocalDateTime lessonTime) {
        log.info("УВЕДОМЛЕНИЕ: Запись ученика {} на урок {} была отменена", studentName, lessonTime);
    }
}