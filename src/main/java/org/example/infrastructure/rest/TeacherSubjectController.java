package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.TeacherSubjectService;
import org.example.application.dto.SubjectDto;
import org.example.application.dto.UpdateTeacherSubjectsCommand;
import org.example.infrastructure.security.AdminKeyGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher/subjects")
@RequiredArgsConstructor
@Tag(name = "Предметы преподавателя", description = "Предметы, которые ведёт преподаватель")
public class TeacherSubjectController {

    private final TeacherSubjectService teacherSubjectService;
    private final AdminKeyGuard adminKeyGuard;

    @GetMapping
    @Operation(summary = "Получить предметы преподавателя")
    public ResponseEntity<List<SubjectDto>> getSubjects() {
        return ResponseEntity.ok(teacherSubjectService.getSubjects().stream()
                .map(SubjectDto::from)
                .toList());
    }

    @PutMapping
    @Operation(summary = "Заменить список предметов преподавателя (только преподаватель)")
    public ResponseEntity<Void> updateSubjects(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestBody UpdateTeacherSubjectsCommand command
    ) {
        adminKeyGuard.check(adminKey);
        teacherSubjectService.setSubjects(command == null ? null : command.subjectCodes());
        return ResponseEntity.noContent().build();
    }
}
