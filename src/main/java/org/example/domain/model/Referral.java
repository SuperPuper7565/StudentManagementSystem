package org.example.domain.model;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record Referral(
        Long id,
        Long referrerId,
        Long referredId,
        ReferralStatus status,
        LocalDateTime createdAt,
        LocalDateTime activatedAt
) {

    public boolean isActive() {
        return status == ReferralStatus.ACTIVE;
    }
}
