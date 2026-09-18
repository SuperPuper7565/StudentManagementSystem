package org.example.domain.status;

import org.example.domain.model.LessonFormat;

import java.math.BigDecimal;

public record Active(
        BigDecimal lessonPrice,
        LessonFormat lessonFormat
) implements StudentStatus {
}
