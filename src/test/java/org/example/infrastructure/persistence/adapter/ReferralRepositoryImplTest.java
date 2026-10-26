package org.example.infrastructure.persistence.adapter;

import org.example.domain.model.Referral;
import org.example.domain.model.ReferralStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ReferralRepositoryImpl.class)
class ReferralRepositoryImplTest {

    @Autowired
    private ReferralRepositoryImpl repository;

    @Test
    @DisplayName("Реферал сохраняется, находится по приглашённому и активируется")
    void savesFindsAndActivatesReferral() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

        Referral saved = repository.save(Referral.builder()
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.PENDING)
                .createdAt(createdAt)
                .build());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(ReferralStatus.PENDING);
        assertThat(saved.activatedAt()).isNull();

        Referral found = repository.findByReferredId(2L).orElseThrow();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.referrerId()).isEqualTo(1L);
        assertThat(found.referredId()).isEqualTo(2L);

        assertThat(repository.existsActiveByStudentId(1L)).isFalse();
        assertThat(repository.existsActiveByStudentId(2L)).isFalse();

        LocalDateTime activatedAt = LocalDateTime.now();
        repository.save(Referral.builder()
                .id(found.id())
                .referrerId(found.referrerId())
                .referredId(found.referredId())
                .status(ReferralStatus.ACTIVE)
                .createdAt(found.createdAt())
                .activatedAt(activatedAt)
                .build());

        Referral activated = repository.findByReferredId(2L).orElseThrow();
        assertThat(activated.status()).isEqualTo(ReferralStatus.ACTIVE);
        assertThat(activated.activatedAt()).isNotNull();
        assertThat(repository.existsActiveByStudentId(1L)).isTrue();
        assertThat(repository.existsActiveByStudentId(2L)).isTrue();
        assertThat(repository.existsActiveByStudentId(3L)).isFalse();
    }

    @Test
    @DisplayName("Список рефералов сортируется по дате создания, реферал удаляется")
    void listsReferralsAndDeletesThem() {
        Referral older = repository.save(Referral.builder()
                .referrerId(1L)
                .referredId(2L)
                .status(ReferralStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build());
        Referral newer = repository.save(Referral.builder()
                .referrerId(1L)
                .referredId(3L)
                .status(ReferralStatus.CANCELLED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());

        List<Referral> referrals = repository.findAll();

        assertThat(referrals).extracting(Referral::id)
                .containsExactly(newer.id(), older.id());

        repository.deleteByReferredId(2L);

        assertThat(repository.findByReferredId(2L)).isEmpty();
        assertThat(repository.findByReferredId(3L)).isPresent();
    }
}
