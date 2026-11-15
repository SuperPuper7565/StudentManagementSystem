package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.repository.TeacherSubjectRepository;
import org.example.infrastructure.persistence.entity.TeacherSubjectEntity;
import org.example.infrastructure.persistence.repository.TeacherSubjectJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TeacherSubjectRepositoryImpl implements TeacherSubjectRepository {

    private final TeacherSubjectJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> findAll() {
        return jpaRepository.findAllByOrderBySubjectAsc().stream()
                .map(TeacherSubjectEntity::getSubject)
                .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<String> subjects) {
        jpaRepository.deleteAllInBatch();
        if (subjects == null || subjects.isEmpty()) {
            return;
        }

        List<TeacherSubjectEntity> entities = subjects.stream()
                .distinct()
                .map(subject -> {
                    TeacherSubjectEntity entity = new TeacherSubjectEntity();
                    entity.setSubject(subject);
                    return entity;
                })
                .toList();
        jpaRepository.saveAll(entities);
    }
}
