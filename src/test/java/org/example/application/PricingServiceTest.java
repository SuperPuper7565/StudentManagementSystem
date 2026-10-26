package org.example.application;

import org.example.application.dto.PriceQuote;
import org.example.domain.model.Student;
import org.example.domain.model.StudentPricing;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.StudentPricingRepository;
import org.example.domain.repository.StudentRepository;
import org.example.infrastructure.config.PricingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentPricingRepository pricingRepository;

    @Mock
    private BookingRepository bookingRepository;

    private PricingService pricingService;
    private Student student;

    @BeforeEach
    void setUp() {
        PricingProperties properties = new PricingProperties();
        properties.setBasePrice(new BigDecimal("2000.00"));
        properties.setTrialPrice(new BigDecimal("1000.00"));
        pricingService = new PricingService(
                studentRepository,
                pricingRepository,
                bookingRepository,
                properties
        );

        student = Student.builder().id(1L).name("Иван").surname("Иванов").build();
    }

    private void trialAvailable() {
        when(bookingRepository.hasActiveTrial(1L)).thenReturn(false);
        when(bookingRepository.hasAttendedLesson(1L)).thenReturn(false);
    }

    private void trialAlreadyUsed() {
        when(bookingRepository.hasActiveTrial(1L)).thenReturn(false);
        when(bookingRepository.hasAttendedLesson(1L)).thenReturn(true);
    }

    @Test
    @DisplayName("Без индивидуальной цены и без пробного действует базовая цена 2000")
    void quote_ReturnsBasePrice() {
        trialAlreadyUsed();
        when(pricingRepository.findById(1L)).thenReturn(Optional.empty());

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.basePrice()).isEqualByComparingTo("2000.00");
        assertThat(quote.finalPrice()).isEqualByComparingTo("2000.00");
        assertThat(quote.trial()).isFalse();
        assertThat(quote.appliedRules()).containsExactly("BASE");
        assertThat(quote.details()).contains("Базовая цена", "2000");
    }

    @Test
    @DisplayName("Индивидуальная цена 1500 понижает стоимость занятия")
    void quote_AppliesIndividualPrice() {
        trialAlreadyUsed();
        when(pricingRepository.findById(1L)).thenReturn(Optional.of(
                new StudentPricing(1L, new BigDecimal("1500.00"), "по знакомству")
        ));

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.finalPrice()).isEqualByComparingTo("1500.00");
        assertThat(quote.trial()).isFalse();
        assertThat(quote.appliedRules()).containsExactly("INDIVIDUAL");
        assertThat(quote.details()).contains("Индивидуальная цена", "1500", "по знакомству");
    }

    @Test
    @DisplayName("Индивидуальная цена выше базовой не увеличивает стоимость")
    void quote_DoesNotIncreasePrice() {
        trialAlreadyUsed();
        when(pricingRepository.findById(1L)).thenReturn(Optional.of(
                new StudentPricing(1L, new BigDecimal("2500.00"), null)
        ));

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.finalPrice()).isEqualByComparingTo("2000.00");
        assertThat(quote.trial()).isFalse();
        assertThat(quote.appliedRules()).containsExactly("BASE");
        assertThat(quote.details()).contains("Базовая цена");
    }

    @Test
    @DisplayName("Первое пробное занятие стоит 1000 и побеждает индивидуальную цену")
    void quote_AppliesTrialPrice() {
        trialAvailable();
        when(pricingRepository.findById(1L)).thenReturn(Optional.of(
                new StudentPricing(1L, new BigDecimal("1500.00"), "по знакомству")
        ));

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.finalPrice()).isEqualByComparingTo("1000.00");
        assertThat(quote.trial()).isTrue();
        assertThat(quote.appliedRules()).containsExactly("TRIAL");
        assertThat(quote.details()).contains("пробное", "1000");
    }

    @Test
    @DisplayName("Активное пробное бронирование не даёт вторую пробную цену")
    void quote_DoesNotApplyTrial_WhenTrialAlreadyBooked() {
        when(bookingRepository.hasActiveTrial(1L)).thenReturn(true);
        when(pricingRepository.findById(1L)).thenReturn(Optional.empty());

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.finalPrice()).isEqualByComparingTo("2000.00");
        assertThat(quote.trial()).isFalse();
        assertThat(quote.appliedRules()).containsExactly("BASE");
    }

    @Test
    @DisplayName("После посещённого занятия пробная цена больше не действует")
    void quote_DoesNotApplyTrial_WhenStudentHasAttendedLesson() {
        trialAlreadyUsed();
        when(pricingRepository.findById(1L)).thenReturn(Optional.empty());

        PriceQuote quote = pricingService.quote(student);

        assertThat(quote.finalPrice()).isEqualByComparingTo("2000.00");
        assertThat(quote.trial()).isFalse();
        assertThat(quote.appliedRules()).containsExactly("BASE");
    }

    @Test
    @DisplayName("Назначение индивидуальной цены сохраняет нормализованное значение")
    void setIndividualPrice_SavesNormalizedValue() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        pricingService.setIndividualPrice(1L, new BigDecimal("1500.5"), "  по знакомству  ");

        ArgumentCaptor<StudentPricing> captor = ArgumentCaptor.forClass(StudentPricing.class);
        verify(pricingRepository).save(captor.capture());

        assertThat(captor.getValue().studentId()).isEqualTo(1L);
        assertThat(captor.getValue().individualPrice()).isEqualByComparingTo("1500.50");
        assertThat(captor.getValue().reason()).isEqualTo("по знакомству");
    }

    @Test
    @DisplayName("Индивидуальная цена должна быть положительной")
    void setIndividualPrice_RejectsNonPositivePrice() {
        assertThatThrownBy(() -> pricingService.setIndividualPrice(1L, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("больше нуля");

        verify(pricingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Нельзя назначить цену несуществующему ученику")
    void setIndividualPrice_RejectsUnknownStudent() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.setIndividualPrice(99L, new BigDecimal("1500"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Студент не найден");

        verify(pricingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Удаление индивидуальной цены")
    void clearIndividualPrice_DeletesPricing() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        pricingService.clearIndividualPrice(1L);

        verify(pricingRepository).deleteById(1L);
    }
}
