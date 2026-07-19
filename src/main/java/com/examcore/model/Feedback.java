package com.examcore.model;

public class Feedback extends BaseEntity {

    private final Student student;
    private final Test test;
    private final String content;
    private boolean read;
    private boolean reported;

    public Feedback(Student student, Test test, String content) {
        super();
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Feedback content cannot be blank");
        }
        this.student = student;
        this.test = test;
        this.content = content;
    }

    public Student getStudent() {
        return student;
    }

    public Test getTest() {
        return test;
    }

    public String getContent() {
        return content;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
        touch();
    }

    public boolean isReported() {
        return reported;
    }

    public void markReported() {
        this.reported = true;
        touch();
    }
}
