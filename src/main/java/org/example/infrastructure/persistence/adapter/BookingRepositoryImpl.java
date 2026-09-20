package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.infrastructure.persistence.entity.BookingEntity;
import org.example.infrastructure.persistence.entity.LessonSlotEntity;
import org.example.infrastructure.persistence.entity.StudentEntity;
import org.example.infrastructure.persistence.repository.BookingJpaRepository;
import org.example.infrastructure.persistence.repository.LessonSlotJpaRepository;
import org.example.infrastructure.persistence.repository.StudentJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepository {

    private final BookingJpaRepository bookingJpaRepository;
    private final StudentJpaRepository studentJpaRepository;
    private final LessonSlotJpaRepository slotJpaRepository;

    @Override
    public Booking save(Booking booking) {
        BookingEntity entity = toEntity(booking);
        BookingEntity saved = bookingJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return bookingJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Booking> findByStudentId(Long studentId) {
        return bookingJpaRepository.findByStudentId(studentId).stream()
                .map(this::toDomain)
                .toList();
    }

    private Booking toDomain(BookingEntity entity) {
        Student student = Student.builder()
                .id(entity.getStudent().getId())
                .name(entity.getStudent().getFirstName())
                .surname(entity.getStudent().getLastName())
                .subject(entity.getStudent().getSubject())
                .build();

        LessonSlot slot = new LessonSlot();
        slot.setId(entity.getSlot().getId());
        slot.setStartTime(entity.getSlot().getStartTime());
        slot.setBooked(entity.getSlot().isBooked());

        return Booking.builder()
                .id(entity.getId())
                .student(student)
                .slot(slot)
                .bookedAt(entity.getBookedAt())
                .isCancelled(entity.isCancelled())
                .isAttended(entity.isAttended())
                .build();
    }

    private BookingEntity toEntity(Booking domain) {
        BookingEntity entity = new BookingEntity();
        entity.setId(domain.getId());

        if (domain.getStudent() != null) {
            StudentEntity studentEntity = studentJpaRepository.findById(domain.getStudent().id())
                    .orElseThrow(() -> new IllegalArgumentException("Студент не найден: " + domain.getStudent().id()));
            entity.setStudent(studentEntity);
        }

        if (domain.getSlot() != null && domain.getSlot().getId() != null) {
            LessonSlotEntity slotEntity = slotJpaRepository.findById(domain.getSlot().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Слот не найден: " + domain.getSlot().getId()));
            entity.setSlot(slotEntity);
        }

        entity.setBookedAt(domain.getBookedAt());
        entity.setCancelled(domain.isCancelled());
        entity.setAttended(domain.isAttended());
        return entity;
    }
}