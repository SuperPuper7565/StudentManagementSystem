package org.example.domain.repository;

import java.util.List;

public interface TeacherSubjectRepository {

    List<String> findAll();

    void replaceAll(List<String> subjects);
}
