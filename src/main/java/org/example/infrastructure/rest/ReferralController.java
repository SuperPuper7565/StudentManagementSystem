package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.ReferralService;
import org.example.domain.model.Referral;
import org.example.infrastructure.security.AdminKeyGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
@Tag(name = "Рефералы", description = "Реферальная программа: приглашения и скидка 1800 ₽")
public class ReferralController {

    private final ReferralService referralService;
    private final AdminKeyGuard adminKeyGuard;

    @PostMapping
    @Operation(summary = "Создать реферал (только преподаватель)")
    public ResponseEntity<Referral> createReferral(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestParam Long referrerId,
            @RequestParam Long referredId
    ) {
        adminKeyGuard.check(adminKey);
        Referral referral = referralService.createReferral(referrerId, referredId);
        return ResponseEntity.status(HttpStatus.CREATED).body(referral);
    }

    @GetMapping
    @Operation(summary = "Список рефералов (только преподаватель)")
    public ResponseEntity<List<Referral>> getReferrals(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        adminKeyGuard.check(adminKey);
        return ResponseEntity.ok(referralService.getAllReferrals());
    }

    @PostMapping("/{referredId}/activate")
    @Operation(summary = "Активировать реферал вручную (только преподаватель)")
    public ResponseEntity<Referral> activateReferral(
            @PathVariable Long referredId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        adminKeyGuard.check(adminKey);
        return ResponseEntity.ok(referralService.activateReferral(referredId));
    }

    @PostMapping("/{referredId}/cancel")
    @Operation(summary = "Отменить реферал вручную (только преподаватель)")
    public ResponseEntity<Referral> cancelReferral(
            @PathVariable Long referredId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        adminKeyGuard.check(adminKey);
        return ResponseEntity.ok(referralService.cancelReferral(referredId));
    }

    @DeleteMapping("/{referredId}")
    @Operation(summary = "Удалить реферал (только преподаватель)")
    public ResponseEntity<Void> deleteReferral(
            @PathVariable Long referredId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        adminKeyGuard.check(adminKey);
        referralService.deleteReferral(referredId);
        return ResponseEntity.noContent().build();
    }
}
