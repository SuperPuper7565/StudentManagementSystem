package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.SubjectCatalogService;
import org.example.application.dto.SubjectDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
@Tag(name = "Предметы", description = "Фиксированный справочник предметов")
public class SubjectController {

    private final SubjectCatalogService subjectCatalogService;

    @GetMapping
    @Operation(summary = "Получить справочник предметов")
    public ResponseEntity<List<SubjectDto>> getSubjects() {
        return ResponseEntity.ok(subjectCatalogService.all().stream()
                .map(SubjectDto::from)
                .toList());
    }
}
