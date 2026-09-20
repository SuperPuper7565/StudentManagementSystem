package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, Long> {

    List<BookingEntity> findByStudentId(Long studentId);
}