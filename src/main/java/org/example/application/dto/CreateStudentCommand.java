package org.example.application.dto;

import org.example.domain.model.LessonFormat;
import org.example.domain.model.StudyGoal;

import java.math.BigDecimal;

public record CreateStudentCommand(
        String firstName,
        String lastName,
        String subject,
        int grade,
        StudyGoal goal,
        BigDecimal lessonPrice,
        LessonFormat lessonFormat
) {}