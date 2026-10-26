package org.example.application;

import org.example.domain.model.Referral;
import org.example.domain.model.ReferralStatus;
import org.example.domain.model.Student;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.ReferralRepository;
import org.example.domain.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock
    private ReferralRepository referralRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ReferralService referralService;

    private Student referrer;
    private Student referred;

    @BeforeEach
    void setUp() {
        referrer = Student.builder().id(1L).name("Иван").surname("Иванов").build();
        referred = Student.builder().id(2L).name("Пётр").surname("Петров").build();
    }

    @Test
    @DisplayName("Ученик не может быть реферером сам себе")
    void createReferral_RejectsSelfReferral() {
        assertThatThrownBy(() -> referralService.createReferral(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("сам себе");

        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Один ученик не может быть приглашён повторно")
    void createReferral_RejectsDuplicate() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(referrer));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(referred));
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(
                Referral.builder()
                        .id(10L)
                        .referrerId(1L)
                        .referredId(2L)
                        .status(ReferralStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build()
        ));

        assertThatThrownBy(() -> referralService.createReferral(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже участвует");

        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("До первого посещённого занятия реферал ожидает активации")
    void createReferral_CreatesPendingReferral() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(referrer));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(referred));
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.empty());
        when(bookingRepository.hasAttendedLesson(2L)).thenReturn(false);
        when(referralRepository.save(any(Referral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Referral created = referralService.createReferral(1L, 2L);

        assertThat(created.status()).isEqualTo(ReferralStatus.PENDING);
        assertThat(created.activatedAt()).isNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.referrerId()).isEqualTo(1L);
        assertThat(created.referredId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Если друг уже посещал занятие, реферал активируется сразу")
    void createReferral_ActivatesImmediately_WhenReferredAlreadyAttended() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(referrer));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(referred));
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.empty());
        when(bookingRepository.hasAttendedLesson(2L)).thenReturn(true);
        when(referralRepository.save(any(Referral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Referral created = referralService.createReferral(1L, 2L);

        assertThat(created.status()).isEqualTo(ReferralStatus.ACTIVE);
        assertThat(created.activatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Нельзя создать реферал для несуществующего реферера")
    void createReferral_RejectsUnknownReferrer() {
        when(studentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> referralService.createReferral(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Реферер не найден");

        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Посещение занятия активирует ожидающий реферал")
    void activateForAttendedStudent_ActivatesPendingReferral() {
        Referral pending = Referral.builder()
                .id(10L)
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(pending));
        when(referralRepository.save(any(Referral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        referralService.activateForAttendedStudent(2L);

        ArgumentCaptor<Referral> captor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(captor.capture());
        Referral saved = captor.getValue();

        assertThat(saved.id()).isEqualTo(10L);
        assertThat(saved.referrerId()).isEqualTo(1L);
        assertThat(saved.referredId()).isEqualTo(2L);
        assertThat(saved.status()).isEqualTo(ReferralStatus.ACTIVE);
        assertThat(saved.activatedAt()).isNotNull();
        assertThat(saved.createdAt()).isEqualTo(pending.createdAt());
    }

    @Test
    @DisplayName("Отменённый вручную реферал не активируется посещением")
    void activateForAttendedStudent_DoesNotReactivateCancelledReferral() {
        Referral cancelled = Referral.builder()
                .id(10L)
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(cancelled));

        referralService.activateForAttendedStudent(2L);

        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Посещение ученика без реферала ничего не делает")
    void activateForAttendedStudent_DoesNothingWithoutReferral() {
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.empty());

        referralService.activateForAttendedStudent(2L);

        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Преподаватель может вручную активировать отменённый реферал")
    void activateReferral_ManuallyActivatesCancelledReferral() {
        Referral cancelled = Referral.builder()
                .id(10L)
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(cancelled));
        when(referralRepository.save(any(Referral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Referral activated = referralService.activateReferral(2L);

        assertThat(activated.status()).isEqualTo(ReferralStatus.ACTIVE);
        assertThat(activated.activatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Преподаватель может отменить активный реферал")
    void cancelReferral_CancelsActiveReferral() {
        Referral active = Referral.builder()
                .id(10L)
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .activatedAt(LocalDateTime.now())
                .build();
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(active));
        when(referralRepository.save(any(Referral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Referral cancelled = referralService.cancelReferral(2L);

        assertThat(cancelled.status()).isEqualTo(ReferralStatus.CANCELLED);
        assertThat(cancelled.activatedAt()).isNull();
    }

    @Test
    @DisplayName("Удаление реферала")
    void deleteReferral_DeletesExistingReferral() {
        Referral existing = Referral.builder()
                .id(10L)
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.of(existing));

        referralService.deleteReferral(2L);

        verify(referralRepository).deleteByReferredId(2L);
    }

    @Test
    @DisplayName("Нельзя удалить несуществующий реферал")
    void deleteReferral_RejectsUnknownReferral() {
        when(referralRepository.findByReferredId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> referralService.deleteReferral(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Реферал не найден");

        verify(referralRepository, never()).deleteByReferredId(anyLong());
    }

    @Test
    @DisplayName("Активность реферала проверяется по обеим сторонам")
    void isActiveForStudent_DelegatesToRepository() {
        when(referralRepository.existsActiveByStudentId(1L)).thenReturn(true);

        assertThat(referralService.isActiveForStudent(1L)).isTrue();
    }
}
