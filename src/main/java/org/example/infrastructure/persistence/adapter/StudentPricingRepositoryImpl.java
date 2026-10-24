package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.StudentPricing;
import org.example.domain.repository.StudentPricingRepository;
import org.example.infrastructure.persistence.entity.StudentPricingEntity;
import org.example.infrastructure.persistence.repository.StudentPricingJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudentPricingRepositoryImpl implements StudentPricingRepository {

    private final StudentPricingJpaRepository jpaRepository;

    @Override
    public Optional<StudentPricing> findById(Long studentId) {
        return jpaRepository.findById(studentId).map(this::toDomain);
    }

    @Override
    public StudentPricing save(StudentPricing pricing) {
        StudentPricingEntity entity = new StudentPricingEntity();
        entity.setStudentId(pricing.studentId());
        entity.setIndividualPrice(pricing.individualPrice());
        entity.setReason(pricing.reason());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public void deleteById(Long studentId) {
        jpaRepository.findById(studentId).ifPresent(jpaRepository::delete);
    }

    private StudentPricing toDomain(StudentPricingEntity entity) {
        return new StudentPricing(
                entity.getStudentId(),
                entity.getIndividualPrice(),
                entity.getReason()
        );
    }
}
