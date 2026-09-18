package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleViewService {

    private final LessonSlotRepository slotRepository;

    public List<LessonSlot> getAvailableSlots() {
        return slotRepository.findAvailableSlots(LocalDateTime.now());
    }

    public List<LessonSlot> getSlotsForWeek(LocalDateTime startOfWeek, LocalDateTime endOfWeek) {
        return slotRepository.findSlotsBetween(startOfWeek, endOfWeek);
    }
}