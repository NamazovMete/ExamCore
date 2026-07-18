package com.examcore.model;

public class Student extends User {

    private String grade;

    public Student(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.STUDENT);
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
        touch();
    }
}