package org.example;

public enum StudyGoal {
    OGE("Подготовка к ОГЭ"),
    EGE("Подготовка к ЕГЭ"),
    OTHER("Повышение успеваемости / Другое");

    private final String description;

    StudyGoal(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
