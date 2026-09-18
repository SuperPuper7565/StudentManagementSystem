package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SlotManagementService {

    private final LessonSlotRepository slotRepository;

    public LessonSlot createSlot(LocalDateTime startTime) {
        LessonSlot slot = new LessonSlot();
        slot.setStartTime(startTime); // Сеттер автоматически выставит +90 минут длительности
        slot.setBooked(false);

        return slotRepository.save(slot);
    }
}