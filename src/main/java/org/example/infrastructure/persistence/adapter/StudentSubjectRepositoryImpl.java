package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.repository.StudentSubjectRepository;
import org.example.infrastructure.persistence.entity.StudentSubjectEntity;
import org.example.infrastructure.persistence.repository.StudentSubjectJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StudentSubjectRepositoryImpl implements StudentSubjectRepository {

    private final StudentSubjectJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> findByStudentId(Long studentId) {
        return jpaRepository.findByStudentIdOrderBySubjectAsc(studentId).stream()
                .map(StudentSubjectEntity::getSubject)
                .toList();
    }

    @Override
    @Transactional
    public void add(Long studentId, String subject) {
        if (jpaRepository.existsByStudentIdAndSubject(studentId, subject)) {
            return;
        }

        StudentSubjectEntity entity = new StudentSubjectEntity();
        entity.setStudentId(studentId);
        entity.setSubject(subject);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void remove(Long studentId, String subject) {
        jpaRepository.deleteByStudentIdAndSubject(studentId, subject);
    }
}
