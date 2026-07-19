package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Exam;
import com.examcore.model.Feedback;
import com.examcore.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackControllerTest {

    private Database database;
    private FeedbackController controller;
    private Student student;
    private Exam exam;

    @BeforeEach
    void setUp() {
        database = new Database();
        controller = new FeedbackController(database);
        student = new Student("student1", "student1@test.com", "password123");
        exam = new Exam("EXAM-X", "Exam X", 60);
    }

    @Test
    void submitFeedback_persistsToDatabase() {
        Feedback feedback = controller.submitFeedback(student, exam, "Great exam!");

        assertEquals("Great exam!", feedback.getContent());
        assertFalse(feedback.isRead());
        assertTrue(database.getFeedbackForTest(exam).contains(feedback));
    }

    @Test
    void submitFeedback_blankContent_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.submitFeedback(student, exam, "  "));
    }

    @Test
    void submitFeedback_nullStudent_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.submitFeedback(null, exam, "text"));
    }

    @Test
    void submitFeedback_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.submitFeedback(student, null, "text"));
    }

    @Test
    void getFeedbackForTest_onlyReturnsMatchingTest() {
        Exam otherExam = new Exam("EXAM-Y", "Exam Y", 30);
        controller.submitFeedback(student, exam, "Feedback for X");
        controller.submitFeedback(student, otherExam, "Feedback for Y");

        List<Feedback> results = controller.getFeedbackForTest(exam);

        assertEquals(1, results.size());
        assertEquals("Feedback for X", results.get(0).getContent());
    }

    @Test
    void getFeedbackForTest_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.getFeedbackForTest(null));
    }

    @Test
    void markRead_flipsReadFlag() {
        Feedback feedback = controller.submitFeedback(student, exam, "text");

        controller.markRead(feedback);

        assertTrue(feedback.isRead());
    }

    @Test
    void markRead_nullFeedback_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.markRead(null));
    }

    @Test
    void constructor_nullDatabase_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FeedbackController(null));
    }
}
