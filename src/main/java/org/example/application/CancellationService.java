package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.LessonSlotRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CancellationService {

    private final BookingRepository bookingRepository;
    private final LessonSlotRepository slotRepository;
    private final NotificationService notificationService;

    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Запись не найдена"));

        if (booking.isCancelled()) {
            throw new IllegalStateException("Запись уже отменена");
        }

        booking.setCancelled(true);
        bookingRepository.save(booking);

        LessonSlot slot = booking.getSlot();
        slot.setBooked(false);
        slotRepository.save(slot);

        notificationService.notifyBookingCancelled(
                booking.getStudent().name(),
                slot.getStartTime()
        );
    }
}