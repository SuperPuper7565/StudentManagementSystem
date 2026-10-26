package org.example.application;

import lombok.RequiredArgsConstructor;
import org.example.application.dto.CreateStudentCommand;
import org.example.domain.model.Student;
import org.example.domain.repository.StudentRepository;
import org.example.domain.repository.StudentSubjectRepository;
import org.example.domain.status.Active;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentRegistrationService {

    private final StudentRepository studentRepository;
    private final StudentSubjectRepository subjectRepository;

    @Transactional
    public Student registerStudent(CreateStudentCommand command) {
        Student student = Student.builder()
                .name(command.firstName())
                .surname(command.lastName())
                .subject(command.subject())
                .grade(command.grade())
                .goal(command.goal())
                .status(new Active(command.lessonFormat()))
                .build();

        Student saved = studentRepository.save(student);
        if (saved.subject() != null && !saved.subject().isBlank()) {
            subjectRepository.add(saved.id(), saved.subject().trim());
        }
        return saved;
    }
}