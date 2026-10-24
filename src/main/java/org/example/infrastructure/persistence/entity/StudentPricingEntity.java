package org.example.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_pricing")
@Getter
@Setter
@NoArgsConstructor
public class StudentPricingEntity {

    @Id
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "individual_price", precision = 10, scale = 2)
    private BigDecimal individualPrice;

    private String reason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
