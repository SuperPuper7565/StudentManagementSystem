package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.StudentSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentSubjectJpaRepository extends JpaRepository<StudentSubjectEntity, Long> {

    List<StudentSubjectEntity> findByStudentIdOrderBySubjectAsc(Long studentId);

    boolean existsByStudentIdAndSubject(Long studentId, String subject);

    void deleteByStudentIdAndSubject(Long studentId, String subject);
}
