package org.example.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Referral;
import org.example.domain.model.ReferralStatus;
import org.example.domain.repository.ReferralRepository;
import org.example.infrastructure.persistence.entity.ReferralEntity;
import org.example.infrastructure.persistence.repository.ReferralJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReferralRepositoryImpl implements ReferralRepository {

    private final ReferralJpaRepository jpaRepository;

    @Override
    @Transactional
    public Referral save(Referral referral) {
        ReferralEntity saved = jpaRepository.save(toEntity(referral));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Referral> findByReferredId(Long referredId) {
        return jpaRepository.findByReferredId(referredId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Referral> findAll() {
        return jpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveByStudentId(Long studentId) {
        return jpaRepository.existsActiveByStudentId(studentId);
    }

    @Override
    @Transactional
    public void deleteByReferredId(Long referredId) {
        jpaRepository.deleteByReferredId(referredId);
    }

    private Referral toDomain(ReferralEntity entity) {
        return Referral.builder()
                .id(entity.getId())
                .referrerId(entity.getReferrerId())
                .referredId(entity.getReferredId())
                .status(ReferralStatus.valueOf(entity.getStatus()))
                .createdAt(entity.getCreatedAt())
                .activatedAt(entity.getActivatedAt())
                .build();
    }

    private ReferralEntity toEntity(Referral referral) {
        ReferralEntity entity = new ReferralEntity();
        if (referral.id() != null) {
            entity.setId(referral.id());
        }
        entity.setReferrerId(referral.referrerId());
        entity.setReferredId(referral.referredId());
        entity.setStatus(referral.status() != null
                ? referral.status().name()
                : ReferralStatus.PENDING.name());
        entity.setCreatedAt(referral.createdAt() != null
                ? referral.createdAt()
                : LocalDateTime.now());
        entity.setActivatedAt(referral.activatedAt());
        return entity;
    }
}
