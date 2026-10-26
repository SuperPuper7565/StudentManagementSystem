package org.example.application;

import org.example.application.dto.PriceQuote;
import org.example.domain.model.Booking;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.LessonSlot;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.LessonSlotRepository;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.example.domain.status.Paused;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LessonSlotRepository slotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PricingService pricingService;

    @InjectMocks
    private BookingService bookingService;

    private Student activeStudent;
    private LessonSlot freeSlot;

    @BeforeEach
    void setUp() {
        activeStudent = Student.builder()
                .id(1L)
                .name("Иван")
                .surname("Иванов")
                .status(new Active(LessonFormat.ONLINE))
                .build();

        freeSlot = new LessonSlot();
        freeSlot.setId(10L);
        freeSlot.setStartTime(LocalDateTime.now().plusDays(1));
        freeSlot.setBooked(false);
    }

    @Test
    @DisplayName("Успешное бронирование урока активным студентом с фиксацией цены")
    void bookLesson_Success() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent));
        when(slotRepository.findById(10L)).thenReturn(Optional.of(freeSlot));
        when(pricingService.quote(activeStudent))
                .thenReturn(new PriceQuote(
                        new BigDecimal("2000.00"),
                        new BigDecimal("1800.00"),
                        false,
                        List.of("MULTI_SUBJECT"),
                        "Скидка за второй предмет"
                ));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = bookingService.bookLesson(1L, 10L);

        assertThat(booking).isNotNull();
        assertThat(booking.getPrice()).isEqualByComparingTo("1800.00");
        assertThat(booking.getPriceDetails()).isEqualTo("Скидка за второй предмет");
        assertThat(booking.isTrial()).isFalse();
        assertThat(freeSlot.isBooked()).isTrue();
        verify(slotRepository, times(1)).save(freeSlot);
        verify(notificationService, times(1)).notifyBookingCreated(eq("Иван"), any());
    }

    @Test
    @DisplayName("Пробное занятие сохраняется в бронировании")
    void bookLesson_PersistsTrialFlag() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(activeStudent));
        when(slotRepository.findById(10L)).thenReturn(Optional.of(freeSlot));
        when(pricingService.quote(activeStudent))
                .thenReturn(new PriceQuote(
                        new BigDecimal("2000.00"),
                        new BigDecimal("1000.00"),
                        true,
                        List.of("TRIAL"),
                        "Первое пробное занятие 1 000 ₽"
                ));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = bookingService.bookLesson(1L, 10L);

        assertThat(booking.getPrice()).isEqualByComparingTo("1000.00");
        assertThat(booking.isTrial()).isTrue();
    }

    @Test
    @DisplayName("Ошибка бронирования, если студент на паузе")
    void bookLesson_ThrowsException_WhenStudentNotActive() {
        Student pausedStudent = Student.builder()
                .id(2L)
                .status(new Paused("Болезнь", LocalDate.now(), LocalDate.now().plusDays(7)))
                .build();

        when(studentRepository.findById(2L)).thenReturn(Optional.of(pausedStudent));

        assertThatThrownBy(() -> bookingService.bookLesson(2L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("студент не активен");

        verify(bookingRepository, never()).save(any());
    }
}
