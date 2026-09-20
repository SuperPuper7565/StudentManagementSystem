package org.example.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String subject;

    private Integer grade;

    private String goal;

    // Поля для сохранения Sealed Status
    @Column(nullable = false)
    private String statusType; // ACTIVE, PAUSED, GRADUATED

    private BigDecimal lessonPrice;

    private String lessonFormat;

    private String pauseReason;

    private LocalDate pauseStartDate;

    private LocalDate pauseEndDate;

    private LocalDate graduationDate;

    private Integer examResult;
}