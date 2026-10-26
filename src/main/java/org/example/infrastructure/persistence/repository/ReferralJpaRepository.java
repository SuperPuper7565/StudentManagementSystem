package org.example.infrastructure.persistence.repository;

import org.example.infrastructure.persistence.entity.ReferralEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralJpaRepository extends JpaRepository<ReferralEntity, Long> {

    Optional<ReferralEntity> findByReferredId(Long referredId);

    List<ReferralEntity> findAllByOrderByCreatedAtDesc();

    @Query("select case when count(r) > 0 then true else false end from ReferralEntity r " +
            "where r.status = 'ACTIVE' " +
            "and (r.referrerId = :studentId or r.referredId = :studentId)")
    boolean existsActiveByStudentId(@Param("studentId") Long studentId);

    void deleteByReferredId(Long referredId);
}
