package com.examcore.controller;

import com.examcore.controller.AuthenticationController.AuthenticationResult;
import com.examcore.controller.AuthenticationController.Dashboard;
import com.examcore.controller.AuthenticationController.RegistrationResult;
import com.examcore.data.Database;
import com.examcore.model.Admin;
import com.examcore.model.Role;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationControllerTest {

    private Database database;
    private AuthenticationController auth;
    private Student student;
    private Teacher teacher;
    private Admin admin;

    @BeforeEach
    void setUp() {
        database = new Database();
        auth = new AuthenticationController(database);

        student = new Student("student1", "student1@test.com", "correctPassword");
        teacher = new Teacher("teacher1", "teacher1@test.com", "correctPassword");
        teacher.setVerified(true);
        admin = new Admin("admin1", "admin1@test.com", "correctPassword");
        database.saveUser(student);
        database.saveUser(teacher);
        database.saveUser(admin);
    }

    @Test
    void login_validStudentCredentials_succeedsAndRoutesToStudentDashboard() {
        AuthenticationResult result = auth.login("student1", "correctPassword");

        assertTrue(result.isSuccess());
        assertEquals(Dashboard.STUDENT_DASHBOARD, result.getDashboard().orElseThrow());
        assertEquals(student, result.getUser().orElseThrow());
        assertTrue(auth.isAuthenticated());
    }

    @Test
    void login_validTeacherCredentials_routesToTeacherDashboard() {
        AuthenticationResult result = auth.login("teacher1", "correctPassword");

        assertTrue(result.isSuccess());
        assertEquals(Dashboard.TEACHER_DASHBOARD, result.getDashboard().orElseThrow());
    }

    @Test
    void login_unverifiedTeacher_isRejected() {
        Teacher unverified = new Teacher("newteacher", "newteacher@test.com", "correctPassword");
        database.saveUser(unverified);

        AuthenticationResult result = auth.login("newteacher", "correctPassword");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().toLowerCase().contains("verif"));
        assertFalse(auth.isAuthenticated());
    }

    @Test
    void login_unverifiedTeacher_doesNotLeaveModelLoggedIn() {
        Teacher unverified = new Teacher("newteacher", "newteacher@test.com", "correctPassword");
        database.saveUser(unverified);

        auth.login("newteacher", "correctPassword");

        assertFalse(unverified.isLoggedIn());
    }

    @Test
    void login_teacherVerifiedAfterFailedAttempt_thenSucceeds() {
        Teacher unverified = new Teacher("newteacher", "newteacher@test.com", "correctPassword");
        database.saveUser(unverified);
        auth.login("newteacher", "correctPassword");

        unverified.setVerified(true);
        AuthenticationResult result = auth.login("newteacher", "correctPassword");

        assertTrue(result.isSuccess());
        assertEquals(Dashboard.TEACHER_DASHBOARD, result.getDashboard().orElseThrow());
    }

    @Test
    void login_validAdminCredentials_routesToAdminDashboard() {
        AuthenticationResult result = auth.login("admin1", "correctPassword");

        assertTrue(result.isSuccess());
        assertEquals(Dashboard.ADMIN_DASHBOARD, result.getDashboard().orElseThrow());
    }

    @Test
    void login_wrongPassword_fails() {
        AuthenticationResult result = auth.login("student1", "wrongPassword");

        assertFalse(result.isSuccess());
        assertTrue(result.getUser().isEmpty());
        assertFalse(auth.isAuthenticated());
    }

    @Test
    void login_unknownUsername_fails() {
        AuthenticationResult result = auth.login("nobody", "whatever");

        assertFalse(result.isSuccess());
    }

    @Test
    void login_unknownUsernameAndWrongPassword_giveIdenticalMessages() {
        AuthenticationResult unknownUser = auth.login("nobody", "whatever");
        AuthenticationResult wrongPassword = auth.login("student1", "wrongPassword");

        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
    }

    @Test
    void login_blankUsername_fails() {
        AuthenticationResult result = auth.login("", "correctPassword");

        assertFalse(result.isSuccess());
    }

    @Test
    void login_nullPassword_fails() {
        AuthenticationResult result = auth.login("student1", null);

        assertFalse(result.isSuccess());
    }

    @Test
    void login_emptyPassword_fails() {
        AuthenticationResult result = auth.login("student1", "");

        assertFalse(result.isSuccess());
    }

    @Test
    void login_deactivatedAccount_fails() {
        student.setActive(false);

        AuthenticationResult result = auth.login("student1", "correctPassword");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().toLowerCase().contains("deactivated"));
    }

    @Test
    void logout_clearsSessionAndModelLoginState() {
        auth.login("student1", "correctPassword");

        auth.logout();

        assertFalse(auth.isAuthenticated());
        assertTrue(auth.getCurrentSession().isEmpty());
        assertFalse(student.isLoggedIn());
    }

    @Test
    void logout_withoutActiveSession_doesNotThrow() {
        auth.logout();

        assertFalse(auth.isAuthenticated());
    }

    @Test
    void hasRole_matchesAuthenticatedUsersRole() {
        auth.login("teacher1", "correctPassword");

        assertTrue(auth.hasRole(Role.TEACHER));
        assertFalse(auth.hasRole(Role.STUDENT));
        assertFalse(auth.hasRole(Role.ADMIN));
    }

    @Test
    void hasRole_noSession_returnsFalse() {
        assertFalse(auth.hasRole(Role.STUDENT));
    }

    @Test
    void canAccessDashboard_matchesOwnRoleOnly() {
        auth.login("student1", "correctPassword");

        assertTrue(auth.canAccessDashboard(Dashboard.STUDENT_DASHBOARD));
        assertFalse(auth.canAccessDashboard(Dashboard.TEACHER_DASHBOARD));
        assertFalse(auth.canAccessDashboard(Dashboard.ADMIN_DASHBOARD));
    }

    @Test
    void canAccessDashboard_noSession_returnsFalse() {
        assertFalse(auth.canAccessDashboard(Dashboard.STUDENT_DASHBOARD));
    }

    @Test
    void requireDashboardAccess_wrongRole_throwsSecurityException() {
        auth.login("student1", "correctPassword");

        assertThrows(SecurityException.class, () -> auth.requireDashboardAccess(Dashboard.TEACHER_DASHBOARD));
    }

    @Test
    void requireDashboardAccess_noSession_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> auth.requireDashboardAccess(Dashboard.STUDENT_DASHBOARD));
    }

    @Test
    void requireDashboardAccess_correctRole_doesNotThrow() {
        auth.login("admin1", "correctPassword");

        assertDoesNotThrow(() -> auth.requireDashboardAccess(Dashboard.ADMIN_DASHBOARD));
    }


}
