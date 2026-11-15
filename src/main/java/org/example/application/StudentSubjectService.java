package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentSubjectService {

    private final StudentSubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final SubjectCatalogService subjectCatalogService;
    private final TeacherSubjectService teacherSubjectService;

    public List<String> getSubjects(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));
        return getSubjects(student);
    }

    public List<String> getSubjects(Student student) {
        LinkedHashSet<String> subjects = new LinkedHashSet<>();

        for (String stored : subjectRepository.findByStudentId(student.id())) {
            subjectCatalogService.canonicalNameIfKnown(stored).ifPresent(subjects::add);
        }
        subjectCatalogService.canonicalNameIfKnown(student.subject()).ifPresent(subjects::add);

        return subjects.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public void addSubject(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));

        String normalized = subjectCatalogService.canonicalName(subject);
        if (!teacherSubjectService.teaches(normalized)) {
            throw new IllegalArgumentException("Преподаватель не ведёт предмет: " + normalized);
        }
        ensurePrimarySubject(student);

        boolean alreadyExists = subjectRepository.findByStudentId(studentId).stream()
                .map(subjectCatalogService::canonicalNameIfKnown)
                .flatMap(Optional::stream)
                .anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!alreadyExists) {
            subjectRepository.add(studentId, normalized);
        }
    }

    @Transactional
    public void removeSubject(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));

        String normalized = subjectCatalogService.canonicalName(subject);
        String primarySubject = subjectCatalogService.canonicalNameIfKnown(student.subject()).orElse(null);
        if (primarySubject != null && primarySubject.equalsIgnoreCase(normalized)) {
            throw new IllegalStateException("Нельзя удалить основной предмет ученика");
        }

        subjectRepository.remove(studentId, normalized);
    }

    private void ensurePrimarySubject(Student student) {
        String primarySubject = subjectCatalogService.canonicalNameIfKnown(student.subject()).orElse(null);
        if (primarySubject == null) {
            return;
        }

        boolean alreadyStored = subjectRepository.findByStudentId(student.id()).stream()
                .map(subjectCatalogService::canonicalNameIfKnown)
                .flatMap(Optional::stream)
                .anyMatch(existing -> existing.equalsIgnoreCase(primarySubject));
        if (!alreadyStored) {
            subjectRepository.add(student.id(), primarySubject);
        }
    }
}
