package org.example.application;

import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentRegistrationService registrationService;

    @Test
    @DisplayName("Регистрация студента: присвоение статуса Active с ценой и форматом")
    void registerStudent_Success() {
        CreateStudentCommand command = new CreateStudentCommand(
                "Петр", "Петров", "Информатика", 10,
                StudyGoal.EGE, new BigDecimal("2000.00"), LessonFormat.ONLINE
        );

        when(studentRepository.save(any(Student.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Student registered = registrationService.registerStudent(command);

        assertThat(registered).isNotNull();
        assertThat(registered.name()).isEqualTo("Петр");
        assertThat(registered.status()).isInstanceOf(Active.class);

        Active activeStatus = (Active) registered.status();
        assertThat(activeStatus.lessonPrice()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(activeStatus.lessonFormat()).isEqualTo(LessonFormat.ONLINE);

        verify(studentRepository, times(1)).save(any(Student.class));
    }
}