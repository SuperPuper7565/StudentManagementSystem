package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.SlotConflictDto;
import org.example.application.dto.SlotOverlapResponse;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotManagementService {

    private final LessonSlotRepository slotRepository;

    @Transactional
    public LessonSlot createSlot(LocalDateTime startTime) {
        return createSlot(startTime, LessonFormat.ONLINE);
    }

    @Transactional
    public LessonSlot createSlot(LocalDateTime startTime, LessonFormat format) {
        LessonFormat effectiveFormat = format == null ? LessonFormat.ONLINE : format;

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
        slot.setFormat(effectiveFormat);

        return slotRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public SlotOverlapResponse checkOverlap(LocalDateTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Время начала занятия обязательно");
        }

        LocalDateTime endTime = startTime.plusMinutes(90);

        List<SlotConflictDto> conflicts = slotRepository
                .findOverlappingSlots(startTime, endTime)
                .stream()
                .map(slot -> new SlotConflictDto(
                        slot.getId(),
                        slot.getStartTime(),
                        slot.getEndTime(),
                        slot.getFormat(),
                        slot.isBooked()))
                .toList();

        return new SlotOverlapResponse(startTime, endTime, !conflicts.isEmpty(), conflicts);
    }
}