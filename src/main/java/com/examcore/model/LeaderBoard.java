package com.examcore.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LeaderBoard extends BaseEntity {

    private final Map<Student, Integer> rankings = new LinkedHashMap<>();

    public void recordScore(Student student, int totalScore) {
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        rankings.put(student, totalScore);
        touch();
    }

    public void updateRanks() {
        touch();
    }

    public List<Student> fetchTopStudents(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n cannot be negative");
        }
        return rankings.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Integer getScore(Student student) {
        return rankings.get(student);
    }

    public Map<Student, Integer> getRankings() {
        return new LinkedHashMap<>(rankings);
    }
}
