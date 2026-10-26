package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.Referral;
import org.example.domain.model.ReferralStatus;
import org.example.domain.repository.BookingRepository;
import org.example.domain.repository.ReferralRepository;
import org.example.domain.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public Referral createReferral(Long referrerId, Long referredId) {
        validateDifferentStudents(referrerId, referredId);
        ensureStudentExists(referrerId, "Реферер");
        ensureStudentExists(referredId, "Приглашённый ученик");

        if (referralRepository.findByReferredId(referredId).isPresent()) {
            throw new IllegalStateException(
                    "Ученик уже участвует в реферальной программе как приглашённый"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        boolean alreadyAttended = bookingRepository.hasAttendedLesson(referredId);

        Referral referral = Referral.builder()
                .referrerId(referrerId)
                .referredId(referredId)
                .status(alreadyAttended ? ReferralStatus.ACTIVE : ReferralStatus.PENDING)
                .createdAt(now)
                .activatedAt(alreadyAttended ? now : null)
                .build();

        return referralRepository.save(referral);
    }

    @Transactional
    public void activateForAttendedStudent(Long studentId) {
        if (studentId == null) {
            return;
        }

        referralRepository.findByReferredId(studentId)
                .filter(referral -> referral.status() == ReferralStatus.PENDING)
                .ifPresent(referral -> referralRepository.save(Referral.builder()
                        .id(referral.id())
                        .referrerId(referral.referrerId())
                        .referredId(referral.referredId())
                        .status(ReferralStatus.ACTIVE)
                        .createdAt(referral.createdAt())
                        .activatedAt(LocalDateTime.now())
                        .build()));
    }

    public List<Referral> getAllReferrals() {
        return referralRepository.findAll();
    }

    @Transactional
    public Referral activateReferral(Long referredId) {
        Referral referral = getByReferredId(referredId);
        if (referral.status() == ReferralStatus.ACTIVE) {
            return referral;
        }
        return updateStatus(referral, ReferralStatus.ACTIVE, LocalDateTime.now());
    }

    @Transactional
    public Referral cancelReferral(Long referredId) {
        Referral referral = getByReferredId(referredId);
        return updateStatus(referral, ReferralStatus.CANCELLED, null);
    }

    @Transactional
    public void deleteReferral(Long referredId) {
        getByReferredId(referredId);
        referralRepository.deleteByReferredId(referredId);
    }

    public boolean isActiveForStudent(Long studentId) {
        if (studentId == null) {
            return false;
        }
        return referralRepository.existsActiveByStudentId(studentId);
    }

    private Referral getByReferredId(Long referredId) {
        return referralRepository.findByReferredId(referredId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Реферал не найден для ученика: " + referredId
                ));
    }

    private Referral updateStatus(
            Referral referral,
            ReferralStatus status,
            LocalDateTime activatedAt
    ) {
        return referralRepository.save(Referral.builder()
                .id(referral.id())
                .referrerId(referral.referrerId())
                .referredId(referral.referredId())
                .status(status)
                .createdAt(referral.createdAt())
                .activatedAt(activatedAt)
                .build());
    }

    private void validateDifferentStudents(Long referrerId, Long referredId) {
        if (referrerId == null || referredId == null) {
            throw new IllegalArgumentException(
                    "Нужно указать реферера и приглашённого ученика"
            );
        }
        if (referrerId.equals(referredId)) {
            throw new IllegalArgumentException("Ученик не может быть реферером сам себе");
        }
    }

    private void ensureStudentExists(Long studentId, String role) {
        studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        role + " не найден: " + studentId
                ));
    }
}
