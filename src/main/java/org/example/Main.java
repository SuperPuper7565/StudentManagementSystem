package org.example;

import org.example.application.StudentService;
import org.example.application.StudentServiceImpl;
import org.example.application.StudentStatusFormatter;
import org.example.application.dto.StudentAdminDTO;
import org.example.domain.model.LessonFormat;
import org.example.domain.model.Student;
import org.example.domain.model.StudyGoal;
import org.example.domain.status.Active;
import org.example.domain.status.Graduated;
import org.example.domain.status.Paused;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        List<Student> students = List.of(
                new Student(1L, "Иван", "Иванов", "Математика", 11,
                        StudyGoal.EGE,
                        new Active(new BigDecimal("1500.00"), LessonFormat.ONLINE)),
                new Student(2L, "Анна", "Петрова", "Физика", 9,
                        StudyGoal.OGE,
                        new Paused("Каникулы", LocalDate.now(), LocalDate.now().plusWeeks(2))),
                new Student(3L, "Сергей", "Сидоров", "Информатика", 8,
                        StudyGoal.OTHER,
                        new Graduated(LocalDate.of(2025, 6, 25), 95))
        );

        // Связываем компоненты через интерфейсы
        StudentStatusFormatter formatter = new StudentStatusFormatter();
        StudentService service = new StudentServiceImpl(formatter);

        List<StudentAdminDTO> adminList = service.prepareForAdminPanel(students);
        adminList.forEach(System.out::println);
    }
}