package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.StudentSubjectService;
import org.example.infrastructure.security.AdminKeyGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/students/{studentId}/subjects")
@RequiredArgsConstructor
@Tag(name = "Предметы ученика", description = "Управление предметами, по которым занимается ученик")
public class StudentSubjectController {

    private final StudentSubjectService subjectService;
    private final AdminKeyGuard adminKeyGuard;

    @GetMapping
    @Operation(summary = "Получить предметы ученика")
    public ResponseEntity<List<String>> getSubjects(@PathVariable Long studentId) {
        return ResponseEntity.ok(subjectService.getSubjects(studentId));
    }

    @PostMapping
    @Operation(summary = "Добавить предмет ученику (только преподаватель)")
    public ResponseEntity<Void> addSubject(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestParam String subject
    ) {
        adminKeyGuard.check(adminKey);
        subjectService.addSubject(studentId, subject);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Удалить предмет ученика (только преподаватель)")
    public ResponseEntity<Void> removeSubject(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestParam String subject
    ) {
        adminKeyGuard.check(adminKey);
        subjectService.removeSubject(studentId, subject);
        return ResponseEntity.noContent().build();
    }
}
