package org.example.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminKeyGuard {

    private final String adminKey;

    public AdminKeyGuard(@Value("${admin.api-key:}") String adminKey) {
        this.adminKey = adminKey == null ? "" : adminKey.trim();
    }

    public void check(String providedKey) {
        if (adminKey.isBlank()) {
            throw new AdminAccessDeniedException(
                    "Административные операции отключены: не задан ADMIN_API_KEY"
            );
        }

        byte[] expected = adminKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedKey == null
                ? new byte[0]
                : providedKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AdminAccessDeniedException("Неверный административный ключ");
        }
    }
}
