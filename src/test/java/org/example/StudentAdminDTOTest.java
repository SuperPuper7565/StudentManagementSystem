package org.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudentAdminDTOTest {

    @Test
    @DisplayName("Проверка форматированного вывода toString()")
    void shouldFormatToStringCorrectly() {
        StudentAdminDTO dto = new StudentAdminDTO(
                1L,
                "Иван Иванов",
                "Математика",
                11,
                "Подготовка к ЕГЭ",
                "Активен (ONLINE, 1500.00 ₽/урок)"
        );

        String result = dto.toString();

        assertNotNull(result);
        assertEquals("[1] Иван Иванов     | 11 класс | Математика   (Подготовка к ЕГЭ) | Активен (ONLINE, 1500.00 ₽/урок)", result);
    }
}