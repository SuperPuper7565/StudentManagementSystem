package org.example.application;

import org.example.domain.model.Subject;
import org.example.domain.repository.TeacherSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherSubjectServiceTest {

    @Mock
    private TeacherSubjectRepository teacherSubjectRepository;

    private TeacherSubjectService teacherSubjectService;

    @BeforeEach
    void setUp() {
        teacherSubjectService = new TeacherSubjectService(
                teacherSubjectRepository,
                new SubjectCatalogService()
        );
    }

    @Test
    @DisplayName("Если предметы не настроены, преподаватель ведёт весь справочник")
    void getSubjects_DefaultsToWholeCatalog() {
        when(teacherSubjectRepository.findAll()).thenReturn(List.of());

        assertThat(teacherSubjectService.getSubjects())
                .extracting(Subject::getDisplayName)
                .containsExactly("Математика", "Информатика");
        assertThat(teacherSubjectService.teaches("Математика")).isTrue();
        assertThat(teacherSubjectService.teaches("Информатика")).isTrue();
    }

    @Test
    @DisplayName("Выбранные предметы сохраняются в каноническом виде")
    void setSubjects_StoresCanonicalNames() {
        teacherSubjectService.setSubjects(List.of("MATH"));

        verify(teacherSubjectRepository).replaceAll(List.of("Математика"));
    }

    @Test
    @DisplayName("Преподаватель не ведёт предмет, которого нет в его списке")
    void teaches_ReturnsFalseForUnselectedSubject() {
        when(teacherSubjectRepository.findAll()).thenReturn(List.of("Математика"));

        assertThat(teacherSubjectService.teaches("Математика")).isTrue();
        assertThat(teacherSubjectService.teaches("Информатика")).isFalse();
    }

    @Test
    @DisplayName("Нельзя сохранить пустой список предметов")
    void setSubjects_RejectsEmptyList() {
        assertThatThrownBy(() -> teacherSubjectService.setSubjects(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("хотя бы один предмет");
    }
}
