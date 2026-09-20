package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.StudentAdminService;
import org.example.application.StudentRegistrationService;
import org.example.application.dto.CreateStudentCommand;
import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.Student;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Студенты", description = "API для регистрации и управления студентами")
public class StudentController {

    private final StudentRegistrationService registrationService;
    private final StudentAdminService adminService;

    @PostMapping
    @Operation(summary = "Регистрация нового студента")
    public ResponseEntity<Student> registerStudent(@RequestBody CreateStudentCommand command) {
        Student student = registrationService.registerStudent(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(student);
    }

    @GetMapping("/admin-list")
    @Operation(summary = "Получение списка всех студентов для панели администратора")
    public ResponseEntity<List<StudentAdminDTO>> getAllStudentsForAdmin() {
        return ResponseEntity.ok(adminService.getAllStudentsForAdmin());
    }
}