package org.example.application;

import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.example.domain.status.Active;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentSubjectRepository subjectRepository;

    @InjectMocks
    private StudentRegistrationService registrationService;

    @Test
    @DisplayName("Регистрация студента: цена не принимается от клиента, статус Active содержит только формат")
    void registerStudent_Success() {
        CreateStudentCommand command = new CreateStudentCommand(
                "Петр", "Петров", "Информатика", 10,
                StudyGoal.EGE, LessonFormat.ONLINE
        );

        when(studentRepository.save(any(Student.class)))
                .thenReturn(Student.builder()
                        .id(1L)
                        .name("Петр")
                        .surname("Петров")
                        .subject("Информатика")
                        .grade(10)
                        .goal(StudyGoal.EGE)
                        .status(new Active(LessonFormat.ONLINE))
                        .build());

        Student registered = registrationService.registerStudent(command);

        assertThat(registered).isNotNull();
        assertThat(registered.name()).isEqualTo("Петр");
        assertThat(registered.status()).isInstanceOf(Active.class);

        Active activeStatus = (Active) registered.status();
        assertThat(activeStatus.lessonFormat()).isEqualTo(LessonFormat.ONLINE);

        verify(studentRepository, times(1)).save(any(Student.class));
        verify(subjectRepository, times(1)).add(1L, "Информатика");
    }
}
