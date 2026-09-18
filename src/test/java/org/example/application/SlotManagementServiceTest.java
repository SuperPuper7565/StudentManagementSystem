package org.example.application;

import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotManagementServiceTest {

    @Mock
    private LessonSlotRepository slotRepository;

    @InjectMocks
    private SlotManagementService slotManagementService;

    @Test
    @DisplayName("Создание слота: проверка фиксации длительности на 90 минут")
    void createSlot_Success() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 15, 0);

        when(slotRepository.save(any(LessonSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonSlot slot = slotManagementService.createSlot(start);

        assertThat(slot).isNotNull();
        assertThat(slot.getStartTime()).isEqualTo(start);
        assertThat(slot.getEndTime()).isEqualTo(start.plusMinutes(90));
        assertThat(slot.isBooked()).isFalse();

        verify(slotRepository, times(1)).save(any(LessonSlot.class));
    }
}