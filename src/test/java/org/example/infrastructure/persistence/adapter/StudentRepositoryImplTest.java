package org.example.infrastructure.persistence.adapter;

import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.status.Active;
import org.example.infrastructure.persistence.repository.StudentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(StudentRepositoryImpl.class)
class StudentRepositoryImplTest {

    @Autowired
    private StudentRepositoryImpl studentRepository;

    @Autowired
    private StudentJpaRepository jpaRepository;

    @Test
    @DisplayName("Сохранение и чтение студента со статусом Active через адаптер")
    void saveAndFindStudent_Success() {
        Student newStudent = Student.builder()
                .name("Иван")
                .surname("Иванов")
                .subject("Математика")
                .grade(11)
                .goal(StudyGoal.EGE)
                .status(new Active(new BigDecimal("2500.00"), LessonFormat.ONLINE))
                .build();

        Student saved = studentRepository.save(newStudent);

        assertThat(saved.id()).isNotNull();

        Optional<Student> found = studentRepository.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Иван");
        assertThat(found.get().status()).isInstanceOf(Active.class);

        Active activeStatus = (Active) found.get().status();
        assertThat(activeStatus.lessonPrice()).isEqualByComparingTo("2500.00");
        assertThat(activeStatus.lessonFormat()).isEqualTo(LessonFormat.ONLINE);
    }
}