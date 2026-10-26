package org.example.application;

import org.example.application.dto.PriceQuote;
import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.example.domain.status.Graduated;
import org.example.domain.status.Paused;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAdminServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentSubjectService subjectService;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private StudentAdminService adminService;

    @Test
    @DisplayName("Получение списка студентов для админки с ценой из PricingService")
    void getAllStudentsForAdmin_Success() {
        Student student1 = Student.builder()
                .id(1L).name("Алексей").surname("Смирнов").subject("Физика").grade(11)
                .goal(StudyGoal.EGE)
                .status(new Active(LessonFormat.OFFLINE))
                .build();

        Student student2 = Student.builder()
                .id(2L).name("Елена").surname("Попова").subject("Математика").grade(9)
                .goal(StudyGoal.OTHER)
                .status(new Paused("Каникулы", LocalDate.now(), LocalDate.now().plusDays(14)))
                .build();

        when(studentRepository.findAll()).thenReturn(List.of(student1, student2));
        when(subjectService.getSubjects(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            return List.of(student.subject());
        });
        when(pricingService.quote(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            BigDecimal price = student.id() == 1L
                    ? new BigDecimal("1800.00")
                    : new BigDecimal("2000.00");
            return new PriceQuote(
                    new BigDecimal("2000.00"),
                    price,
                    false,
                    List.of("BASE"),
                    "Тестовая цена"
            );
        });

        List<StudentAdminDTO> result = adminService.getAllStudentsForAdmin();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).statusDescription()).contains("Активен", "OFFLINE");
        assertThat(result.get(0).subjects()).containsExactly("Физика");
        assertThat(result.get(0).lessonPrice()).isEqualByComparingTo("1800.00");
        assertThat(result.get(0).priceDetails()).isEqualTo("Тестовая цена");
        assertThat(result.get(1).statusDescription()).contains("На паузе", "Каникулы");
    }

    @Test
    @DisplayName("Обновление статуса: выбрасывает исключение, если студент не найден")
    void updateStudentStatus_ThrowsException_WhenNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateStudentStatus(99L, new Graduated(LocalDate.now(), 95)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Студент не найден");

        verify(studentRepository, never()).updateStatus(any(), any());
    }
}
