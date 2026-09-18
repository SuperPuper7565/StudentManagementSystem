package org.example.application;

/**
 * Сервис для форматирования статусов студентов в текстовое представление.
 */

import org.example.domain.status.Active;
import org.example.domain.status.Graduated;
import org.example.domain.status.Paused;
import org.example.domain.status.StudentStatus;

public class StudentStatusFormatter {
    public String format(StudentStatus status) {
        return switch (status) {
            case Active active -> "Активен (" + active.lessonFormat() + ", " + active.lessonPrice() + " ₽/урок)";
            case Paused paused -> "На паузе до " + paused.endDate() + " (Причина: " + paused.reason() + ")";
            case Graduated graduated -> "Выпустился " + graduated.graduationDate() + " (Балл: " + graduated.examResult() + ")";
        };
    }
}
