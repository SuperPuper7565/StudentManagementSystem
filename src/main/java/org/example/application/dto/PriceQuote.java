package org.example.application.dto;

import java.math.BigDecimal;

public record PriceQuote(
        BigDecimal basePrice,
        BigDecimal finalPrice,
        String details
) {
}
