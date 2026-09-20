package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.ScheduleViewService;
import org.example.application.SlotManagementService;
import org.example.domain.model.LessonSlot;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
@Tag(name = "Расписание", description = "Управление слотами и просмотр расписания")
public class ScheduleController {

    private final SlotManagementService slotManagementService;
    private final ScheduleViewService scheduleViewService;

    @PostMapping("/slots")
    @Operation(summary = "Создание нового слота для занятия (90 минут)")
    public ResponseEntity<LessonSlot> createSlot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime) {
        LessonSlot slot = slotManagementService.createSlot(startTime);
        return ResponseEntity.status(HttpStatus.CREATED).body(slot);
    }

    @GetMapping("/slots/available")
    @Operation(summary = "Просмотр свободных слотов")
    public ResponseEntity<List<LessonSlot>> getAvailableSlots() {
        return ResponseEntity.ok(scheduleViewService.getAvailableSlots());
    }
}