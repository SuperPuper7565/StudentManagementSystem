package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.LessonSlot;
import org.example.domain.repository.LessonSlotRepository;
import org.example.infrastructure.persistence.entity.LessonSlotEntity;
import org.example.infrastructure.persistence.repository.LessonSlotJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LessonSlotRepositoryImpl implements LessonSlotRepository {

    private final LessonSlotJpaRepository jpaRepository;

    @Override
    public LessonSlot save(LessonSlot slot) {
        LessonSlotEntity entity = toEntity(slot);
        LessonSlotEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<LessonSlot> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<LessonSlot> findAvailableSlots(LocalDateTime now) {
        return jpaRepository.findByBookedFalseAndStartTimeAfter(now).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<LessonSlot> findSlotsBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByStartTimeBetween(start, end).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsOverlappingSlot(LocalDateTime startTime, LocalDateTime endTime) {
        return jpaRepository.existsOverlappingSlot(startTime, endTime);
    }

    private LessonSlot toDomain(LessonSlotEntity entity) {
        LessonSlot slot = new LessonSlot();
        slot.setId(entity.getId());
        slot.setStartTime(entity.getStartTime());
        slot.setBooked(entity.isBooked());
        return slot;
    }

    private LessonSlotEntity toEntity(LessonSlot domain) {
        LessonSlotEntity entity = new LessonSlotEntity();
        entity.setId(domain.getId());
        entity.setStartTime(domain.getStartTime());
        entity.setEndTime(domain.getEndTime());
        entity.setBooked(domain.isBooked());
        return entity;
    }
}