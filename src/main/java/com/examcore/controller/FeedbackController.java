package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Feedback;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import com.examcore.model.Test;
import com.examcore.util.ProfanityFilter;

import java.util.List;
import java.util.Optional;

public class FeedbackController {

    private final Database database;

    public FeedbackController() {
        this(Database.getInstance());
    }

    public FeedbackController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }

    public Feedback submitFeedback(Student student, Test test, String content) {
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Feedback content cannot be blank");
        }

        Optional<String> bannedWord = ProfanityFilter.findBannedWord(content);
        if (bannedWord.isPresent()) {
            database.logSystemEvent(student.getUsername() + " attempted to submit feedback on '" + test.getTitle()
                    + "' containing a prohibited word ('" + bannedWord.get() + "')");
            throw new IllegalArgumentException(
                    "It is prohibited to type this word: \"" + bannedWord.get() + "\". Please remove it and try again.");
        }

        student.submitFeedback(test, content);
        Feedback feedback = new Feedback(student, test, content);
        database.saveFeedback(feedback);
        return feedback;
    }

    //report
    public void reportFeedback(Teacher reportedBy, Feedback feedback) {
        if (reportedBy == null) {
            throw new IllegalArgumentException("Reporter cannot be null");
        }
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback cannot be null");
        }
        feedback.markReported();
        database.logSystemEvent(reportedBy.getUsername() + " reported feedback from "
                + feedback.getStudent().getUsername() + " on '" + feedback.getTest().getTitle()
                + "' as inappropriate");
    }

    public List<Feedback> getFeedbackForTest(Test test) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        return database.getFeedbackForTest(test);
    }

    public void markRead(Feedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback cannot be null");
        }
        feedback.markRead();
        database.flush();
    }
}
