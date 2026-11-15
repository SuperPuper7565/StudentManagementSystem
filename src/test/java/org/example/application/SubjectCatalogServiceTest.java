package org.example.application;

import org.example.domain.model.Subject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectCatalogServiceTest {

    private final SubjectCatalogService subjectCatalogService = new SubjectCatalogService();

    @Test
    @DisplayName("Справочник содержит только математику и информатику")
    void all_ReturnsFixedCatalog() {
        assertThat(subjectCatalogService.all())
                .extracting(Subject::getDisplayName)
                .containsExactly("Математика", "Информатика");
    }

    @Test
    @DisplayName("Коды и названия приводятся к каноническим именам без дублей")
    void canonicalNames_NormalizesAndDeduplicates() {
        assertThat(subjectCatalogService.canonicalNames(
                List.of("MATH", "Математика", "INFORMATICS", "информатика")
        )).containsExactly("Математика", "Информатика");
    }

    @Test
    @DisplayName("Неизвестный предмет отклоняется")
    void canonicalNames_RejectsUnknownSubject() {
        assertThatThrownBy(() -> subjectCatalogService.canonicalNames(List.of("Физика")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный предмет");
    }

    @Test
    @DisplayName("Пустой список предметов отклоняется")
    void canonicalNames_RejectsEmptyList() {
        assertThatThrownBy(() -> subjectCatalogService.canonicalNames(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("хотя бы один предмет");
    }
}
