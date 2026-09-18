package org.example.domain.model;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonSlot {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean isBooked;

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        if (startTime != null) {
            this.endTime = startTime.plusMinutes(90);
        }
    }
}
