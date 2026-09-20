package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.AttendanceTrackingService;
import org.example.application.BookingService;
import org.example.application.CancellationService;
import org.example.domain.model.Booking;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Бронирование", description = "Запись на уроки, отмена и посещаемость")
public class BookingController {

    private final BookingService bookingService;
    private final CancellationService cancellationService;
    private final AttendanceTrackingService attendanceTrackingService;

    @PostMapping
    @Operation(summary = "Запись студента на свободный слот")
    public ResponseEntity<Booking> bookLesson(@RequestParam Long studentId, @RequestParam Long slotId) {
        Booking booking = bookingService.bookLesson(studentId, slotId);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Отмена записи на урок")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long bookingId) {
        cancellationService.cancelBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bookingId}/attendance")
    @Operation(summary = "Отметка посещаемости занятия")
    public ResponseEntity<Void> markAttendance(@PathVariable Long bookingId, @RequestParam boolean attended) {
        attendanceTrackingService.markAttendance(bookingId, attended);
        return ResponseEntity.ok().build();
    }
}