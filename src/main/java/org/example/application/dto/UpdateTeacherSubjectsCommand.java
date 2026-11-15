package org.example.application.dto;

import java.util.List;

public record UpdateTeacherSubjectsCommand(
        List<String> subjectCodes
) {
}
