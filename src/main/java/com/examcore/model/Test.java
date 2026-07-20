package com.examcore.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Test extends BaseEntity {

    private final TestType testType;
    private final String testID;
    private String title;
    private int durationLimit;
    private final List<Question> questions = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private LocalDateTime timerStartedAt;
    private Teacher owner;

    protected Test(TestType testType, String testID, String title, int durationLimitMinutes) {
        super();
        if (testType == null) {
            throw new IllegalArgumentException("Test type cannot be null");
        }
        if (testID == null || testID.isBlank()) {
            throw new IllegalArgumentException("Test ID cannot be blank");
        }
        if (durationLimitMinutes <= 0) {
            throw new IllegalArgumentException("Duration limit must be positive");
        }
        this.testType = testType;
        this.testID = testID;
        this.title = title;
        this.durationLimit = durationLimitMinutes;
    }

    public void startTimer() {
        this.timerStartedAt = LocalDateTime.now();
        touch();
    }

    public LocalDateTime getTimerStartedAt() {
        return timerStartedAt;
    }


    public int calculateScore(Submission submission) {
        if (submission == null || questions.isEmpty()) {
            return 0;
        }
        int points = 0;
        for (Question question : questions) {
            String answer = submission.getAnswer(question.getQuestionID());
            if (answer != null && question.validateAnswer(answer)) {
                points++;
            }
        }
        return points;
    }

    public void submitTest(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        submission.markSubmitted(submission.isTimeExpired());
        touch();
    }

    public void addQuestion(Question question) {
        if (question == null) {
            throw new IllegalArgumentException("Question cannot be null");
        }
        questions.add(question);
        touch();
    }


    public void clearQuestions() {
        questions.clear();
        touch();
    }

    public void replaceQuestion(String questionID, Question replacement) {
        if (questionID == null || replacement == null) {
            throw new IllegalArgumentException("Question ID and replacement cannot be null");
        }
        int index = -1;
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getQuestionID().equals(questionID)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("No question with ID " + questionID + " found on this test");
        }
        questions.set(index, replacement);
        touch();
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public Teacher getOwner() {
        return owner;
    }

    public void setOwner(Teacher owner) {
        this.owner = owner;
        touch();
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            tags.add(tag);
            touch();
        }
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public TestType getTestType() {
        return testType;
    }

    public String getTestID() {
        return testID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    public int getDurationLimit() {
        return durationLimit;
    }

    public void setDurationLimit(int durationLimit) {
        if (durationLimit <= 0) {
            throw new IllegalArgumentException("Duration limit must be positive");
        }
        this.durationLimit = durationLimit;
        touch();
    }
}
