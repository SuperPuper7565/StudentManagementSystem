package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.*;
import org.example.infrastructure.persistence.entity.StudentEntity;
import org.example.infrastructure.persistence.repository.StudentJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudentRepositoryImpl implements StudentRepository {

    private final StudentJpaRepository jpaRepository;

    @Override
    public Student save(Student student) {
        StudentEntity entity = toEntity(student);
        StudentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Student> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Student> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateStatus(Long id, StudentStatus newStatus) {
        StudentEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + id));
        applyStatusToEntity(newStatus, entity);
        jpaRepository.save(entity);
    }

    private Student toDomain(StudentEntity entity) {
        StudentStatus status = switch (entity.getStatusType()) {
            case "ACTIVE" -> new Active(entity.getLessonPrice(),
                    entity.getLessonFormat() != null ? LessonFormat.valueOf(entity.getLessonFormat()) : null);
            case "PAUSED" -> new Paused(entity.getPauseReason(), entity.getPauseStartDate(), entity.getPauseEndDate());
            case "GRADUATED" -> new Graduated(entity.getGraduationDate(), entity.getExamResult() != null ? entity.getExamResult() : 0);
            default -> null;
        };

        return Student.builder()
                .id(entity.getId())
                .name(entity.getFirstName())
                .surname(entity.getLastName())
                .subject(entity.getSubject())
                .grade(entity.getGrade() != null ? entity.getGrade() : 0)
                .goal(entity.getGoal() != null ? StudyGoal.valueOf(entity.getGoal()) : null)
                .status(status)
                .build();
    }

    private StudentEntity toEntity(Student domain) {
        StudentEntity entity = new StudentEntity();
        entity.setId(domain.id());
        entity.setFirstName(domain.name());
        entity.setLastName(domain.surname());
        entity.setSubject(domain.subject());
        entity.setGrade(domain.grade());
        if (domain.goal() != null) {
            entity.setGoal(domain.goal().name());
        }
        if (domain.status() != null) {
            applyStatusToEntity(domain.status(), entity);
        }
        return entity;
    }

    private void applyStatusToEntity(StudentStatus status, StudentEntity entity) {
        switch (status) {
            case Active a -> {
                entity.setStatusType("ACTIVE");
                entity.setLessonPrice(a.lessonPrice());
                entity.setLessonFormat(a.lessonFormat() != null ? a.lessonFormat().name() : null);
            }
            case Paused p -> {
                entity.setStatusType("PAUSED");
                entity.setPauseReason(p.reason());
                entity.setPauseStartDate(p.startDate());
                entity.setPauseEndDate(p.endDate());
            }
            case Graduated g -> {
                entity.setStatusType("GRADUATED");
                entity.setGraduationDate(g.graduationDate());
                entity.setExamResult(g.examResult());
            }
        }
    }
}