package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import com.examcore.service.GradingService;
import com.examcore.service.TimerService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class ExamRunnerController {

    public interface ExamRunnerListener {
        void onExamStarted(Test test, int durationLimitMinutes);

        void onAnswerSaved(String questionID, String answer);

        void onTimeAlert(long secondsRemaining);

        void onTick(long secondsRemaining);

        void onExamSubmitted(Submission submission, int score, boolean dueToTimeout);
    }

    private final Database database;
    private final GradingService gradingService;
    private final TimerService timerService;

    private volatile ExamRunnerListener listener;
    private final List<String> stateLog = Collections.synchronizedList(new ArrayList<>());

    private Student currentStudent;
    private Test currentTest;
    private Submission currentSubmission;
    private volatile boolean active;

    public ExamRunnerController() {
        this(Database.getInstance(), new GradingService(), new TimerService());
    }

    public ExamRunnerController(Database database, GradingService gradingService, TimerService timerService) {
        if (database == null || gradingService == null || timerService == null) {
            throw new IllegalArgumentException("Database, GradingService, and TimerService cannot be null");
        }
        this.database = database;
        this.gradingService = gradingService;
        this.timerService = timerService;
        this.timerService.setListener(new TimerService.TimerListener() {
            @Override
            public void onTick(long secondsRemaining) {
                ExamRunnerListener l = listener;
                if (l != null) {
                    l.onTick(secondsRemaining);
                }
            }

            @Override
            public void onAlert(long secondsRemaining) {
                ExamRunnerListener l = listener;
                if (l != null) {
                    l.onTimeAlert(secondsRemaining);
                }
            }

            @Override
            public void onExpired() {
                handleTimeExpired();
            }
        });
    }

    public void setListener(ExamRunnerListener listener) {
        this.listener = listener;
    }

    public synchronized Submission startExam(String testID, Student student) {
        if (active) {
            throw new IllegalStateException("An exam is already in progress");
        }
        if (testID == null || testID.isBlank()) {
            throw new IllegalArgumentException("Test ID cannot be blank");
        }
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }

        Test test = database.findTestById(testID)
                .orElseThrow(() -> new NoSuchElementException("Test not found: " + testID));

        if (database.findSubmission(student, test).isPresent()) {
            String label = test.getTestType() == TestType.QUIZ ? "quiz" : "exam";
            throw new IllegalStateException("You have already completed this " + label);
        }

        currentStudent = student;
        currentTest = test;
        currentSubmission = new Submission(student, test);
        database.saveSubmission(currentSubmission);
        active = true;

        logState("Started " + test.getTestType() + " '" + test.getTestID() + "' for student '"
                + student.getUsername() + "'");

        test.startTimer();
        timerService.start(test.getDurationLimit());

        ExamRunnerListener l = listener;
        if (l != null) {
            l.onExamStarted(test, test.getDurationLimit());
        }
        return currentSubmission;
    }

    public synchronized void saveAnswer(String questionID, String answer) {
        ensureActive();
        if (questionID == null || questionID.isBlank()) {
            throw new IllegalArgumentException("Question ID cannot be blank");
        }

        currentSubmission.recordAnswer(questionID, answer);
        database.saveSubmission(currentSubmission);
        logState("Auto-saved answer for question '" + questionID + "'");

        ExamRunnerListener l = listener;
        if (l != null) {
            l.onAnswerSaved(questionID, answer);
        }
    }

    public synchronized Submission submitExam() {
        ensureActive();
        return finishExam(false);
    }

    private synchronized void handleTimeExpired() {
        if (!active) {
            // The student already submitted manually in the instant before expiry; nothing to do.
            return;
        }
        finishExam(true);
    }

    private Submission finishExam(boolean dueToTimeout) {
        timerService.stop();
        if (dueToTimeout) {
            currentSubmission.markTimeExpired();
        }
        currentTest.submitTest(currentSubmission);

        int score = gradingService.gradeSubmission(currentSubmission);

        logState("Submitted '" + currentTest.getTestID() + "' "
                + (dueToTimeout ? "(auto-submitted: time expired)" : "(manual submit)")
                + " -- score " + score + "/" + currentTest.getQuestions().size());

        Submission result = currentSubmission;
        active = false;
        currentSubmission = null;
        currentTest = null;
        currentStudent = null;

        ExamRunnerListener l = listener;
        if (l != null) {
            l.onExamSubmitted(result, score, dueToTimeout);
        }
        return result;
    }

    public synchronized int recordFocusLost() {
        if (!active) {
            return 0;
        }
        currentSubmission.recordFocusLoss();
        database.saveSubmission(currentSubmission);
        int count = currentSubmission.getFocusLossCount();
        logState("Focus lost / window left during '" + currentTest.getTestID() + "' (occurrence #" + count + ")");
        database.logSystemEvent(currentStudent.getUsername() + " left the Focus Mode window during '"
                + currentTest.getTitle() + "' (occurrence #" + count + ")");
        return count;
    }

    public synchronized void recordClipboardBlocked(String action) {
        if (!active) {
            return;
        }
        logState("Blocked clipboard action (" + action + ") during '" + currentTest.getTestID() + "'");
    }

    private void ensureActive() {
        if (!active) {
            throw new IllegalStateException("No exam is currently in progress");
        }
    }

    private void logState(String entry) {
        stateLog.add(LocalDateTime.now() + " - " + entry);
    }

    public List<String> getStateLog() {
        synchronized (stateLog) {
            return List.copyOf(stateLog);
        }
    }

    public boolean isActive() {
        return active;
    }

    public Optional<Submission> getCurrentSubmission() {
        return Optional.ofNullable(currentSubmission);
    }

    public Optional<Test> getCurrentTest() {
        return Optional.ofNullable(currentTest);
    }

    public long getSecondsRemaining() {
        return timerService.getSecondsRemaining();
    }

    public void shutdown() {
        timerService.shutdown();
    }

}
