package org.example.infrastructure.persistence.adapter;

import org.example.domain.model.StudentPricing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(StudentPricingRepositoryImpl.class)
class StudentPricingRepositoryImplTest {

    @Autowired
    private StudentPricingRepositoryImpl repository;

    @Test
    @DisplayName("Сохранение и чтение индивидуальной цены")
    void saveAndFindIndividualPrice() {
        repository.save(new StudentPricing(1L, new BigDecimal("1500.00"), "по знакомству"));

        StudentPricing found = repository.findById(1L).orElseThrow();

        assertThat(found.studentId()).isEqualTo(1L);
        assertThat(found.individualPrice()).isEqualByComparingTo("1500.00");
        assertThat(found.reason()).isEqualTo("по знакомству");
    }

    @Test
    @DisplayName("Удаление индивидуальной цены")
    void deleteIndividualPrice() {
        repository.save(new StudentPricing(2L, new BigDecimal("1800.00"), null));

        repository.deleteById(2L);

        assertThat(repository.findById(2L)).isEmpty();
    }
}
