package org.example.infrastructure.persistence.adapter;

import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.infrastructure.persistence.entity.LessonSlotEntity;
import org.example.infrastructure.persistence.entity.StudentEntity;
import org.example.infrastructure.persistence.repository.LessonSlotJpaRepository;
import org.example.infrastructure.persistence.repository.StudentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(BookingRepositoryImpl.class)
class BookingRepositoryImplTest {

    @Autowired
    private BookingRepositoryImpl repository;

    @Autowired
    private StudentJpaRepository studentJpaRepository;

    @Autowired
    private LessonSlotJpaRepository slotJpaRepository;

    @Test
    @DisplayName("Пробное бронирование учитывается до отмены, посещение — после отметки")
    void tracksTrialAndAttendance() {
        StudentEntity student = new StudentEntity();
        student.setFirstName("Иван");
        student.setLastName("Иванов");
        student.setStatusType("ACTIVE");
        student.setLessonFormat("ONLINE");
        student = studentJpaRepository.save(student);

        LessonSlotEntity slot = new LessonSlotEntity();
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(slot.getStartTime().plusMinutes(90));
        slot.setBooked(true);
        slot = slotJpaRepository.save(slot);

        Student studentDomain = Student.builder().id(student.getId()).build();
        LessonSlot slotDomain = new LessonSlot();
        slotDomain.setId(slot.getId());

        Booking saved = repository.save(Booking.builder()
                .student(studentDomain)
                .slot(slotDomain)
                .subject("Математика")
                .bookedAt(LocalDateTime.now())
                .isCancelled(false)
                .isAttended(false)
                .isTrial(true)
                .price(new BigDecimal("1000.00"))
                .priceDetails("Первое пробное занятие")
                .build());

        assertThat(saved.isTrial()).isTrue();
        assertThat(repository.hasActiveTrial(student.getId())).isTrue();
        assertThat(repository.hasAttendedLesson(student.getId())).isFalse();

        saved.setAttended(true);
        repository.save(saved);

        assertThat(repository.hasAttendedLesson(student.getId())).isTrue();
        assertThat(repository.hasActiveTrial(student.getId())).isTrue();

        saved.setCancelled(true);
        repository.save(saved);

        assertThat(repository.hasActiveTrial(student.getId())).isFalse();
        assertThat(repository.hasAttendedLesson(student.getId())).isTrue();
    }

    @Test
    @DisplayName("Считаются только посещённые и неотменённые занятия по предметам ученика")
    void countsAttendedSubjects() {
        StudentEntity student = new StudentEntity();
        student.setFirstName("Мария");
        student.setLastName("Петрова");
        student.setStatusType("ACTIVE");
        student.setLessonFormat("ONLINE");
        student = studentJpaRepository.save(student);

        Student studentDomain = Student.builder().id(student.getId()).build();

        Booking math = repository.save(bookingFor(studentDomain, "Математика"));
        Booking physics = repository.save(bookingFor(studentDomain, "Физика"));

        math.setAttended(true);
        repository.save(math);
        physics.setAttended(true);
        repository.save(physics);

        assertThat(repository.countAttendedSubjects(
                student.getId(), List.of("Математика", "Физика")
        )).isEqualTo(2);
        assertThat(repository.countAttendedSubjects(
                student.getId(), List.of("Математика")
        )).isEqualTo(1);

        physics.setCancelled(true);
        repository.save(physics);

        assertThat(repository.countAttendedSubjects(
                student.getId(), List.of("Математика", "Физика")
        )).isEqualTo(1);
    }

    private Booking bookingFor(Student student, String subject) {
        LessonSlotEntity slot = new LessonSlotEntity();
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(slot.getStartTime().plusMinutes(90));
        slot.setBooked(true);
        slot = slotJpaRepository.save(slot);

        LessonSlot slotDomain = new LessonSlot();
        slotDomain.setId(slot.getId());

        return Booking.builder()
                .student(student)
                .slot(slotDomain)
                .subject(subject)
                .bookedAt(LocalDateTime.now())
                .isCancelled(false)
                .isAttended(false)
                .isTrial(false)
                .price(new BigDecimal("2000.00"))
                .priceDetails("Базовая цена")
                .build();
    }
}
