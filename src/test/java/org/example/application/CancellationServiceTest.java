package org.example.application;

import org.example.domain.model.Booking;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.LessonSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private LessonSlotRepository slotRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CancellationService cancellationService;

    private Booking activeBooking;
    private LessonSlot bookedSlot;

    @BeforeEach
    void setUp() {
        bookedSlot = new LessonSlot();
        bookedSlot.setId(10L);
        bookedSlot.setStartTime(LocalDateTime.now().plusDays(1));
        bookedSlot.setBooked(true);

        Student student = Student.builder().name("Ольга").build();

        activeBooking = Booking.builder()
                .id(100L)
                .student(student)
                .slot(bookedSlot)
                .isCancelled(false)
                .build();
    }

    @Test
    @DisplayName("Отмена бронирования: освобождает слот и отправляет уведомление")
    void cancelBooking_Success() {
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(activeBooking));

        cancellationService.cancelBooking(100L);

        assertThat(activeBooking.isCancelled()).isTrue();
        assertThat(bookedSlot.isBooked()).isFalse();

        verify(bookingRepository, times(1)).save(activeBooking);
        verify(slotRepository, times(1)).save(bookedSlot);
        verify(notificationService, times(1)).notifyBookingCancelled(eq("Ольга"), any());
    }

    @Test
    @DisplayName("Ошибка при попытке повторной отмены уже отмененной брони")
    void cancelBooking_ThrowsException_WhenAlreadyCancelled() {
        activeBooking.setCancelled(true);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(activeBooking));

        assertThatThrownBy(() -> cancellationService.cancelBooking(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Запись уже отменена");

        verify(notificationService, never()).notifyBookingCancelled(any(), any());
    }
}