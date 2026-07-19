package com.examcore.model;

public class Teacher extends User {

    private boolean isVerified;
    
    public Teacher(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.TEACHER);
        this.isVerified = false;
    }


    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
        touch();
    }
}
