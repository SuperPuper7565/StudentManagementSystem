package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.status.Active;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentRegistrationService {

    private final StudentRepository studentRepository;

    public Student registerStudent(CreateStudentCommand command) {
        Student student = Student.builder()
                .name(command.firstName())
                .surname(command.lastName())
                .subject(command.subject())
                .grade(command.grade())
                .goal(command.goal())
                .status(new Active(command.lessonPrice(), command.lessonFormat()))
                .build();

        return studentRepository.save(student);
    }
}