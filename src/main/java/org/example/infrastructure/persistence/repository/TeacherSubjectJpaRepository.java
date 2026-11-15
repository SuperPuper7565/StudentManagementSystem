package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.TeacherSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherSubjectJpaRepository extends JpaRepository<TeacherSubjectEntity, Long> {

    List<TeacherSubjectEntity> findAllByOrderBySubjectAsc();
}
