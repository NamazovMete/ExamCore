package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Exam;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.service.GradingService;
import com.examcore.service.TimerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamRunnerControllerTest {

    private static class FastExpiringTimerService extends TimerService {
        @Override
        public synchronized void start(int durationLimitMinutes) {
            startSeconds(1);
        }
    }

    private Database database;
    private GradingService gradingService;
    private ExamRunnerController controller;
    private Student student;
    private Exam exam;
    private Exam secondExam;

    @BeforeEach
    void setUp() {
        database = new Database();
        gradingService = new GradingService(database);

        exam = new Exam("EXAM-1", "Sample Exam", 60);
        MultipleChoiceQuestion q1 = new MultipleChoiceQuestion("Q1", "2+2=?", "4");
        q1.addChoice("3");
        q1.addChoice("4");
        exam.addQuestion(q1);
        MultipleChoiceQuestion q2 = new MultipleChoiceQuestion("Q2", "3+3=?", "6");
        q2.addChoice("6");
        q2.addChoice("7");
        exam.addQuestion(q2);
        database.saveTest(exam);

        secondExam = new Exam("EXAM-2", "Another Exam", 60);
        MultipleChoiceQuestion q3 = new MultipleChoiceQuestion("Q1", "5+5=?", "10");
        q3.addChoice("9");
        q3.addChoice("10");
        secondExam.addQuestion(q3);
        database.saveTest(secondExam);

        student = new Student("student1", "student1@test.com", "password123");
        database.saveUser(student);

        controller = new ExamRunnerController(database, gradingService, new TimerService());
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
    }

    @Test
    void startExam_createsAndReturnsSubmission() {
        Submission submission = controller.startExam("EXAM-1", student);

        assertTrue(controller.isActive());
        assertEquals(student, submission.getStudent());
        assertEquals(exam, submission.getTest());
        assertEquals(exam, controller.getCurrentTest().orElseThrow());
    }

    @Test
    void startExam_unknownTestId_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> controller.startExam("NOPE", student));
    }

    @Test
    void startExam_alreadyCompletedByStudent_throwsIllegalStateException() {
        controller.startExam("EXAM-1", student);
        controller.submitExam();

        assertThrows(IllegalStateException.class, () -> controller.startExam("EXAM-1", student));
    }

    @Test
    void startExam_completedByDifferentStudent_stillAllowsThisStudent() {
        Student other = new Student("student2", "student2@test.com", "password123");
        database.saveUser(other);
        controller.startExam("EXAM-1", other);
        controller.submitExam();

        assertDoesNotThrow(() -> controller.startExam("EXAM-1", student));
    }

    @Test
    void startExam_nullStudent_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> controller.startExam("EXAM-1", null));
    }

    @Test
    void startExam_blankTestId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> controller.startExam("  ", student));
    }

    @Test
    void startExam_whileAlreadyActive_throwsIllegalStateException() {
        controller.startExam("EXAM-1", student);

        assertThrows(IllegalStateException.class, () -> controller.startExam("EXAM-1", student));
    }

    @Test
    void saveAnswer_recordsAnswerOnSubmission() {
        Submission submission = controller.startExam("EXAM-1", student);

        controller.saveAnswer("Q1", "4");

        assertEquals("4", submission.getAnswer("Q1"));
    }

    @Test
    void saveAnswer_withoutActiveExam_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> controller.saveAnswer("Q1", "4"));
    }

    @Test
    void saveAnswer_blankQuestionId_throwsIllegalArgumentException() {
        controller.startExam("EXAM-1", student);

        assertThrows(IllegalArgumentException.class, () -> controller.saveAnswer("", "4"));
    }

    @Test
    void saveAnswer_invokesListenerCallback() {
        controller.startExam("EXAM-1", student);
        AtomicReference<String> savedQuestion = new AtomicReference<>();
        controller.setListener(new ExamRunnerController.ExamRunnerListener() {
            @Override
            public void onExamStarted(com.examcore.model.Test test, int durationLimitMinutes) {
            }

            @Override
            public void onAnswerSaved(String questionID, String answer) {
                savedQuestion.set(questionID);
            }

            @Override
            public void onTimeAlert(long secondsRemaining) {
            }

            @Override
            public void onTick(long secondsRemaining) {
            }

            @Override
            public void onExamSubmitted(Submission submission, int score, boolean dueToTimeout) {
            }
        });

        controller.saveAnswer("Q1", "4");

        assertEquals("Q1", savedQuestion.get());
    }

    @Test
    void submitExam_allCorrect_returnsFullyGradedSubmission() {
        controller.startExam("EXAM-1", student);
        controller.saveAnswer("Q1", "4");
        controller.saveAnswer("Q2", "6");

        Submission result = controller.submitExam();

        assertTrue(result.isGraded());
        assertEquals(2, result.getScore());
        assertFalse(result.isTimeExpired());
        assertFalse(controller.isActive());
    }

    @Test
    void submitExam_emptySubmission_gradesToZero() {
        controller.startExam("EXAM-1", student);

        Submission result = controller.submitExam();

        assertTrue(result.isGraded());
        assertEquals(0, result.getScore());
    }

    @Test
    void submitExam_withoutActiveExam_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> controller.submitExam());
    }

    @Test
    void submitExam_twice_secondCallThrows() {
        controller.startExam("EXAM-1", student);
        controller.submitExam();

        assertThrows(IllegalStateException.class, () -> controller.submitExam());
    }

    @Test
    void submitExam_updatesStudentExamScore() {
        controller.startExam("EXAM-1", student);
        controller.saveAnswer("Q1", "4");

        controller.submitExam();

        assertEquals(1, student.getExamScore());
    }

    @Test
    void submitExam_allowsStartingADifferentTestAfterward() {
        controller.startExam("EXAM-1", student);
        controller.submitExam();

        Submission secondAttempt = controller.startExam("EXAM-2", student);

        assertTrue(controller.isActive());
        assertEquals(secondExam, secondAttempt.getTest());
    }

    @Test
    void stateLog_recordsStartAnswerAndSubmitEvents() {
        controller.startExam("EXAM-1", student);
        controller.saveAnswer("Q1", "4");
        controller.submitExam();

        assertEquals(3, controller.getStateLog().size());
        assertTrue(controller.getStateLog().get(0).contains("Started"));
        assertTrue(controller.getStateLog().get(1).contains("Auto-saved"));
        assertTrue(controller.getStateLog().get(2).contains("Submitted"));
    }

    @Test
    void timerExpiry_midTest_autoSubmitsWithPartialAnswers() throws InterruptedException {
        ExamRunnerController fastController = new ExamRunnerController(
                database, new GradingService(database), new FastExpiringTimerService());
        try {
            AtomicBoolean dueToTimeoutFlag = new AtomicBoolean(false);
            AtomicInteger reportedScore = new AtomicInteger(-1);
            CountDownLatch submittedLatch = new CountDownLatch(1);
            fastController.setListener(new ExamRunnerController.ExamRunnerListener() {
                @Override
                public void onExamStarted(com.examcore.model.Test test, int durationLimitMinutes) {
                }

                @Override
                public void onAnswerSaved(String questionID, String answer) {
                }

                @Override
                public void onTimeAlert(long secondsRemaining) {
                }

                @Override
                public void onTick(long secondsRemaining) {
                }

                @Override
                public void onExamSubmitted(Submission submission, int score, boolean dueToTimeout) {
                    dueToTimeoutFlag.set(dueToTimeout);
                    reportedScore.set(score);
                    submittedLatch.countDown();
                }
            });

            fastController.startExam("EXAM-1", student);
            fastController.saveAnswer("Q1", "4");
            // deliberately leave Q2 unanswered before time runs out

            boolean completed = submittedLatch.await(5, TimeUnit.SECONDS);

            assertTrue(completed, "Expected auto-submit within 5 seconds of timer expiry");
            assertTrue(dueToTimeoutFlag.get());
            assertEquals(1, reportedScore.get());
            assertFalse(fastController.isActive());
        } finally {
            fastController.shutdown();
        }
    }

    @Test
    void timerExpiry_afterManualSubmitJustBefore_doesNotDoubleSubmit() throws InterruptedException {
        ExamRunnerController fastController = new ExamRunnerController(
                database, new GradingService(database), new FastExpiringTimerService());
        try {
            fastController.startExam("EXAM-1", student);
            Submission manualResult = fastController.submitExam();

            // Allow time for the timer thread to fire onExpired even though we already submitted manually.
            Thread.sleep(1500);

            assertTrue(manualResult.isGraded());
            assertFalse(fastController.isActive());
        } finally {
            fastController.shutdown();
        }
    }

    @Test
    void getSecondsRemaining_reflectsActiveTimer() {
        controller.startExam("EXAM-1", student);

        assertEquals(60L * exam.getDurationLimit(), controller.getSecondsRemaining());
    }

    @Test
    void constructor_nullDependencies_throwIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExamRunnerController(null, gradingService, new TimerService()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamRunnerController(database, null, new TimerService()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamRunnerController(database, gradingService, null));
    }
}
