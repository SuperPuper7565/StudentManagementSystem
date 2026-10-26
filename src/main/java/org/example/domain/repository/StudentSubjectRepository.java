package org.example.domain.repository;

import java.util.List;

public interface StudentSubjectRepository {

    List<String> findByStudentId(Long studentId);

    void add(Long studentId, String subject);

    void remove(Long studentId, String subject);
}
