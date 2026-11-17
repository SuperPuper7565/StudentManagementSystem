package org.example.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SlotOverlapResponse(
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean overlap,
        List<SlotConflictDto> conflicts
) {
}
