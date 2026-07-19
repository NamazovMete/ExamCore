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


}
