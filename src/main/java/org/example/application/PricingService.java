package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.PriceQuote;
import org.example.domain.model.Student;
import org.example.domain.model.StudentPricing;
import org.example.domain.repository.StudentPricingRepository;
import org.example.domain.repository.StudentRepository;
import org.example.infrastructure.config.PricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final StudentRepository studentRepository;
    private final StudentPricingRepository pricingRepository;
    private final PricingProperties properties;

    public BigDecimal defaultPrice() {
        return properties.getBasePrice();
    }

    public PriceQuote quote(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));
        return quote(student);
    }

    public PriceQuote quote(Student student) {
        BigDecimal basePrice = properties.getBasePrice();
        BigDecimal finalPrice = basePrice;
        String details = "Базовая цена " + formatMoney(basePrice);

        Optional<StudentPricing> individual = pricingRepository.findById(student.id());
        if (individual.isPresent()) {
            BigDecimal individualPrice = individual.get().individualPrice();
            if (individualPrice != null && individualPrice.compareTo(finalPrice) < 0) {
                finalPrice = individualPrice;
                details = "Индивидуальная цена " + formatMoney(individualPrice);
                if (individual.get().reason() != null && !individual.get().reason().isBlank()) {
                    details += " (" + individual.get().reason().trim() + ")";
                }
            }
        }

        return new PriceQuote(basePrice, finalPrice, details);
    }

    public void setIndividualPrice(Long studentId, BigDecimal individualPrice, String reason) {
        if (individualPrice == null || individualPrice.signum() <= 0) {
            throw new IllegalArgumentException("Индивидуальная цена должна быть больше нуля");
        }
        studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));

        BigDecimal normalized = individualPrice.setScale(2, RoundingMode.HALF_UP);
        String normalizedReason = reason == null ? null : reason.trim();
        if (normalizedReason != null && normalizedReason.isBlank()) {
            normalizedReason = null;
        }

        pricingRepository.save(new StudentPricing(studentId, normalized, normalizedReason));
    }

    public void clearIndividualPrice(Long studentId) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + studentId));
        pricingRepository.deleteById(studentId);
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.setScale(0).toPlainString() + " ₽";
        }
        return normalized.setScale(2, RoundingMode.HALF_UP).toPlainString() + " ₽";
    }
}
