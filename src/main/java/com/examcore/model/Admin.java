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
