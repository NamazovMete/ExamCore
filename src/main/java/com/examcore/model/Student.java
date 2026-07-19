package com.examcore.model;

public class Student extends User {

    private int examScore;
    private int quizScore;
    private String grade;

    public Student(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.STUDENT);
    }

    private Student(String username, String email, String passwordHash, boolean isActive) {
        super(username, email, passwordHash, Role.STUDENT, PasswordEncoding.HASHED);
        setActive(isActive);
    }

    public static Student restoreFromStorage(String username, String email, String passwordHash, boolean isActive,
                                              String firstName, String middleName, String lastName, String school,
                                              String department, String profilePicturePath,
                                              int examScore, int quizScore, String grade) {
        Student student = new Student(username, email, passwordHash, isActive);
        student.setFirstName(firstName);
        student.setMiddleName(middleName);
        student.setLastName(lastName);
        student.setSchool(school);
        student.setDepartment(department);
        student.setProfilePicturePath(profilePicturePath);
        student.examScore = examScore;
        student.quizScore = quizScore;
        student.grade = grade;
        return student;
    }

    public int getExamScore() {
        return examScore;
    }

    public void addExamScore(int points) {
        this.examScore += points;
        touch();
    }

    public int getQuizScore() {
        return quizScore;
    }

    public void addQuizScore(int points) {
        this.quizScore += points;
        touch();
    }

    public int getTotalScore() {
        return examScore + quizScore;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
        touch();
    }
}
