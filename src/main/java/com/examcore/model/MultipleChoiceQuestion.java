package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultipleChoiceQuestion extends Question {

    private final List<String> choices = new ArrayList<>();

    public MultipleChoiceQuestion(String questionID, String content, String solution) {
        super(QuestionType.MULTIPLE_CHOICE, questionID, content, solution);
    }

    public void addChoice(String choice) {
        if (choice == null || choice.isBlank()) {
            throw new IllegalArgumentException("Choice cannot be blank");
        }
        choices.add(choice);
        touch();
    }

    public List<String> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    @Override
    public boolean validateAnswer(String submittedAnswer) {
        if (submittedAnswer == null || getSolution() == null) {
            return false;
        }
        return submittedAnswer.trim().equalsIgnoreCase(getSolution().trim());
    }
}
