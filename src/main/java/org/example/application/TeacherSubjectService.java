package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Subject;
import org.example.domain.repository.TeacherSubjectRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Предметы, которые ведёт преподаватель.
 *
 * <p>Пока в системе один преподаватель, поэтому отдельная сущность Teacher не нужна.
 * Если таблица пуста, считаем, что преподаватель ведёт весь справочник — это удобно
 * для нового окружения и тестов.</p>
 */
@Service
@RequiredArgsConstructor
public class TeacherSubjectService {

    private final TeacherSubjectRepository teacherSubjectRepository;
    private final SubjectCatalogService subjectCatalogService;

    public List<Subject> getSubjects() {
        List<Subject> stored = teacherSubjectRepository.findAll().stream()
                .map(Subject::tryParse)
                .flatMap(Optional::stream)
                .distinct()
                .sorted(Comparator.comparingInt(Subject::ordinal))
                .toList();
        return stored.isEmpty() ? subjectCatalogService.all() : stored;
    }

    public void setSubjects(Collection<String> rawSubjects) {
        List<String> canonical = subjectCatalogService.canonicalNames(rawSubjects);
        teacherSubjectRepository.replaceAll(canonical);
    }

    public boolean teaches(String subject) {
        String canonical = subjectCatalogService.canonicalNameIfKnown(subject).orElse(null);
        if (canonical == null) {
            return false;
        }
        return getSubjects().stream()
                .anyMatch(value -> value.getDisplayName().equalsIgnoreCase(canonical));
    }
}
