package com.examcore.model;

import com.examcore.util.PasswordUtil;

public abstract class User extends BaseEntity {

    private String username;
    private String email;
    private String passwordHash;
    private boolean isActive;
    private final Role role;
    private String firstName;
    private String middleName;
    private String lastName;
    private String school;
    private String department;
    private String profilePicturePath;
    private boolean loggedIn;

    protected User(String username, String email, String plainTextPassword, Role role) {
        super();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        this.username = username;
        this.email = email;
        this.passwordHash = PasswordUtil.hash(plainTextPassword);
        this.role = role;
        this.isActive = true;
        this.loggedIn = false;
    }

    public boolean login(String plainTextPassword) {
        if (!isActive) {
            return false;
        }
        boolean matches = PasswordUtil.matches(plainTextPassword, passwordHash);
        if (matches) {
            loggedIn = true;
            touch();
        }
        return matches;
    }

    public void logout() {
        loggedIn = false;
        touch();
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        touch();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
        touch();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePassword(String newPlainTextPassword) {
        this.passwordHash = PasswordUtil.hash(newPlainTextPassword);
        touch();
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
        touch();
    }

    public Role getRole() {
        return role;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        touch();
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
        touch();
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        touch();
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
        touch();
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
        touch();
    }

    public String getProfilePicturePath() {
        return profilePicturePath;
    }

    public void setProfilePicturePath(String profilePicturePath) {
        this.profilePicturePath = profilePicturePath;
        touch();
    }
}
