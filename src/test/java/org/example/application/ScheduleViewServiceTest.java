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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleViewServiceTest {

    @Mock
    private LessonSlotRepository slotRepository;

    @InjectMocks
    private ScheduleViewService scheduleViewService;

    @Test
    @DisplayName("Получение доступных слотов расписания")
    void getAvailableSlots_Success() {
        LessonSlot slot1 = new LessonSlot();
        LessonSlot slot2 = new LessonSlot();

        when(slotRepository.findAvailableSlots(any(LocalDateTime.class)))
                .thenReturn(List.of(slot1, slot2));

        List<LessonSlot> available = scheduleViewService.getAvailableSlots();

        assertThat(available).hasSize(2);
        verify(slotRepository, times(1)).findAvailableSlots(any(LocalDateTime.class));
    }
}