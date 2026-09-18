package org.example.domain.status;

import java.time.LocalDate;

public record Graduated(
        LocalDate graduationDate,
        int examResult
) implements StudentStatus {
}
