package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.PriceQuote;
import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.LessonSlotRepository;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final StudentRepository studentRepository;
    private final LessonSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final PricingService pricingService;
    private final StudentSubjectService subjectService;

    public Booking bookLesson(Long studentId, Long slotId, String subject) {
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

        String requestedSubject = subject == null ? "" : subject.trim();
        if (requestedSubject.isEmpty()) {
            throw new IllegalArgumentException("Предмет не указан");
        }

        List<String> studentSubjects = subjectService.getSubjects(student);
        String canonicalSubject = studentSubjects.stream()
                .filter(existing -> existing.equalsIgnoreCase(requestedSubject))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "У ученика нет предмета: " + requestedSubject
                ));

        PriceQuote quote = pricingService.quote(student);

        slot.setBooked(true);
        slotRepository.save(slot);

        Booking booking = Booking.builder()
                .student(student)
                .slot(slot)
                .subject(canonicalSubject)
                .bookedAt(LocalDateTime.now())
                .isCancelled(false)
                .isAttended(false)
                .isTrial(quote.trial())
                .price(quote.finalPrice())
                .priceDetails(quote.details())
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        notificationService.notifyBookingCreated(student.name(), slot.getStartTime());

        return savedBooking;
    }
}