package com.examcore.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnalyticsReport {

    private final Test test;
    private final int submissionCount;
    private final double averageScore;
    private final Map<String, Double> perQuestionSuccessRate = new LinkedHashMap<>();

    public AnalyticsReport(Test test, int submissionCount, double averageScore) {
        this.test = test;
        this.submissionCount = submissionCount;
        this.averageScore = averageScore;
    }

    public void setQuestionSuccessRate(String questionID, double rate) {
        perQuestionSuccessRate.put(questionID, rate);
    }

    public Test getTest() {
        return test;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public Map<String, Double> getPerQuestionSuccessRate() {
        return perQuestionSuccessRate;
    }
}
