package org.example;

import java.math.BigDecimal;

public record Active(
        BigDecimal lessonPrice,
        LessonFormat lessonFormat
) implements StudentStatus {
}
