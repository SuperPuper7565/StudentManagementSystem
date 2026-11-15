package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.example.domain.status.Active;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentRegistrationService {

    private static final int MAX_GOAL_LENGTH = 200;

    private final StudentRepository studentRepository;
    private final StudentSubjectRepository subjectRepository;
    private final SubjectCatalogService subjectCatalogService;
    private final TeacherSubjectService teacherSubjectService;

    @Transactional
    public Student registerStudent(CreateStudentCommand command) {
        validateGrade(command.grade());

        List<String> subjects = subjectCatalogService.canonicalNames(command.requestedSubjects());
        for (String subject : subjects) {
            if (!teacherSubjectService.teaches(subject)) {
                throw new IllegalArgumentException("Преподаватель не ведёт предмет: " + subject);
            }
        }

        Student student = Student.builder()
                .name(command.firstName())
                .surname(command.lastName())
                .subject(subjects.getFirst())
                .grade(command.grade())
                .goal(normalizeGoal(command.goal()))
                .status(new Active(command.lessonFormat()))
                .build();

        Student saved = studentRepository.save(student);
        for (String subject : subjects) {
            subjectRepository.add(saved.id(), subject);
        }
        return saved;
    }

    private void validateGrade(int grade) {
        if (grade < 1 || grade > 11) {
            throw new IllegalArgumentException("Класс должен быть числом от 1 до 11");
        }
    }

    private String normalizeGoal(String goal) {
        if (goal == null) {
            return null;
        }
        String normalized = goal.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_GOAL_LENGTH) {
            throw new IllegalArgumentException(
                    "Цель обучения слишком длинная (максимум " + MAX_GOAL_LENGTH + " символов)"
            );
        }
        return normalized;
    }
}
