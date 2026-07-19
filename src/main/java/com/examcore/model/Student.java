package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Student extends User {

    private int examScore;
    private int quizScore;
    private final ActivityLog activityLog = new ActivityLog();
    private final List<Test> assignedExams = new ArrayList<>();
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

    public Submission takeExam(Test test) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        test.startTimer();
        touch();
        return new Submission(this, test);
    }

    public LeaderBoard viewLeaderBoard(LeaderBoard leaderBoard) {
        return leaderBoard;
    }

    public void submitFeedback(Test test, String text) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Feedback text cannot be blank");
        }
        touch();
    }

    public void assignTest(Test test) {
        if (test != null && !assignedExams.contains(test)) {
            assignedExams.add(test);
            touch();
        }
    }

    public List<Test> getAssignedExams() {
        return Collections.unmodifiableList(assignedExams);
    }

    public ActivityLog getActivityLog() {
        return activityLog;
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
