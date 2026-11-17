package org.example.application.dto;

import org.example.domain.model.LessonFormat;

import java.time.LocalDateTime;

public record SlotConflictDto(
        Long id,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LessonFormat format,
        boolean booked
) {
}
