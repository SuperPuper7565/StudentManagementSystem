package org.example.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceQuote(
        BigDecimal basePrice,
        BigDecimal finalPrice,
        boolean trial,
        List<String> appliedRules,
        String details
) {
}
