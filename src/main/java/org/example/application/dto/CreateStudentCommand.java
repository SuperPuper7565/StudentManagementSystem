package org.example.application.dto;

import org.example.domain.model.LessonFormat;
import org.example.domain.model.StudyGoal;

public record CreateStudentCommand(
        String firstName,
        String lastName,
        String subject,
        int grade,
        StudyGoal goal,
        LessonFormat lessonFormat
) {
}
