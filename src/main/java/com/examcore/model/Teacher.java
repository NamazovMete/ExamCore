package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Teacher extends User {

    private boolean isVerified;
    private final List<Classroom> managedClassrooms = new ArrayList<>();

    public Teacher(String username, String email, String plainTextPassword) {
        super(username, email, plainTextPassword, Role.TEACHER);
        this.isVerified = false;
    }

    private Teacher(String username, String email, String passwordHash, boolean isActive, boolean isVerified) {
        super(username, email, passwordHash, Role.TEACHER, PasswordEncoding.HASHED);
        setActive(isActive);
        this.isVerified = isVerified;
    }

    /** Rebuilds a Teacher from persisted state without re-hashing the already-hashed password. */
    public static Teacher restoreFromStorage(String username, String email, String passwordHash, boolean isActive,
                                              String firstName, String middleName, String lastName, String school,
                                              String department, String profilePicturePath, boolean isVerified) {
        Teacher teacher = new Teacher(username, email, passwordHash, isActive, isVerified);
        teacher.setFirstName(firstName);
        teacher.setMiddleName(middleName);
        teacher.setLastName(lastName);
        teacher.setSchool(school);
        teacher.setDepartment(department);
        teacher.setProfilePicturePath(profilePicturePath);
        return teacher;
    }

    public Exam createExam(String title, int durationLimitMinutes) {
        Exam exam = new Exam("EXAM-" + UUID.randomUUID().toString().substring(0, 8), title, durationLimitMinutes);
        touch();
        return exam;
    }

    public Quiz createQuiz(String title, int durationLimitMinutes, String topic) {
        Quiz quiz = new Quiz("QUIZ-" + UUID.randomUUID().toString().substring(0, 8), title, durationLimitMinutes, topic);
        touch();
        return quiz;
    }

    public void assignExam(Test exam, Classroom classroom) {
        if (exam == null || classroom == null) {
            throw new IllegalArgumentException("Exam and classroom cannot be null");
        }
        classroom.assignTest(exam);
        touch();
    }

    public void addStudentToClass(Student student, Classroom classroom) {
        if (student == null || classroom == null) {
            throw new IllegalArgumentException("Student and classroom cannot be null");
        }
        classroom.addStudent(student);
        touch();
    }

    public AnalyticsReport viewAnalytics(Test test, List<Submission> submissions) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        List<Submission> graded = submissions == null ? List.of() : submissions.stream()
                .filter(s -> s.getTest().equals(test) && s.isGraded())
                .toList();
        double average = graded.stream().mapToInt(Submission::getScore).average().orElse(0.0);
        AnalyticsReport report = new AnalyticsReport(test, graded.size(), average);
        for (Question question : test.getQuestions()) {
            long correct = graded.stream()
                    .filter(s -> {
                        String answer = s.getAnswer(question.getQuestionID());
                        return answer != null && question.validateAnswer(answer);
                    })
                    .count();
            double rate = graded.isEmpty() ? 0.0 : (double) correct / graded.size();
            report.setQuestionSuccessRate(question.getQuestionID(), rate);
        }
        return report;
    }

    public void addManagedClassroom(Classroom classroom) {
        if (classroom != null && !managedClassrooms.contains(classroom)) {
            managedClassrooms.add(classroom);
            touch();
        }
    }

    public List<Classroom> getManagedClassrooms() {
        return Collections.unmodifiableList(managedClassrooms);
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
        touch();
    }
}
