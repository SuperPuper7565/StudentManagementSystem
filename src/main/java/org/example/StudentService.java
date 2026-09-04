package org.example;

import java.util.List;

public interface StudentService {
    List<StudentAdminDTO> prepareForAdminPanel(List<Student> students);
}
