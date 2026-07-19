package com.examcore.model;

public class FillInBlankQuestion extends Question {

    public FillInBlankQuestion(String questionID, String content, String solution) {
        super(QuestionType.FILL_IN_THE_BLANK, questionID, content, solution);
    }

    @Override
    public boolean validateAnswer(String submittedAnswer) {
        if (submittedAnswer == null || getSolution() == null) {
            return false;
        }
        String submitted = submittedAnswer.trim();
        String expected = getSolution().trim();
        if (submitted.isEmpty()) {
            return false;
        }
        if (submitted.equalsIgnoreCase(expected)) {
            return true;
        }
        return numericallyEqual(submitted, expected);
    }

    private boolean numericallyEqual(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
