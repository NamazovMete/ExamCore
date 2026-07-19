package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Role;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import com.examcore.model.User;

import java.util.Objects;
import java.util.Optional;

public class AuthenticationController {

    public enum Dashboard {
        STUDENT_DASHBOARD,
        TEACHER_DASHBOARD,
        ADMIN_DASHBOARD
    }

    public static final class AuthenticationResult {
        private final boolean success;
        private final String message;
        private final User user;
        private final Dashboard dashboard;

        private AuthenticationResult(boolean success, String message, User user, Dashboard dashboard) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.dashboard = dashboard;
        }

        static AuthenticationResult success(User user, Dashboard dashboard) {
            return new AuthenticationResult(true, "Login successful", user, dashboard);
        }

        static AuthenticationResult failure(String message) {
            return new AuthenticationResult(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Optional<User> getUser() {
            return Optional.ofNullable(user);
        }

        public Optional<Dashboard> getDashboard() {
            return Optional.ofNullable(dashboard);
        }
    }

    public static final class RegistrationResult {
        private final boolean success;
        private final String message;
        private final User user;

        private RegistrationResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        static RegistrationResult success(User user) {
            return new RegistrationResult(true, "Registration successful", user);
        }

        static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }


        public Optional<User> getUser() {
            return Optional.ofNullable(user);
        }
    }

    private final Database database;
    private User currentSession;

    public AuthenticationController() {
        this(Database.getInstance());
    }

    public AuthenticationController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }


    public synchronized AuthenticationResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return AuthenticationResult.failure("Username and password are required");
        }

        Optional<User> maybeUser = database.findUserByUsername(username);
        if (maybeUser.isEmpty()) {
            return AuthenticationResult.failure("Invalid username or password");
        }

        User user = maybeUser.get();
        if (!user.isActive()) {
            return AuthenticationResult.failure("This account has been deactivated. Contact an administrator.");
        }

        if (!user.login(password)) {
            return AuthenticationResult.failure("Invalid username or password");
        }

        if (user instanceof Teacher teacher && !teacher.isVerified()) {
            user.logout();
            return AuthenticationResult.failure(
                    "Your teacher account is awaiting admin verification. Please try again once an admin has approved it.");
        }

        currentSession = user;
        return AuthenticationResult.success(user, routeToDashboard(user.getRole()));
    }

    public synchronized RegistrationResult register(String username, String email, String password, Role role) {
        if (username == null || username.isBlank()) {
            return RegistrationResult.failure("Username is required");
        }
        if (email == null || email.isBlank()) {
            return RegistrationResult.failure("Email is required");
        }
        if (password == null || password.length() < 6) {
            return RegistrationResult.failure("Password must be at least 6 characters");
        }
        if (role != Role.STUDENT && role != Role.TEACHER) {
            return RegistrationResult.failure("Self-registration is only available for Student or Teacher accounts");
        }
        if (database.findUserByUsername(username).isPresent()) {
            return RegistrationResult.failure("Username is already taken");
        }

        User user = role == Role.STUDENT
                ? new Student(username, email, password)
                : new Teacher(username, email, password);
        database.saveUser(user);
        database.logSystemEvent("Registered new " + role.name().toLowerCase() + " account: " + username);
        return RegistrationResult.success(user);
    }

    public synchronized void logout() {
        if (currentSession != null) {
            currentSession.logout();
            currentSession = null;
        }
    }

    public synchronized Optional<User> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    public synchronized boolean isAuthenticated() {
        return currentSession != null && currentSession.isLoggedIn();
    }

    public synchronized boolean hasRole(Role role) {
        return isAuthenticated() && currentSession.getRole() == role;
    }

    public synchronized boolean canAccessDashboard(Dashboard dashboard) {
        if (!isAuthenticated() || dashboard == null) {
            return false;
        }
        return routeToDashboard(currentSession.getRole()) == dashboard;
    }

    public synchronized void requireDashboardAccess(Dashboard dashboard) {
        Objects.requireNonNull(dashboard, "Dashboard cannot be null");
        if (!isAuthenticated()) {
            throw new SecurityException("No authenticated session");
        }
        if (!canAccessDashboard(dashboard)) {
            throw new SecurityException(
                    "Access denied: role " + currentSession.getRole() + " cannot access " + dashboard);
        }
    }

    private Dashboard routeToDashboard(Role role) {
        return switch (role) {
            case STUDENT -> Dashboard.STUDENT_DASHBOARD;
            case TEACHER -> Dashboard.TEACHER_DASHBOARD;
            case ADMIN -> Dashboard.ADMIN_DASHBOARD;
        };
    }
}
