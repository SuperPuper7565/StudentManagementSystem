package org.example.application;

import org.example.application.dto.SlotOverlapResponse;
import org.example.domain.model.LessonFormat;
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
class SlotManagementServiceTest {

    @Mock
    private LessonSlotRepository slotRepository;

    @InjectMocks
    private SlotManagementService slotManagementService;

    @Test
    @DisplayName("Создание слота: проверка фиксации длительности на 90 минут")
    void createSlot_Success() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(15).withMinute(0).withSecond(0).withNano(0);

        when(slotRepository.save(any(LessonSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonSlot slot = slotManagementService.createSlot(start);

        assertThat(slot).isNotNull();
        assertThat(slot.getStartTime()).isEqualTo(start);
        assertThat(slot.getEndTime()).isEqualTo(start.plusMinutes(90));
        assertThat(slot.isBooked()).isFalse();
        assertThat(slot.getFormat()).isEqualTo(LessonFormat.ONLINE);

        verify(slotRepository, times(1)).save(any(LessonSlot.class));
    }

    @Test
    @DisplayName("Создание слота: преподаватель может выбрать офлайн-формат")
    void createSlot_WithOfflineFormat() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0);

        when(slotRepository.save(any(LessonSlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonSlot slot = slotManagementService.createSlot(start, LessonFormat.OFFLINE);

        assertThat(slot.getFormat()).isEqualTo(LessonFormat.OFFLINE);
        verify(slotRepository).save(any(LessonSlot.class));
    }

    @Test
    @DisplayName("Проверка пересечения: возвращает конкретный конфликтующий слот")
    void checkOverlap_ReturnsConflict() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 14, 0);

        LocalDateTime conflictStart = LocalDateTime.of(2026, 10, 25, 13, 30);
        LessonSlot conflicting = LessonSlot.builder()
                .id(12L)
                .startTime(conflictStart)
                .endTime(conflictStart.plusMinutes(90))
                .isBooked(true)
                .format(LessonFormat.OFFLINE)
                .build();

        when(slotRepository.findOverlappingSlots(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(conflicting));

        SlotOverlapResponse response = slotManagementService.checkOverlap(start);

        assertThat(response.overlap()).isTrue();
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(90));
        assertThat(response.conflicts()).hasSize(1);
        assertThat(response.conflicts().get(0).id()).isEqualTo(12L);
        assertThat(response.conflicts().get(0).startTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 25, 13, 30));
        assertThat(response.conflicts().get(0).endTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 25, 15, 0));
        assertThat(response.conflicts().get(0).format()).isEqualTo(LessonFormat.OFFLINE);
        assertThat(response.conflicts().get(0).booked()).isTrue();

        verify(slotRepository).findOverlappingSlots(start, start.plusMinutes(90));
    }

    @Test
    @DisplayName("Проверка пересечения: свободное время не даёт конфликтов")
    void checkOverlap_ReturnsNoConflicts() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 16, 0);

        when(slotRepository.findOverlappingSlots(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        SlotOverlapResponse response = slotManagementService.checkOverlap(start);

        assertThat(response.overlap()).isFalse();
        assertThat(response.conflicts()).isEmpty();
        assertThat(response.endTime()).isEqualTo(start.plusMinutes(90));
    }
}