package org.example.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Фиксированный справочник предметов.
 *
 * <p>Код используется в API и callback-данных бота, отображаемое имя — в интерфейсе
 * и в существующих строковых полях базы данных.</p>
 */
public enum Subject {
    MATH("MATH", "Математика"),
    INFORMATICS("INFORMATICS", "Информатика");

    private final String code;
    private final String displayName;

    Subject(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<Subject> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(subject -> subject.code.equalsIgnoreCase(normalized)
                        || subject.displayName.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static Subject parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Предмет не может быть пустым. Доступны: " + availableNames()
            );
        }
        return tryParse(raw).orElseThrow(() -> new IllegalArgumentException(
                "Неизвестный предмет: " + raw.trim()
                        + ". Доступны: " + availableNames()
        ));
    }

    public static String availableNames() {
        return String.join(", ", Arrays.stream(values())
                .map(Subject::getDisplayName)
                .toList());
    }
}
