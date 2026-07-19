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

    public void verifyTeacher(Teacher teacher) {
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher cannot be null");
        }
        teacher.setVerified(true);
        systemLogs.add(LocalDateTime.now() + " - Verified teacher " + teacher.getUsername());
        touch();
    }

    public void deactivateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        user.setActive(false);
        systemLogs.add(LocalDateTime.now() + " - Deactivated user " + user.getUsername());
        touch();
    }

    public void reactivateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        user.setActive(true);
        systemLogs.add(LocalDateTime.now() + " - Reactivated user " + user.getUsername());
        touch();
    }

    public List<String> viewSystemLogs() {
        return Collections.unmodifiableList(systemLogs);
    }

    public int getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
        touch();
    }
}
