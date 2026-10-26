package org.example.application;

import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentSubjectServiceTest {

    @Mock
    private StudentSubjectRepository subjectRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentSubjectService subjectService;

    private Student student;

    @BeforeEach
    void setUp() {
        student = Student.builder()
                .id(1L)
                .name("Иван")
                .surname("Иванов")
                .subject("Математика")
                .build();
    }

    @Test
    @DisplayName("Основной предмет возвращается, даже если таблица предметов пуста")
    void getSubjects_ReturnsPrimarySubject() {
        when(subjectRepository.findByStudentId(1L)).thenReturn(List.of());

        assertThat(subjectService.getSubjects(student)).containsExactly("Математика");
    }

    @Test
    @DisplayName("Основной предмет объединяется с дополнительными без дублей")
    void getSubjects_MergesPrimaryAndStoredSubjects() {
        when(subjectRepository.findByStudentId(1L)).thenReturn(List.of("Физика"));

        assertThat(subjectService.getSubjects(student))
                .containsExactlyInAnyOrder("Математика", "Физика");
    }

    @Test
    @DisplayName("При добавлении второго предмета основной сохраняется в таблицу")
    void addSubject_StoresPrimaryAndNewSubject() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findByStudentId(1L))
                .thenReturn(List.of(), List.of("Математика"));

        subjectService.addSubject(1L, "  Физика  ");

        verify(subjectRepository).add(1L, "Математика");
        verify(subjectRepository).add(1L, "Физика");
    }

    @Test
    @DisplayName("Повторное добавление предмета без учёта регистра не создаёт дубль")
    void addSubject_DoesNotDuplicateExistingSubject() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(subjectRepository.findByStudentId(1L)).thenReturn(List.of("Математика"));

        subjectService.addSubject(1L, "математика");

        verify(subjectRepository, never()).add(anyLong(), anyString());
    }

    @Test
    @DisplayName("Пустой предмет добавить нельзя")
    void addSubject_RejectsBlankSubject() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> subjectService.addSubject(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");

        verify(subjectRepository, never()).add(anyLong(), anyString());
    }

    @Test
    @DisplayName("Основной предмет ученика удалить нельзя")
    void removeSubject_RejectsPrimarySubject() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> subjectService.removeSubject(1L, "математика"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("основной предмет");

        verify(subjectRepository, never()).remove(anyLong(), anyString());
    }

    @Test
    @DisplayName("Дополнительный предмет удаляется")
    void removeSubject_RemovesSecondarySubject() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        subjectService.removeSubject(1L, "Физика");

        verify(subjectRepository).remove(1L, "Физика");
    }

    @Test
    @DisplayName("Предметы несуществующего ученика получить нельзя")
    void getSubjects_RejectsUnknownStudent() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubjects(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Студент не найден");
    }
}
