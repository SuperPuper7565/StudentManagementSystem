package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.LessonSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LessonSlotJpaRepository extends JpaRepository<LessonSlotEntity, Long> {

    List<LessonSlotEntity> findByBookedFalseAndStartTimeAfter(LocalDateTime now);

    List<LessonSlotEntity> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(s) > 0 FROM LessonSlotEntity s " +
            "WHERE :newStart < s.endTime AND :newEnd > s.startTime")
    boolean existsOverlappingSlot(@Param("newStart") LocalDateTime newStart,
                                  @Param("newEnd") LocalDateTime newEnd);
}