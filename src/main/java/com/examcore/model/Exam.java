package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Exam extends Test {

    private final List<String> topics = new ArrayList<>();

    public Exam(String testID, String title, int durationLimitMinutes) {
        super(TestType.EXAM, testID, title, durationLimitMinutes);
    }

    public void addTopic(String topic) {
        if (topic != null && !topic.isBlank()) {
            topics.add(topic);
            touch();
        }
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }
}
