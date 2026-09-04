package org.example;

public sealed interface StudentStatus
        permits Active, Paused, Graduated {
}
