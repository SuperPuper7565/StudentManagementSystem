package org.example;

import java.time.LocalDate;

public record Paused(
        String reason,
        LocalDate startDate,
        LocalDate endDate
) implements StudentStatus {
}
