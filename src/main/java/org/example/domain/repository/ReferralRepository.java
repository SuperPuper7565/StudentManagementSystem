package org.example.domain.repository;

import org.example.domain.model.Referral;

import java.util.List;
import java.util.Optional;

public interface ReferralRepository {

    Referral save(Referral referral);

    Optional<Referral> findByReferredId(Long referredId);

    List<Referral> findAll();

    boolean existsActiveByStudentId(Long studentId);

    void deleteByReferredId(Long referredId);
}
