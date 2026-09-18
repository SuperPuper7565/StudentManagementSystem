package org.example.application;

import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.Student;

import java.util.List;

/**
 * Реализация сервиса управления данными студентов.
 * Отвечает за бизнес-логику преобразования доменных моделей в DTO для административной панели.
 */

public class StudentServiceImpl implements StudentService {

    private final StudentStatusFormatter statusFormatter;

    // Внедряем форматтер через конструктор
    public StudentServiceImpl(StudentStatusFormatter statusFormatter) {
        this.statusFormatter = statusFormatter;
    }

    @Override
    public List<StudentAdminDTO> prepareForAdminPanel(List<Student> students) {
        return students.stream()
                .map(this::toAdminDto)
                .toList();
    }

    private StudentAdminDTO toAdminDto(Student student) {
        String fullName = student.name() + " " + student.surname();
        String statusDescription = statusFormatter.format(student.status());

        return new StudentAdminDTO(
                student.id(),
                fullName,
                student.subject(),
                student.grade(),
                student.goal().getDescription(),
                statusDescription
        );
    }
}