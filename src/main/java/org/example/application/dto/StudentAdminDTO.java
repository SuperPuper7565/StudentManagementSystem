package org.example.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentAdminDTO(
        Long id,
        String fullName,
        String subject,
        List<String> subjects,
        int grade,
        String goalDescription,
        String statusDescription,
        BigDecimal lessonPrice,
        String priceDetails
) {
}
