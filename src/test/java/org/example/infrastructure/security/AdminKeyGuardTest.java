package org.example.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminKeyGuardTest {

    @Test
    @DisplayName("Верный административный ключ пропускает операцию")
    void check_AcceptsCorrectKey() {
        AdminKeyGuard guard = new AdminKeyGuard("secret");

        assertThatCode(() -> guard.check("secret")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Неверный ключ запрещает операцию")
    void check_RejectsWrongKey() {
        AdminKeyGuard guard = new AdminKeyGuard("secret");

        assertThatThrownBy(() -> guard.check("wrong"))
                .isInstanceOf(AdminAccessDeniedException.class)
                .hasMessageContaining("Неверный");
    }

    @Test
    @DisplayName("Если ключ не настроен, административные операции отключены")
    void check_RejectsWhenKeyIsNotConfigured() {
        AdminKeyGuard guard = new AdminKeyGuard("");

        assertThatThrownBy(() -> guard.check(""))
                .isInstanceOf(AdminAccessDeniedException.class)
                .hasMessageContaining("не задан");
    }
}
