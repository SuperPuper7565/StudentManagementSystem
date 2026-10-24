package org.example.domain.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    private Long id;
    private Student student;
    private LessonSlot slot;
    private LocalDateTime bookedAt;
    private boolean isCancelled;
    private boolean isAttended;
    private BigDecimal price;
    private String priceDetails;
}
