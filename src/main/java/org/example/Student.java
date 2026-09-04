package org.example;

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
