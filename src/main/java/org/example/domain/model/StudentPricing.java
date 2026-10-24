package org.example.domain.model;

import java.math.BigDecimal;

public record StudentPricing(
        Long studentId,
        BigDecimal individualPrice,
        String reason
) {
}
