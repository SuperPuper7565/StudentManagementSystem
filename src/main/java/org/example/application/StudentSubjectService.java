package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentSubjectService {

    private static final int MAX_SUBJECT_LENGTH = 80;

    private final StudentSubjectRepository subjectRepository;
    private final StudentRepository studentRepository;

    public List<String> getSubjects(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));
        return getSubjects(student);
    }

    public List<String> getSubjects(Student student) {
        List<String> subjects = new ArrayList<>(subjectRepository.findByStudentId(student.id()));
        String primarySubject = normalizeOrNull(student.subject());

        if (primarySubject != null && subjects.stream()
                .noneMatch(subject -> subject.equalsIgnoreCase(primarySubject))) {
            subjects.add(primarySubject);
        }

        return subjects.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public void addSubject(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));

        String normalized = normalize(subject);
        ensurePrimarySubject(student);

        boolean alreadyExists = subjectRepository.findByStudentId(studentId).stream()
                .anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!alreadyExists) {
            subjectRepository.add(studentId, normalized);
        }
    }

    @Transactional
    public void removeSubject(Long studentId, String subject) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));

        String normalized = normalize(subject);
        String primarySubject = normalizeOrNull(student.subject());
        if (primarySubject != null && primarySubject.equalsIgnoreCase(normalized)) {
            throw new IllegalStateException("Нельзя удалить основной предмет ученика");
        }

        subjectRepository.remove(studentId, normalized);
    }

    private void ensurePrimarySubject(Student student) {
        String primarySubject = normalizeOrNull(student.subject());
        if (primarySubject == null) {
            return;
        }

        boolean alreadyStored = subjectRepository.findByStudentId(student.id()).stream()
                .anyMatch(existing -> existing.equalsIgnoreCase(primarySubject));
        if (!alreadyStored) {
            subjectRepository.add(student.id(), primarySubject);
        }
    }

    private String normalize(String subject) {
        String normalized = normalizeOrNull(subject);
        if (normalized == null) {
            throw new IllegalArgumentException("Предмет не может быть пустым");
        }
        if (normalized.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException(
                    "Предмет слишком длинный (максимум " + MAX_SUBJECT_LENGTH + " символов)"
            );
        }
        return normalized;
    }

    private String normalizeOrNull(String subject) {
        if (subject == null) {
            return null;
        }
        String normalized = subject.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
