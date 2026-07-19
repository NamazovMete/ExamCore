package com.examcore.model;

public class Quiz extends Test {

    private String topic;

    public Quiz(String testID, String title, int durationLimitMinutes, String topic) {
        super(TestType.QUIZ, testID, title, durationLimitMinutes);
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
        touch();
    }
}
