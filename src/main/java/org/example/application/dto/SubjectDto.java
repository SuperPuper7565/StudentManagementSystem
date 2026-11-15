package org.example.application.dto;

import org.example.domain.model.Subject;

public record SubjectDto(
        String code,
        String name
) {
    public static SubjectDto from(Subject subject) {
        return new SubjectDto(subject.getCode(), subject.getDisplayName());
    }
}
