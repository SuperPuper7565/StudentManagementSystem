package org.example.application.dto;

import java.math.BigDecimal;

public record StudentAdminDTO(
        Long id,
        String fullName,
        String subject,
        int grade,
        String goalDescription,
        String statusDescription,
        BigDecimal lessonPrice,
        String priceDetails
) {
}
