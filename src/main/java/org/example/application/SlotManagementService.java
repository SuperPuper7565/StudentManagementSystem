package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SlotManagementService {

    private final LessonSlotRepository slotRepository;

    @Transactional
    public LessonSlot createSlot(LocalDateTime startTime) {

        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Нельзя создать слот на прошедшее время");
        }

        LocalDateTime endTime = startTime.plusMinutes(90);

        boolean isOverlapping = slotRepository.existsOverlappingSlot(startTime, endTime);
        if (isOverlapping) {
            throw new IllegalArgumentException("Слот пересекается по времени с уже существующим занятием!");
        }

        LessonSlot slot = new LessonSlot();
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setBooked(false);

        return slotRepository.save(slot);
    }
}