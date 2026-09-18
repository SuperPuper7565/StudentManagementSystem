package org.example.domain.repository;

import org.example.domain.model.Booking;

import java.util.List;
import java.util.Optional;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(Long id);
    List<Booking> findByStudentId(Long studentId);
}