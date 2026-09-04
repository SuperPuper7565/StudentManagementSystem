package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StudentStatusFormatterTest {
    private StudentStatusFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new StudentStatusFormatter();
    }

    @Test
    @DisplayName("Форматирование активного статуса")
    void shouldFormatActiveStatus() {
        StudentStatus status = new Active(new BigDecimal("1500.00"), LessonFormat.ONLINE);

        String result = formatter.format(status);

        assertEquals("Активен (ONLINE, 1500.00 ₽/урок)", result);
    }

    @Test
    @DisplayName("Форматирование статуса на паузе")
    void shouldFormatPausedStatus() {
        LocalDate endDate = LocalDate.of(2026, 10, 1);
        StudentStatus status = new Paused("Каникулы", LocalDate.of(2026, 9, 1), endDate);

        String result = formatter.format(status);

        assertEquals("На паузе до 2026-10-01 (Причина: Каникулы)", result);
    }

    @Test
    @DisplayName("Форматирование статуса выпускника")
    void shouldFormatGraduatedStatus() {
        StudentStatus status = new Graduated(LocalDate.of(2025, 6, 25), 98);

        String result = formatter.format(status);

        assertEquals("Выпустился 2025-06-25 (Балл: 98)", result);
    }
}
