package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Classroom;
import com.examcore.model.Exam;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomControllerTest {

    private Database database;
    private ClassroomController controller;
    private Teacher teacher;
    private Student student;

    @BeforeEach
    void setUp() {
        database = new Database();
        controller = new ClassroomController(database);
        teacher = new Teacher("teacher1", "teacher1@test.com", "password123");
        student = new Student("student1", "student1@test.com", "password123");
    }

    @Test
    void createClassroom_persistsAndAssignsToTeacher() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");

        assertTrue(database.findClassroomById("CS999-1").isPresent());
        assertTrue(teacher.getManagedClassrooms().contains(classroom));
    }

    @Test
    void createClassroom_duplicateId_throws() {
        controller.createClassroom(teacher, "CS999-1", "CS999-1");

        assertThrows(IllegalStateException.class, () -> controller.createClassroom(teacher, "CS999-1", "CS999-1"));
    }

    @Test
    void createClassroom_blankClassId_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createClassroom(teacher, "  ", "Name"));
    }

    @Test
    void createClassroom_nullTeacher_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createClassroom(null, "CS999-1", "Name"));
    }

    @Test
    void addStudent_enrollsStudentInClassroom() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");

        controller.addStudent(teacher, classroom, student);

        assertTrue(classroom.getStudentList().contains(student));
        assertEquals(1, classroom.getParticipantCount());
    }

    @Test
    void addStudent_nullArgs_throws() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");

        assertThrows(IllegalArgumentException.class, () -> controller.addStudent(teacher, classroom, null));
    }

    @Test
    void removeStudent_removesFromClassroom() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");
        controller.addStudent(teacher, classroom, student);

        controller.removeStudent(classroom, student);

        assertFalse(classroom.getStudentList().contains(student));
        assertEquals(0, classroom.getParticipantCount());
    }

    @Test
    void assignTest_addsToClassroomAndEnrolledStudents() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");
        controller.addStudent(teacher, classroom, student);
        Exam exam = new Exam("EXAM-X", "Exam X", 60);

        controller.assignTest(teacher, exam, classroom);

        assertTrue(classroom.getAssignedExams().contains(exam));
        assertTrue(student.getAssignedExams().contains(exam));
    }

    @Test
    void assignTest_nullTest_throws() {
        Classroom classroom = controller.createClassroom(teacher, "CS999-1", "CS999-1");

        assertThrows(IllegalArgumentException.class, () -> controller.assignTest(teacher, null, classroom));
    }

    @Test
    void constructor_nullDatabase_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ClassroomController(null));
    }
}
