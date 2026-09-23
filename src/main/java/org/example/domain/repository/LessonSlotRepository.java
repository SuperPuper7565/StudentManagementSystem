package org.example.domain.repository;

import org.example.domain.model.LessonSlot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LessonSlotRepository {
    LessonSlot save(LessonSlot slot);
    Optional<LessonSlot> findById(Long id);
    List<LessonSlot> findAvailableSlots(LocalDateTime now);
    List<LessonSlot> findSlotsBetween(LocalDateTime start, LocalDateTime end);
    boolean existsOverlappingSlot(LocalDateTime startTime, LocalDateTime endTime);
}