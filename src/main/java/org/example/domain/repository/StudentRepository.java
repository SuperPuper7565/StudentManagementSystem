package org.example.domain.repository;

import org.example.domain.model.Student;
import org.example.domain.status.StudentStatus;

import java.util.List;
import java.util.Optional;

public interface StudentRepository {
    Student save(Student student);
    Optional<Student> findById(Long id);
    List<Student> findAll();
    void updateStatus(Long id, StudentStatus newStatus);
}