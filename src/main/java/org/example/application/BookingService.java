package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.LessonSlotRepository;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final StudentRepository studentRepository;
    private final LessonSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public Booking bookLesson(Long studentId, Long slotId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Студент не найден"));

        if (!(student.status() instanceof Active)) {
            throw new IllegalStateException("Запись невозможна: студент не активен");
        }

        LessonSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Слот не найден"));

        if (slot.isBooked()) {
            throw new IllegalStateException("Слот уже забронирован");
        }

        slot.setBooked(true);
        slotRepository.save(slot);

        Booking booking = Booking.builder()
                .student(student)
                .slot(slot)
                .bookedAt(LocalDateTime.now())
                .isCancelled(false)
                .isAttended(false)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        notificationService.notifyBookingCreated(student.name(), slot.getStartTime());

        return savedBooking;
    }
}