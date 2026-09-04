package org.example;

public record StudentAdminDTO(
        long id,
        String fullName,
        String subject,
        int grade,
        String goal,
        String statusDescription
) {
    @Override
    public String toString() {
        return String.format("[%d] %-15s | %d класс | %-12s (%s) | %s",
                id, fullName, grade, subject, goal, statusDescription);
    }
}
