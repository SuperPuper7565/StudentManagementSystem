package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Booking;
import org.example.domain.repository.BookingRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttendanceTrackingService {

    private final BookingRepository bookingRepository;

    public void markAttendance(Long bookingId, boolean isAttended) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена"));

        if (booking.isCancelled()) {
            throw new IllegalStateException("Нельзя отметить посещаемость для отмененного занятия");
        }

        booking.setAttended(isAttended);
        bookingRepository.save(booking);
    }
}