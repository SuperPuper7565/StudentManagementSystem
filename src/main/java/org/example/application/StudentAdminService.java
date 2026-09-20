package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentAdminService {

    private final StudentRepository studentRepository;

    public List<StudentAdminDTO> getAllStudentsForAdmin() {
        return studentRepository.findAll().stream()
                .map(this::toAdminDTO)
                .toList();
    }

    public void updateStudentStatus(Long studentId, StudentStatus newStatus) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден с ID: " + studentId));

        studentRepository.updateStatus(student.id(), newStatus);
    }

    private StudentAdminDTO toAdminDTO(Student student) {
        String statusDesc = switch (student.status()) {
            case Active a -> "Активен (" + a.lessonFormat() + ", " + a.lessonPrice() + " руб/урок)";
            case Paused p -> "На паузе до " + p.endDate() + " (Причина: " + p.reason() + ")";
            case Graduated g -> "Выпустился " + g.graduationDate() + " (Балл: " + g.examResult() + ")";
            case null -> "Статус не указан";
        };

        return new StudentAdminDTO(
                student.id(),
                student.name() + " " + student.surname(),
                student.subject(),
                student.grade(),
                student.goal() != null ? student.goal().name() : "Не указана",
                statusDesc
        );
    }
}