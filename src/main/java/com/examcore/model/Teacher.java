package com.examcore.model;

public class Teacher extends User {

    private boolean isVerified;
    
    public Teacher(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.TEACHER);
        this.isVerified = false;
    }

    private Teacher(String username, String email, String passwordHash, boolean isActive, boolean isVerified) {
        super(username, email, passwordHash, Role.TEACHER, PasswordEncoding.HASHED);
        setActive(isActive);
        this.isVerified = isVerified;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
        touch();
    }
}
