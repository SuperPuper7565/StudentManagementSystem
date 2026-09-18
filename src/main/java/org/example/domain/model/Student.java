package org.example.domain.model;

import lombok.Builder;
import org.example.domain.status.StudentStatus;

@Builder
public record Student(
        long id,
        String name,
        String surname,
        String subject,
        int grade,
        StudyGoal goal,
        StudentStatus status
) {
}
