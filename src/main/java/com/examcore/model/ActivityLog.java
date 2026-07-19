package com.examcore.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ActivityLog extends BaseEntity {

    private final List<Submission> completionHistory = new ArrayList<>();

    public void logCompletedTest(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        completionHistory.add(submission);
        touch();
    }

    public List<Submission> getCompletionHistory() {
        return Collections.unmodifiableList(completionHistory);
    }


    public Map<LocalDate, Integer> generateHeatMap(int weeks) {
        if (weeks <= 0) {
            throw new IllegalArgumentException("Weeks must be positive");
        }
        LocalDate cutoff = LocalDate.now().minusWeeks(weeks);
        Map<LocalDate, Integer> heatmap = new LinkedHashMap<>();
        for (Submission submission : completionHistory) {
            if (submission.getSubmittedAt() == null) {
                continue;
            }
            LocalDate day = submission.getSubmittedAt().toLocalDate();
            if (day.isBefore(cutoff)) {
                continue;
            }
            heatmap.merge(day, 1, Integer::sum);
        }
        return heatmap;
    }
}

