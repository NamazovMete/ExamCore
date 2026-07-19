package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Admin;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminControllerTest {

    private Database database;
    private AdminController controller;
    private Admin admin;
    private Teacher teacher;
    private Student student;

    @BeforeEach
    void setUp() {
        database = new Database();
        controller = new AdminController(database);
        admin = new Admin("admin1", "admin1@test.com", "password123");
        teacher = new Teacher("teacher1", "teacher1@test.com", "password123");
        student = new Student("student1", "student1@test.com", "password123");
        database.saveUser(admin);
        database.saveUser(teacher);
        database.saveUser(student);
    }

    @Test
    void verifyTeacher_marksTeacherVerified() {
        controller.verifyTeacher(admin, teacher);

        assertTrue(teacher.isVerified());
        assertEquals(1, admin.viewSystemLogs().size());
    }

    @Test
    void verifyTeacher_nullTeacher_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.verifyTeacher(admin, null));
    }

    @Test
    void verifyTeacher_nullAdmin_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.verifyTeacher(null, teacher));
    }

    @Test
    void deactivateUser_marksUserInactive() {
        controller.deactivateUser(admin, student);

        assertFalse(student.isActive());
    }

    @Test
    void reactivateUser_marksUserActive() {
        controller.deactivateUser(admin, student);

        controller.reactivateUser(admin, student);

        assertTrue(student.isActive());
    }

    @Test
    void reactivateUser_nullUser_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.reactivateUser(admin, null));
    }

    @Test
    void getPendingTeachers_returnsOnlyUnverifiedTeachers() {
        Teacher verifiedTeacher = new Teacher("teacher2", "teacher2@test.com", "password123");
        verifiedTeacher.setVerified(true);
        database.saveUser(verifiedTeacher);

        List<Teacher> pending = controller.getPendingTeachers();

        assertTrue(pending.contains(teacher));
        assertFalse(pending.contains(verifiedTeacher));
    }

    @Test
    void getAllUsers_includesEveryRole() {
        List<?> allUsers = controller.getAllUsers();

        assertTrue(allUsers.contains(admin));
        assertTrue(allUsers.contains(teacher));
        assertTrue(allUsers.contains(student));
    }

    @Test
    void constructor_nullDatabase_throws() {
        assertThrows(IllegalArgumentException.class, () -> new AdminController(null));
    }
}
