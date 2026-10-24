package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.StudentPricingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentPricingJpaRepository extends JpaRepository<StudentPricingEntity, Long> {
}
