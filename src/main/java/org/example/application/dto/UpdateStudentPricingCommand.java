package org.example.application.dto;

import java.math.BigDecimal;

public record UpdateStudentPricingCommand(
        BigDecimal individualPrice,
        String reason
) {
}
