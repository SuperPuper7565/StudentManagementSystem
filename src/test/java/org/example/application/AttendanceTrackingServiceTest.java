package org.example.application;

import org.example.domain.model.Booking;
import org.example.domain.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceTrackingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private AttendanceTrackingService attendanceService;

    @Test
    @DisplayName("Отметка посещаемости для активной брони")
    void markAttendance_Success() {
        Booking booking = Booking.builder().id(1L).isCancelled(false).isAttended(false).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        attendanceService.markAttendance(1L, true);

        assertThat(booking.isAttended()).isTrue();
        verify(bookingRepository, times(1)).save(booking);
    }

    @Test
    @DisplayName("Ошибка при попытке отметить посещаемость отмененного занятия")
    void markAttendance_ThrowsException_WhenCancelled() {
        Booking booking = Booking.builder().id(1L).isCancelled(true).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> attendanceService.markAttendance(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Нельзя отметить посещаемость для отмененного занятия");

        verify(bookingRepository, never()).save(any());
    }
}