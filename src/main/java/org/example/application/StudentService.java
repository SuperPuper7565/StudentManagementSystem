package org.example.application;

import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.Student;

import java.util.List;

public interface StudentService {
    List<StudentAdminDTO> prepareForAdminPanel(List<Student> students);
}
