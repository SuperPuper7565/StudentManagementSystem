package org.example.domain.status;

public sealed interface StudentStatus
        permits Active, Paused, Graduated {
}
