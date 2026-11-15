package org.example.application;

import org.example.domain.model.Subject;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Работа с фиксированным справочником предметов.
 */
@Service
public class SubjectCatalogService {

    public List<Subject> all() {
        return List.of(Subject.values());
    }

    public Subject parse(String raw) {
        return Subject.parse(raw);
    }

    public String canonicalName(String raw) {
        return Subject.parse(raw).getDisplayName();
    }

    public Optional<String> canonicalNameIfKnown(String raw) {
        return Subject.tryParse(raw).map(Subject::getDisplayName);
    }

    /**
     * Приводит список кодов/названий к каноническим отображаемым именам.
     * Пустой список и неизвестные предметы отклоняются.
     */
    public List<String> canonicalNames(Collection<String> rawSubjects) {
        if (rawSubjects == null) {
            throw new IllegalArgumentException("Выберите хотя бы один предмет");
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : rawSubjects) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            names.add(canonicalName(raw));
        }

        if (names.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один предмет");
        }
        return List.copyOf(names);
    }
}
