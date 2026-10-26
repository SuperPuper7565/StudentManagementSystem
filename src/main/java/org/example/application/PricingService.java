package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.PriceQuote;
import org.example.domain.model.Student;
import org.example.domain.model.StudentPricing;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.StudentPricingRepository;
import org.example.domain.repository.StudentRepository;
import org.example.infrastructure.config.PricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final StudentRepository studentRepository;
    private final StudentPricingRepository pricingRepository;
    private final BookingRepository bookingRepository;
    private final StudentSubjectService subjectService;
    private final ReferralService referralService;
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

        List<PriceCandidate> candidates = new ArrayList<>();
        candidates.add(new PriceCandidate(
                basePrice,
                "BASE",
                "Базовая цена " + formatMoney(basePrice)
        ));

        if (isTrialAvailable(student)) {
            BigDecimal trialPrice = properties.getTrialPrice();
            candidates.add(new PriceCandidate(
                    trialPrice,
                    "TRIAL",
                    "Первое пробное занятие " + formatMoney(trialPrice)
            ));
        }

        Optional<StudentPricing> individual = pricingRepository.findById(student.id());
        if (individual.isPresent()) {
            BigDecimal individualPrice = individual.get().individualPrice();
            if (individualPrice != null && individualPrice.signum() > 0) {
                String details = "Индивидуальная цена " + formatMoney(individualPrice);
                if (individual.get().reason() != null && !individual.get().reason().isBlank()) {
                    details += " (" + individual.get().reason().trim() + ")";
                }
                candidates.add(new PriceCandidate(individualPrice, "INDIVIDUAL", details));
            }
        }

        if (isMultiSubjectActive(student)) {
            BigDecimal multiSubjectPrice = properties.getMultiSubjectPrice();
            candidates.add(new PriceCandidate(
                    multiSubjectPrice,
                    "MULTI_SUBJECT",
                    "Цена за занятия по двум предметам " + formatMoney(multiSubjectPrice)
            ));
        }

        if (referralService.isActiveForStudent(student.id())) {
            BigDecimal referralPrice = properties.getReferralPrice();
            candidates.add(new PriceCandidate(
                    referralPrice,
                    "REFERRAL",
                    "Реферальная цена " + formatMoney(referralPrice)
            ));
        }

        PriceCandidate best = candidates.get(0);
        for (PriceCandidate candidate : candidates) {
            if (candidate.price().compareTo(best.price()) < 0) {
                best = candidate;
            }
        }

        return new PriceQuote(
                basePrice,
                best.price(),
                "TRIAL".equals(best.rule()),
                List.of(best.rule()),
                best.details()
        );
    }

    private boolean isTrialAvailable(Student student) {
        return !bookingRepository.hasActiveTrial(student.id())
                && !bookingRepository.hasAttendedLesson(student.id());
    }

    private boolean isMultiSubjectActive(Student student) {
        List<String> subjects = subjectService.getSubjects(student);
        if (subjects.size() < 2) {
            return false;
        }
        return bookingRepository.countAttendedSubjects(student.id(), subjects) >= 2;
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

    private record PriceCandidate(BigDecimal price, String rule, String details) {
    }
}
