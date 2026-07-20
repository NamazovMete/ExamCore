package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Exam;
import com.examcore.model.FillInBlankQuestion;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Question;
import com.examcore.model.Quiz;
import com.examcore.model.Teacher;
import com.examcore.model.Test;

import java.util.List;

public class ExamAuthoringController {

    private final Database database;

    public ExamAuthoringController() {
        this(Database.getInstance());
    }

    public ExamAuthoringController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }

    public Exam createExam(Teacher teacher, String title, int durationLimitMinutes) {
        requireTeacher(teacher);
        requireTitle(title);
        Exam exam = teacher.createExam(title, durationLimitMinutes);
        exam.setOwner(teacher);
        database.saveTest(exam);
        database.logSystemEvent("Created exam '" + title + "' (owner: " + teacher.getUsername() + ")");
        return exam;
    }

    public Quiz createQuiz(Teacher teacher, String title, int durationLimitMinutes, String topic) {
        requireTeacher(teacher);
        requireTitle(title);
        Quiz quiz = teacher.createQuiz(title, durationLimitMinutes, topic);
        quiz.setOwner(teacher);
        database.saveTest(quiz);
        database.logSystemEvent("Created quiz '" + title + "' (owner: " + teacher.getUsername() + ")");
        return quiz;
    }

    public MultipleChoiceQuestion addMultipleChoiceQuestion(Test test, String content, List<String> choices,
                                                              String correctChoice, List<String> tags) {
        return addMultipleChoiceQuestion(test, content, choices, correctChoice, tags, null);
    }
 
    public MultipleChoiceQuestion addMultipleChoiceQuestion(Test test, String content, List<String> choices,
                                                              String correctChoice, List<String> tags, String explanation) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        validateMultipleChoiceInput(content, choices, correctChoice);

        String questionID = nextQuestionID(test);
        MultipleChoiceQuestion question = new MultipleChoiceQuestion(questionID, content, correctChoice);
        for (String choice : choices) {
            question.addChoice(choice);
        }
        question.setTags(tags);
        question.setExplanation(explanation);
        test.addQuestion(question);
        database.saveTest(test);
        return question;
    }

    public FillInBlankQuestion addShortAnswerQuestion(Test test, String content, String correctAnswer,
                                                        List<String> tags) {
        return addShortAnswerQuestion(test, content, correctAnswer, tags, null);                                                    
    }
   
    public FillInBlankQuestion addShortAnswerQuestion(Test test, String content, String correctAnswer,
                                                        List<String> tags, String explanation) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        validateShortAnswerInput(content, correctAnswer);

        String questionID = nextQuestionID(test);
        FillInBlankQuestion question = new FillInBlankQuestion(questionID, content, correctAnswer);
        question.setTags(tags);
        question.setExplanation(explanation);
        test.addQuestion(question);
        database.saveTest(test);
        return question;
    }

    public MultipleChoiceQuestion updateMultipleChoiceQuestion(Test test, String questionID, String content,
                                                                 List<String> choices, String correctChoice,
                                                                 List<String> tags) {
        return updateMultipleChoiceQuestion(test, questionID, content, choices, correctChoice, tags, null);
    }

    public MultipleChoiceQuestion updateMultipleChoiceQuestion(Test test, String questionID, String content,
                                                                 List<String> choices, String correctChoice,
                                                                 List<String> tags, String explanation) {
        requireExistingQuestion(test, questionID);
        validateMultipleChoiceInput(content, choices, correctChoice);

        MultipleChoiceQuestion replacement = new MultipleChoiceQuestion(questionID, content, correctChoice);
        for (String choice : choices) {
            replacement.addChoice(choice);
        }
        replacement.setTags(tags);
        replacement.setExplanation(explanation);
        test.replaceQuestion(questionID, replacement);
        database.saveTest(test);
        return replacement;
    }

    public FillInBlankQuestion updateShortAnswerQuestion(Test test, String questionID, String content,
                                                           String correctAnswer, List<String> tags) {
        return updateShortAnswerQuestion(test, questionID, content, correctAnswer, tags, null);
    }
    
    public FillInBlankQuestion updateShortAnswerQuestion(Test test, String questionID, String content,
                                                           String correctAnswer, List<String> tags, String explanation) {
        requireExistingQuestion(test, questionID);
        validateShortAnswerInput(content, correctAnswer);

        FillInBlankQuestion replacement = new FillInBlankQuestion(questionID, content, correctAnswer);
        replacement.setTags(tags);
        replacement.setExplanation(explanation);
        test.replaceQuestion(questionID, replacement);
        database.saveTest(test);
        return replacement;
    }

    public void setShowAnswersAfterExam(Test test, boolean showAnswersAfterExam) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        test.setShowAnswersAfterExam(showAnswersAfterExam);
        database.saveTest(test);
    }


    //removes question by id
    public void removeQuestion(Test test, String questionID) {
        if (test == null || questionID == null) {
            return;
        }
        List<Question> remaining = test.getQuestions().stream()
                .filter(q -> !q.getQuestionID().equals(questionID))
                .toList();
        
        test.clearQuestions();
        remaining.forEach(test::addQuestion);
        database.saveTest(test);
    }

    public void updateTitle(Test test, String newTitle) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        requireTitle(newTitle);
        test.setTitle(newTitle);
        database.saveTest(test);
    }

    public void updateDuration(Test test, int durationLimitMinutes) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        test.setDurationLimit(durationLimitMinutes);
        database.saveTest(test);
    }

    //deletes test
    public void deleteTest(Test test) {
        if (test != null) {
            database.removeTest(test.getTestID());
            database.logSystemEvent("Deleted " + test.getTestType().name().toLowerCase() + " '" + test.getTitle() + "'");
        }
    }

    private void requireExistingQuestion(Test test, String questionID) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        if (questionID == null || test.getQuestions().stream().noneMatch(q -> q.getQuestionID().equals(questionID))) {
            throw new IllegalArgumentException("No question with ID " + questionID + " found on this test");
        }
    }

    private void validateMultipleChoiceInput(String content, List<String> choices, String correctChoice) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Question content cannot be blank");
        }
        if (choices == null || choices.size() < 2) {
            throw new IllegalArgumentException("A multiple-choice question needs at least two options");
        }
        if (correctChoice == null || !choices.contains(correctChoice)) {
            throw new IllegalArgumentException("The correct choice must be one of the provided options");
        }
    }

    private void validateShortAnswerInput(String content, String correctAnswer) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Question content cannot be blank");
        }
        if (correctAnswer == null || correctAnswer.isBlank()) {
            throw new IllegalArgumentException("Correct answer cannot be blank");
        }
    }

    private String nextQuestionID(Test test) {
        return test.getTestID() + "-Q" + (test.getQuestions().size() + 1);
    }

    private void requireTeacher(Teacher teacher) {
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher cannot be null");
        }
    }

    private void requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
    }
}
