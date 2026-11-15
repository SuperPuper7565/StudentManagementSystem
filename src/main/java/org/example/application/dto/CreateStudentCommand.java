package org.example.application.dto;

import org.example.domain.model.LessonFormat;

import java.util.List;

public record CreateStudentCommand(
        String firstName,
        String lastName,
        List<String> subjects,
        String subject,
        int grade,
        String goal,
        LessonFormat lessonFormat
) {
    /**
     * Новый бот присылает subjects, старый — одно поле subject.
     * Поддерживаем оба варианта на время перехода.
     */
    public List<String> requestedSubjects() {
        if (subjects != null && !subjects.isEmpty()) {
            return subjects;
        }
        if (subject != null && !subject.isBlank()) {
            return List.of(subject);
        }
        return List.of();
    }
}
