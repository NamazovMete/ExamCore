package com.examcore.service;

import com.examcore.data.Database;
import com.examcore.model.AnalyticsReport;
import com.examcore.model.Exam;
import com.examcore.model.FillInBlankQuestion;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Quiz;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingServiceTest {

    private GradingService gradingService;
    private Student student;
    private Exam exam;

    @BeforeEach
    void initFixtures() {
        gradingService = new GradingService(new Database());
        student = new Student("student1", "student1@test.com", "password123");

        exam = new Exam("EXAM-1", "Sample Exam", 60);
        MultipleChoiceQuestion mc = new MultipleChoiceQuestion("Q1", "2+2=?", "4");
        mc.addChoice("3");
        mc.addChoice("4");
        mc.addChoice("5");
        exam.addQuestion(mc);

        FillInBlankQuestion fib = new FillInBlankQuestion("Q2", "Capital of France?", "Paris");
        exam.addQuestion(fib);
    }

    @Test
    void calculateScore_allCorrect_returnsFullScore() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "Paris");

        assertEquals(2, gradingService.calculateScore(submission));
    }

    @Test
    void calculateScore_partiallyCorrect_returnsPartialScore() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "London");

        assertEquals(1, gradingService.calculateScore(submission));
    }

    @Test
    void calculateScore_allWrong_returnsZero() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "3");
        submission.recordAnswer("Q2", "Berlin");

        assertEquals(0, gradingService.calculateScore(submission));
    }

    @Test
    void calculateScore_emptySubmission_returnsZero() {
        Submission submission = new Submission(student, exam);

        assertTrue(submission.isEmpty());
        assertEquals(0, gradingService.calculateScore(submission));
    }

    @Test
    void calculateScore_testWithNoQuestions_returnsZero() {
        Exam emptyExam = new Exam("EMPTY-EXAM", "Empty Exam", 30);
        Submission submission = new Submission(student, emptyExam);

        assertEquals(0, gradingService.calculateScore(submission));
    }

    @Test
    void calculateScore_blankAnswer_isTreatedAsUnanswered() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "   ");

        assertEquals(1, gradingService.calculateScore(submission));
    }

    @Test
    void getPerQuestionResults_reportsCorrectnessPerQuestion() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "wrong");

        Map<String, Boolean> results = gradingService.getPerQuestionResults(submission);

        assertTrue(results.get("Q1"));
        assertFalse(results.get("Q2"));
    }

    @Test
    void getPerQuestionResults_unansweredQuestion_reportsFalse() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");

        Map<String, Boolean> results = gradingService.getPerQuestionResults(submission);

        assertFalse(results.get("Q2"));
    }

    @Test
    void calculatePercentage_halfCorrect_returnsFifty() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "wrong");

        assertEquals(50.0, gradingService.calculatePercentage(submission), 0.001);
    }

    @Test
    void gradeSubmission_marksGradedAndUpdatesStudentTotals() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "Paris");

        int score = gradingService.gradeSubmission(submission);

        assertEquals(2, score);
        assertTrue(submission.isGraded());
        assertEquals(2, submission.getScore());
        assertEquals(2, student.getExamScore());
    }

    @Test
    void gradeSubmission_quizType_updatesQuizScoreNotExamScore() {
        Quiz quiz = new Quiz("QUIZ-1", "Sample Quiz", 20, "Topic");
        MultipleChoiceQuestion mc = new MultipleChoiceQuestion("QQ1", "1+1=?", "2");
        mc.addChoice("2");
        mc.addChoice("3");
        quiz.addQuestion(mc);

        Submission submission = new Submission(student, quiz);
        submission.recordAnswer("QQ1", "2");

        gradingService.gradeSubmission(submission);

        assertEquals(1, student.getQuizScore());
        assertEquals(0, student.getExamScore());
    }

    @Test
    void gradeSubmission_logsCompletionInActivityLog() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");

        gradingService.gradeSubmission(submission);

        assertEquals(1, student.getActivityLog().getCompletionHistory().size());
        assertTrue(student.getActivityLog().getCompletionHistory().contains(submission));
    }

    @Test
    void gradeSubmission_updatesLeaderboard() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        submission.recordAnswer("Q2", "Paris");

        Database db = new Database();
        GradingService service = new GradingService(db);
        service.gradeSubmission(submission);

        assertEquals(student.getTotalScore(), db.getLeaderBoard().getScore(student));
    }

    @Test
    void gradeSubmission_alreadyGraded_throwsIllegalStateException() {
        Submission submission = new Submission(student, exam);
        submission.recordAnswer("Q1", "4");
        gradingService.gradeSubmission(submission);

        assertThrows(IllegalStateException.class, () -> gradingService.gradeSubmission(submission));
    }

    @Test
    void gradeSubmission_nullSubmission_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> gradingService.gradeSubmission(null));
    }

    @Test
    void constructor_nullDatabase_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new GradingService(null));
    }

    @Test
    void rankSubmissionsForTest_ordersHighestScoreFirst() {
        Database db = new Database();
        GradingService service = new GradingService(db);
        Student low = new Student("low", "low@test.com", "password123");
        Student high = new Student("high", "high@test.com", "password123");

        Submission lowSubmission = new Submission(low, exam);
        lowSubmission.recordAnswer("Q1", "4");
        service.gradeSubmission(lowSubmission);

        Submission highSubmission = new Submission(high, exam);
        highSubmission.recordAnswer("Q1", "4");
        highSubmission.recordAnswer("Q2", "Paris");
        service.gradeSubmission(highSubmission);

        var ranked = service.rankSubmissionsForTest(exam);

        assertEquals(2, ranked.size());
        assertEquals(highSubmission, ranked.get(0));
        assertEquals(lowSubmission, ranked.get(1));
    }

    @Test
    void rankSubmissionsForTest_excludesUngradedSubmissions() {
        Database db = new Database();
        GradingService service = new GradingService(db);
        Submission ungraded = new Submission(student, exam);
        db.saveSubmission(ungraded);

        var ranked = service.rankSubmissionsForTest(exam);

        assertTrue(ranked.isEmpty());
    }

    @Test
    void rankSubmissionsForTest_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () -> gradingService.rankSubmissionsForTest(null));
    }

    @Test
    void computeTagBreakdown_singleGradedSubmission_reportsPerTagCorrectness() {
        Exam taggedExam = new Exam("EXAM-TAGS", "Tagged Exam", 30);
        MultipleChoiceQuestion q1 = new MultipleChoiceQuestion("T1", "2+2=?", "4");
        q1.addChoice("3");
        q1.addChoice("4");
        q1.addTag("arithmetic");
        taggedExam.addQuestion(q1);

        FillInBlankQuestion q2 = new FillInBlankQuestion("T2", "Capital of France?", "Paris");
        q2.addTag("geography");
        taggedExam.addQuestion(q2);

        Submission submission = new Submission(student, taggedExam);
        submission.recordAnswer("T1", "4");
        submission.recordAnswer("T2", "wrong");
        gradingService.gradeSubmission(submission);

        Map<String, int[]> breakdown = gradingService.computeTagBreakdown(taggedExam, List.of(submission));

        assertArrayEquals(new int[]{1, 1}, breakdown.get("arithmetic"));
        assertArrayEquals(new int[]{0, 1}, breakdown.get("geography"));
    }

    @Test
    void computeTagBreakdown_multipleSubmissions_aggregatesAcrossStudents() {
        Database db = new Database();
        GradingService service = new GradingService(db);
        Exam taggedExam = new Exam("EXAM-TAGS-2", "Tagged Exam 2", 30);
        MultipleChoiceQuestion q1 = new MultipleChoiceQuestion("U1", "2+2=?", "4");
        q1.addChoice("3");
        q1.addChoice("4");
        q1.addTag("arithmetic");
        taggedExam.addQuestion(q1);

        Student first = new Student("first", "first@test.com", "password123");
        Student second = new Student("second", "second@test.com", "password123");

        Submission s1 = new Submission(first, taggedExam);
        s1.recordAnswer("U1", "4");
        service.gradeSubmission(s1);

        Submission s2 = new Submission(second, taggedExam);
        s2.recordAnswer("U1", "3");
        service.gradeSubmission(s2);

        Map<String, int[]> breakdown = service.computeTagBreakdown(taggedExam, List.of(s1, s2));

        assertArrayEquals(new int[]{1, 2}, breakdown.get("arithmetic"));
    }

    @Test
    void computeTagBreakdown_ignoresUngradedSubmissions() {
        Exam taggedExam = new Exam("EXAM-TAGS-3", "Tagged Exam 3", 30);
        MultipleChoiceQuestion q1 = new MultipleChoiceQuestion("V1", "2+2=?", "4");
        q1.addChoice("3");
        q1.addChoice("4");
        q1.addTag("arithmetic");
        taggedExam.addQuestion(q1);

        Submission ungraded = new Submission(student, taggedExam);
        ungraded.recordAnswer("V1", "4");

        Map<String, int[]> breakdown = gradingService.computeTagBreakdown(taggedExam, List.of(ungraded));

        assertTrue(breakdown.isEmpty());
    }

    @Test
    void computeTagBreakdown_questionWithMultipleTags_countsTowardEachTag() {
        Exam taggedExam = new Exam("EXAM-TAGS-4", "Tagged Exam 4", 30);
        MultipleChoiceQuestion q1 = new MultipleChoiceQuestion("W1", "2+2=?", "4");
        q1.addChoice("3");
        q1.addChoice("4");
        q1.addTag("arithmetic");
        q1.addTag("basics");
        taggedExam.addQuestion(q1);

        Submission submission = new Submission(student, taggedExam);
        submission.recordAnswer("W1", "4");
        gradingService.gradeSubmission(submission);

        Map<String, int[]> breakdown = gradingService.computeTagBreakdown(taggedExam, List.of(submission));

        assertArrayEquals(new int[]{1, 1}, breakdown.get("arithmetic"));
        assertArrayEquals(new int[]{1, 1}, breakdown.get("basics"));
    }

    @Test
    void computeTagBreakdown_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () -> gradingService.computeTagBreakdown(null, List.of()));
    }

    @Test
    void computeTagBreakdown_nullSubmissions_throws() {
        assertThrows(IllegalArgumentException.class, () -> gradingService.computeTagBreakdown(exam, null));
    }

    @Test
    void buildAnalyticsReport_computesAverageAndPerQuestionRates() {
        Submission s1 = new Submission(student, exam);
        s1.recordAnswer("Q1", "4");
        s1.recordAnswer("Q2", "Paris");
        gradingService.gradeSubmission(s1);

        Student other = new Student("other", "other@test.com", "password123");
        Submission s2 = new Submission(other, exam);
        s2.recordAnswer("Q1", "4");
        s2.recordAnswer("Q2", "wrong");
        gradingService.gradeSubmission(s2);

        AnalyticsReport report = gradingService.buildAnalyticsReport(exam, List.of(s1, s2));

        assertEquals(2, report.getSubmissionCount());
        assertEquals(1.5, report.getAverageScore(), 0.001);
        assertEquals(1.0, report.getPerQuestionSuccessRate().get("Q1"), 0.001);
        assertEquals(0.5, report.getPerQuestionSuccessRate().get("Q2"), 0.001);
    }

    @Test
    void buildAnalyticsReport_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () -> gradingService.buildAnalyticsReport(null, List.of()));
    }

    @Test
    void buildAnalyticsReport_nullSubmissions_treatsAsEmpty() {
        AnalyticsReport report = gradingService.buildAnalyticsReport(exam, null);
        assertEquals(0, report.getSubmissionCount());
    }
}
