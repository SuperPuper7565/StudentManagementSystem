package org.example.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.application.PricingService;
import org.example.application.dto.PriceQuote;
import org.example.application.dto.UpdateStudentPricingCommand;
import org.example.infrastructure.security.AdminKeyGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Цены", description = "Расчёт стоимости занятий и индивидуальные цены")
public class PricingController {

    private final PricingService pricingService;
    private final AdminKeyGuard adminKeyGuard;

    @GetMapping("/{studentId}/price")
    @Operation(summary = "Получить актуальную стоимость занятия для ученика")
    public ResponseEntity<PriceQuote> getPrice(@PathVariable Long studentId) {
        return ResponseEntity.ok(pricingService.quote(studentId));
    }

    @PutMapping("/{studentId}/pricing")
    @Operation(summary = "Назначить индивидуальную цену ученику (только администратор)")
    public ResponseEntity<Void> setIndividualPrice(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestBody UpdateStudentPricingCommand command
    ) {
        adminKeyGuard.check(adminKey);
        if (command == null) {
            throw new IllegalArgumentException("Не передана индивидуальная цена");
        }
        pricingService.setIndividualPrice(studentId, command.individualPrice(), command.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{studentId}/pricing")
    @Operation(summary = "Убрать индивидуальную цену ученика (только администратор)")
    public ResponseEntity<Void> clearIndividualPrice(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey
    ) {
        adminKeyGuard.check(adminKey);
        pricingService.clearIndividualPrice(studentId);
        return ResponseEntity.noContent().build();
    }
}
