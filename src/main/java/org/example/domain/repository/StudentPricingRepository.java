package org.example.domain.repository;

import org.example.domain.model.StudentPricing;

import java.util.Optional;

public interface StudentPricingRepository {
    Optional<StudentPricing> findById(Long studentId);
    StudentPricing save(StudentPricing pricing);
    void deleteById(Long studentId);
}
