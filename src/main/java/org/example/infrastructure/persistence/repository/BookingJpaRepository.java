package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.BookingEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"student", "slot"})
    Optional<BookingEntity> findById(Long id);

    @EntityGraph(attributePaths = {"student", "slot"})
    List<BookingEntity> findByStudentId(Long studentId);

    @Query(value = "select count(*) from bookings " +
            "where student_id = :studentId and is_trial = true and is_cancelled = false",
            nativeQuery = true)
    long countActiveTrialBookings(@Param("studentId") Long studentId);

    @Query(value = "select count(*) from bookings " +
            "where student_id = :studentId and is_attended = true",
            nativeQuery = true)
    long countAttendedBookings(@Param("studentId") Long studentId);
}
