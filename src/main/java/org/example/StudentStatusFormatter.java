package org.example;

/**
 * Сервис для форматирования статусов студентов в текстовое представление.
 */

public class StudentStatusFormatter {
    public String format(StudentStatus status) {
        return switch (status) {
            case Active active -> "Активен (" + active.lessonFormat() + ", " + active.lessonPrice() + " ₽/урок)";
            case Paused paused -> "На паузе до " + paused.endDate() + " (Причина: " + paused.reason() + ")";
            case Graduated graduated -> "Выпустился " + graduated.graduationDate() + " (Балл: " + graduated.examResult() + ")";
        };
    }
}
