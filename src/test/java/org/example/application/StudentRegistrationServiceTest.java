package org.example.application;

import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.example.domain.status.Active;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRegistrationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentSubjectRepository subjectRepository;

    @Mock
    private TeacherSubjectService teacherSubjectService;

    private final SubjectCatalogService subjectCatalogService = new SubjectCatalogService();

    private StudentRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new StudentRegistrationService(
                studentRepository,
                subjectRepository,
                subjectCatalogService,
                teacherSubjectService
        );
        lenient().when(teacherSubjectService.teaches(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Регистрация студента с несколькими предметами и текстовой целью")
    void registerStudent_Success() {
        CreateStudentCommand command = new CreateStudentCommand(
                "Петр",
                "Петров",
                List.of("MATH", "INFORMATICS"),
                null,
                10,
                "Подготовка к ЕГЭ",
                LessonFormat.ONLINE
        );

        when(studentRepository.save(any(Student.class)))
                .thenReturn(Student.builder()
                        .id(1L)
                        .name("Петр")
                        .surname("Петров")
                        .subject("Математика")
                        .grade(10)
                        .goal("Подготовка к ЕГЭ")
                        .status(new Active(LessonFormat.ONLINE))
                        .build());

        Student registered = registrationService.registerStudent(command);

        assertThat(registered).isNotNull();
        assertThat(registered.name()).isEqualTo("Петр");
        assertThat(registered.goal()).isEqualTo("Подготовка к ЕГЭ");
        assertThat(registered.status()).isInstanceOf(Active.class);

        Active activeStatus = (Active) registered.status();
        assertThat(activeStatus.lessonFormat()).isEqualTo(LessonFormat.ONLINE);

        verify(studentRepository, times(1)).save(any(Student.class));
        verify(subjectRepository, times(1)).add(1L, "Математика");
        verify(subjectRepository, times(1)).add(1L, "Информатика");
    }

    @Test
    @DisplayName("Цель обучения необязательна: пустая строка сохраняется как null")
    void registerStudent_BlankGoalBecomesNull() {
        CreateStudentCommand command = new CreateStudentCommand(
                "Петр",
                "Петров",
                List.of("MATH"),
                null,
                10,
                "   ",
                LessonFormat.ONLINE
        );

        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            return Student.builder()
                    .id(1L)
                    .name(student.name())
                    .surname(student.surname())
                    .subject(student.subject())
                    .grade(student.grade())
                    .goal(student.goal())
                    .status(student.status())
                    .build();
        });

        registrationService.registerStudent(command);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().goal()).isNull();
    }

    @Test
    @DisplayName("Нельзя зарегистрировать предмет вне фиксированного справочника")
    void registerStudent_RejectsUnknownSubject() {
        CreateStudentCommand command = new CreateStudentCommand(
                "Петр",
                "Петров",
                List.of("Физика"),
                null,
                10,
                null,
                LessonFormat.ONLINE
        );

        assertThatThrownBy(() -> registrationService.registerStudent(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный предмет");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("Нельзя зарегистрировать предмет, который не ведёт преподаватель")
    void registerStudent_RejectsSubjectNotTaughtByTeacher() {
        when(teacherSubjectService.teaches("Математика")).thenReturn(false);

        CreateStudentCommand command = new CreateStudentCommand(
                "Петр",
                "Петров",
                List.of("MATH"),
                null,
                10,
                null,
                LessonFormat.ONLINE
        );

        assertThatThrownBy(() -> registrationService.registerStudent(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не ведёт предмет");

        verify(studentRepository, never()).save(any(Student.class));
    }
}
