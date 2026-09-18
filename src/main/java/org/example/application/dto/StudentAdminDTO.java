package org.example.application.dto;

public record StudentAdminDTO(
        Long id,
        String fullName,
        String subject,
        int grade,
        String goalDescription,
        String statusDescription
) {}