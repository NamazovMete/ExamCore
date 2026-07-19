package com.examcore.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Admin extends User {

    private int adminLevel;
    private final List<String> systemLogs = new ArrayList<>();

    public Admin(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.ADMIN);
        this.adminLevel = 1;
    }

    private Admin(String username, String email, String passwordHash, boolean isActive, int adminLevel) {
        super(username, email, passwordHash, Role.ADMIN, PasswordEncoding.HASHED);
        setActive(isActive);
        this.adminLevel = adminLevel;
    }

    public static Admin restoreFromStorage(String username, String email, String passwordHash, boolean isActive,
                                            String firstName, String middleName, String lastName, String school,
                                            String department, String profilePicturePath, int adminLevel) {
        Admin admin = new Admin(username, email, passwordHash, isActive, adminLevel);
        admin.setFirstName(firstName);
        admin.setMiddleName(middleName);
        admin.setLastName(lastName);
        admin.setSchool(school);
        admin.setDepartment(department);
        admin.setProfilePicturePath(profilePicturePath);
        return admin;
    }

    public void verifyTeacher(Teacher teacher) {
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher cannot be null");
        }
        teacher.setVerified(true);
        recordLog("Verified teacher " + teacher.getUsername());
    }

    public void deactivateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        user.setActive(false);
        recordLog("Deactivated user " + user.getUsername());
    }

    public void reactivateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        user.setActive(true);
        recordLog("Reactivated user " + user.getUsername());
    }

    private void recordLog(String action) {
        systemLogs.add(LocalDateTime.now() + " - " + action);
        touch();
    }

    public List<String> viewSystemLogs() {
        return Collections.unmodifiableList(systemLogs);
    }

    public void loadSystemLogEntries(List<String> entries) {
        systemLogs.clear();
        if (entries != null) {
            systemLogs.addAll(entries);
        }
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
        touch();
    }
}
