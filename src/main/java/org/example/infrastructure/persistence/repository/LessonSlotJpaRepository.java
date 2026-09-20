package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.LessonSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LessonSlotJpaRepository extends JpaRepository<LessonSlotEntity, Long> {

    List<LessonSlotEntity> findByBookedFalseAndStartTimeAfter(LocalDateTime now);

    List<LessonSlotEntity> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}