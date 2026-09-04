package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Тестирование StudentServiceImpl")
class StudentServiceImplTest {

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        StudentStatusFormatter formatter = new StudentStatusFormatter();
        studentService = new StudentServiceImpl(formatter);
    }

    @Nested
    @DisplayName("Основные сценарии использования (Happy Path)")
    class HappyPathTests {

        @Test
        @DisplayName("Корректное преобразование студента в DTO")
        void shouldPrepareStudentsForAdminPanelSuccessfully() {
            List<Student> students = List.of(
                    new Student(1L, "Иван", "Иванов", "Математика", 11,
                            StudyGoal.EGE,
                            new Active(new BigDecimal("1500.00"), LessonFormat.ONLINE))
            );

            List<StudentAdminDTO> result = studentService.prepareForAdminPanel(students);

            assertEquals(1, result.size());
            StudentAdminDTO dto = result.get(0);
            assertEquals(1L, dto.id());
            assertEquals("Иван Иванов", dto.fullName());
            assertEquals("Подготовка к ЕГЭ", dto.goal());
        }
    }

    @Nested
    @DisplayName("Граничные случаи и краевые значения")
    class EdgeCasesTests {

        @Test
        @DisplayName("Возврат пустого списка при пустом входном списке")
        void shouldReturnEmptyListWhenInputIsEmpty() {
            List<StudentAdminDTO> result = studentService.prepareForAdminPanel(Collections.emptyList());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Обработка крайних значений баллов и классов")
        void shouldHandleBoundaryValuesCorrectly() {
            Student student = new Student(
                    999L, "Алексей", "Петров", "Физика", 1,
                    StudyGoal.OTHER,
                    new Graduated(LocalDate.of(2026, 1, 1), 0)
            );

            List<StudentAdminDTO> result = studentService.prepareForAdminPanel(List.of(student));

            assertEquals(1, result.get(0).grade());
            assertTrue(result.get(0).statusDescription().contains("Балл: 0"));
        }
    }
}