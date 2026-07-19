package com.examcore.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures a single student's attempt at a Test (Exam or Quiz): the answers
 * they recorded, whether grading has completed, and how the attempt ended.
 */
public class Submission extends BaseEntity {

    private final Student student;
    private final Test test;
    private final Map<String, String> answers = new LinkedHashMap<>();
    private final Map<String, LocalDateTime> firstAnsweredAt = new LinkedHashMap<>();
    private final List<LocalDateTime> focusLossEvents = new ArrayList<>();
    private final LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private int score;
    private boolean graded;
    private boolean timeExpired;

    public Submission(Student student, Test test) {
        super();
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        this.student = student;
        this.test = test;
        this.startedAt = LocalDateTime.now();
    }

    private Submission(Student student, Test test, LocalDateTime startedAt) {
        super();
        this.student = student;
        this.test = test;
        this.startedAt = startedAt;
    }

    /**
     * Rebuilds a Submission from persisted state, preserving the original
     * timestamps exactly (e.g. {@code submittedAt} feeds the student's
     * activity heatmap, so it must reflect the real historical date rather
     * than the moment the app happened to reload).
     */
    public static Submission restoreFromStorage(Student student, Test test, LocalDateTime startedAt,
                                                LocalDateTime submittedAt, int score, boolean graded,
                                                boolean timeExpired, Map<String, String> answers,
                                                Map<String, LocalDateTime> firstAnsweredAt,
                                                List<LocalDateTime> focusLossEvents) {
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        Submission submission = new Submission(student, test, startedAt);
        submission.submittedAt = submittedAt;
        submission.score = score;
        submission.graded = graded;
        submission.timeExpired = timeExpired;
        if (answers != null) {
            submission.answers.putAll(answers);
        }
        if (firstAnsweredAt != null) {
            submission.firstAnsweredAt.putAll(firstAnsweredAt);
        }
        if (focusLossEvents != null) {
            submission.focusLossEvents.addAll(focusLossEvents);
        }
        return submission;
    }

    public void recordAnswer(String questionID, String answer) {
        if (questionID == null || questionID.isBlank()) {
            throw new IllegalArgumentException("Question ID cannot be blank");
        }
        if (answer == null || answer.isBlank()) {
            answers.remove(questionID);
        } else {
            answers.put(questionID, answer);
            firstAnsweredAt.putIfAbsent(questionID, LocalDateTime.now());
        }
        touch();
    }

    /**
     * Approximate time spent reaching an answer for a question: the duration
     * from the start of the attempt to the first time that question was
     * answered. Returns {@code null} if the question was never answered.
     */
    public java.time.Duration getTimeToFirstAnswer(String questionID) {
        LocalDateTime answeredAt = firstAnsweredAt.get(questionID);
        if (answeredAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, answeredAt);
    }

    public String getAnswer(String questionID) {
        return answers.get(questionID);
    }

    public Map<String, String> getAnswers() {
        return Collections.unmodifiableMap(answers);
    }

    public boolean isEmpty() {
        return answers.isEmpty();
    }

    public Student getStudent() {
        return student;
    }

    public Test getTest() {
        return test;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public int getScore() {
        return score;
    }

    public boolean isGraded() {
        return graded;
    }

    public void markGraded(int finalScore) {
        this.score = finalScore;
        this.graded = true;
        touch();
    }

    /** Flags this submission as having run out of time, prior to being marked submitted. */
    public void markTimeExpired() {
        this.timeExpired = true;
        touch();
    }

    public boolean isTimeExpired() {
        return timeExpired;
    }

    public void markSubmitted(boolean dueToTimeExpiry) {
        this.submittedAt = LocalDateTime.now();
        this.timeExpired = dueToTimeExpiry;
        touch();
    }

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    /** Records that Focus Mode detected the student's window losing focus during this attempt. */
    public void recordFocusLoss() {
        focusLossEvents.add(LocalDateTime.now());
        touch();
    }

    public int getFocusLossCount() {
        return focusLossEvents.size();
    }

    public List<LocalDateTime> getFocusLossEvents() {
        return Collections.unmodifiableList(focusLossEvents);
    }
}
