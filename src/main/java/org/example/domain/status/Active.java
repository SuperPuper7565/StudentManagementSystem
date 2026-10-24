package org.example.domain.status;

import org.example.domain.model.LessonFormat;

public record Active(
        LessonFormat lessonFormat
) implements StudentStatus {
}
