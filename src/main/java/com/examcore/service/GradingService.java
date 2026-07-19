package com.examcore.service;

import com.examcore.data.Database;
import com.examcore.model.AnalyticsReport;
import com.examcore.model.Question;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.model.TestType;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradingService {

    private final Database database;

    public GradingService() {
        this(Database.getInstance());
    }

    public GradingService(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }

    public int calculateScore(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        Test test = submission.getTest();
        if (test.getQuestions().isEmpty()) {
            return 0;
        }
        int score = 0;
        for (Question question : test.getQuestions()) {
            String answer = submission.getAnswer(question.getQuestionID());
            if (answer != null && !answer.isBlank() && question.validateAnswer(answer)) {
                score++;
            }
        }
        return score;
    }

    public Map<String, Boolean> getPerQuestionResults(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (Question question : submission.getTest().getQuestions()) {
            String answer = submission.getAnswer(question.getQuestionID());
            boolean correct = answer != null && !answer.isBlank() && question.validateAnswer(answer);
            results.put(question.getQuestionID(), correct);
        }
        return results;
    }

    public double calculatePercentage(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        int totalQuestions = submission.getTest().getQuestions().size();
        if (totalQuestions == 0) {
            return 0.0;
        }
        int score = submission.isGraded() ? submission.getScore() : calculateScore(submission);
        return (score * 100.0) / totalQuestions;
    }

    public synchronized int gradeSubmission(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        if (submission.isGraded()) {
            throw new IllegalStateException("Submission has already been graded");
        }

        int score = calculateScore(submission);
        submission.markGraded(score);

        Student student = submission.getStudent();
        Test test = submission.getTest();
        if (test.getTestType() == TestType.EXAM) {
            student.addExamScore(score);
        } else {
            student.addQuizScore(score);
        }

        student.getActivityLog().logCompletedTest(submission);
        database.saveSubmission(submission);
        database.getLeaderBoard().recordScore(student, student.getTotalScore());
        database.logSystemEvent(student.getUsername() + " completed '" + test.getTitle() + "' (score "
                + score + "/" + test.getQuestions().size() + ")");

        return score;
    }

    public List<Submission> rankSubmissionsForTest(Test test) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        return database.getSubmissionsForTest(test).stream()
                .filter(Submission::isGraded)
                .sorted(Comparator.comparingInt(Submission::getScore).reversed())
                .toList();
    }

    public Map<String, int[]> computeTagBreakdown(Test test, List<Submission> submissions) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        if (submissions == null) {
            throw new IllegalArgumentException("Submissions cannot be null");
        }
        Map<String, int[]> breakdown = new LinkedHashMap<>();
        for (Submission submission : submissions) {
            if (!submission.isGraded() || !submission.getTest().equals(test)) {
                continue;
            }
            Map<String, Boolean> perQuestion = getPerQuestionResults(submission);
            for (Question question : test.getQuestions()) {
                boolean correct = Boolean.TRUE.equals(perQuestion.get(question.getQuestionID()));
                for (String tag : question.getTags()) {
                    int[] counts = breakdown.computeIfAbsent(tag, t -> new int[2]);
                    counts[1]++;
                    if (correct) {
                        counts[0]++;
                    }
                }
            }
        }
        return breakdown;
    }

    public AnalyticsReport buildAnalyticsReport(Test test, List<Submission> submissions) {
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
}
