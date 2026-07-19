package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Exam;
import com.examcore.model.FillInBlankQuestion;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Quiz;
import com.examcore.model.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamAuthoringControllerTest {

    private Database database;
    private ExamAuthoringController controller;
    private Teacher teacher;

    @BeforeEach
    void setUp() {
        database = new Database();
        controller = new ExamAuthoringController(database);
        teacher = new Teacher("teacher1", "teacher1@test.com", "password123");
    }

    @Test
    void createExam_persistsToDatabaseAndSetsOwner() {
        Exam exam = controller.createExam(teacher, "Midterm", 90);

        assertEquals("Midterm", exam.getTitle());
        assertEquals(90, exam.getDurationLimit());
        assertEquals(teacher, exam.getOwner());
        assertTrue(database.findTestById(exam.getTestID()).isPresent());
    }

    @Test
    void createQuiz_persistsToDatabaseAndSetsOwner() {
        Quiz quiz = controller.createQuiz(teacher, "Pop Quiz", 15, "Loops");

        assertEquals("Pop Quiz", quiz.getTitle());
        assertEquals("Loops", quiz.getTopic());
        assertEquals(teacher, quiz.getOwner());
        assertTrue(database.findTestById(quiz.getTestID()).isPresent());
    }

    @Test
    void createExam_blankTitle_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createExam(teacher, "  ", 60));
    }

    @Test
    void createExam_nullTeacher_throws() {
        assertThrows(IllegalArgumentException.class, () -> controller.createExam(null, "Midterm", 60));
    }

    @Test
    void addMultipleChoiceQuestion_appendsToTest() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        MultipleChoiceQuestion q = controller.addMultipleChoiceQuestion(
                exam, "2+2=?", List.of("3", "4", "5"), "4", List.of("arithmetic"));

        assertEquals(1, exam.getQuestions().size());
        assertEquals("4", q.getSolution());
        assertTrue(q.getTags().contains("arithmetic"));
        assertTrue(q.validateAnswer("4"));
    }

    @Test
    void addMultipleChoiceQuestion_multipleTags_areAllStored() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        MultipleChoiceQuestion q = controller.addMultipleChoiceQuestion(
                exam, "2+2=?", List.of("3", "4"), "4", List.of("arithmetic", "easy"));

        assertEquals(2, q.getTags().size());
        assertTrue(q.getTags().containsAll(List.of("arithmetic", "easy")));
    }

    @Test
    void addMultipleChoiceQuestion_correctChoiceNotInOptions_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () ->
                controller.addMultipleChoiceQuestion(exam, "2+2=?", List.of("3", "5"), "4", null));
    }

    @Test
    void addMultipleChoiceQuestion_tooFewChoices_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () ->
                controller.addMultipleChoiceQuestion(exam, "2+2=?", List.of("4"), "4", null));
    }

    @Test
    void addMultipleChoiceQuestion_blankContent_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () ->
                controller.addMultipleChoiceQuestion(exam, "  ", List.of("3", "4"), "4", null));
    }

    @Test
    void addShortAnswerQuestion_appendsToTest() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        FillInBlankQuestion q = controller.addShortAnswerQuestion(exam, "Capital of France?", "Paris", List.of("geo"));

        assertEquals(1, exam.getQuestions().size());
        assertTrue(q.validateAnswer("Paris"));
        assertTrue(q.getTags().contains("geo"));
    }

    @Test
    void addShortAnswerQuestion_blankAnswer_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () ->
                controller.addShortAnswerQuestion(exam, "Capital of France?", "  ", null));
    }

    @Test
    void addQuestion_nullTest_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.addShortAnswerQuestion(null, "Q", "A", null));
    }

    @Test
    void questionIDs_areSequentialPerTest() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        var q1 = controller.addShortAnswerQuestion(exam, "Q1", "A1", null);
        var q2 = controller.addShortAnswerQuestion(exam, "Q2", "A2", null);

        assertEquals(exam.getTestID() + "-Q1", q1.getQuestionID());
        assertEquals(exam.getTestID() + "-Q2", q2.getQuestionID());
    }

    @Test
    void removeQuestion_removesOnlyMatchingQuestion() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);
        var q1 = controller.addShortAnswerQuestion(exam, "Q1", "A1", null);
        controller.addShortAnswerQuestion(exam, "Q2", "A2", null);

        controller.removeQuestion(exam, q1.getQuestionID());

        assertEquals(1, exam.getQuestions().size());
        assertEquals("Q2", exam.getQuestions().get(0).getContent());
    }

    @Test
    void removeQuestion_unknownId_isNoOp() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);
        controller.addShortAnswerQuestion(exam, "Q1", "A1", null);

        controller.removeQuestion(exam, "does-not-exist");

        assertEquals(1, exam.getQuestions().size());
    }

    @Test
    void updateMultipleChoiceQuestion_replacesContentChoicesAndTags_preservingIdAndPosition() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);
        controller.addShortAnswerQuestion(exam, "Q1", "A1", null);
        var original = controller.addMultipleChoiceQuestion(exam, "2+2=?", List.of("3", "4"), "4", List.of("math"));
        controller.addShortAnswerQuestion(exam, "Q3", "A3", null);

        MultipleChoiceQuestion updated = controller.updateMultipleChoiceQuestion(
                exam, original.getQuestionID(), "3+3=?", List.of("5", "6"), "6", List.of("addition"));

        assertEquals(3, exam.getQuestions().size());
        assertEquals(original.getQuestionID(), updated.getQuestionID());
        assertEquals(1, exam.getQuestions().indexOf(updated));
        assertEquals("3+3=?", updated.getContent());
        assertTrue(updated.validateAnswer("6"));
        assertTrue(updated.getTags().contains("addition"));
    }

    @Test
    void updateMultipleChoiceQuestion_unknownId_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () ->
                controller.updateMultipleChoiceQuestion(exam, "nope", "Q", List.of("A", "B"), "A", null));
    }

    @Test
    void updateShortAnswerQuestion_replacesContentAndAnswer_preservingId() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);
        var original = controller.addShortAnswerQuestion(exam, "Capital of France?", "Paris", null);

        FillInBlankQuestion updated = controller.updateShortAnswerQuestion(
                exam, original.getQuestionID(), "Capital of Japan?", "Tokyo", List.of("geo"));

        assertEquals(original.getQuestionID(), updated.getQuestionID());
        assertEquals(1, exam.getQuestions().size());
        assertTrue(updated.validateAnswer("Tokyo"));
        assertFalse(updated.validateAnswer("Paris"));
    }

    @Test
    void updateShortAnswerQuestion_blankAnswer_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);
        var original = controller.addShortAnswerQuestion(exam, "Q", "A", null);

        assertThrows(IllegalArgumentException.class, () ->
                controller.updateShortAnswerQuestion(exam, original.getQuestionID(), "Q", "  ", null));
    }

    @Test
    void updateTitle_changesTitle() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        controller.updateTitle(exam, "Final");

        assertEquals("Final", exam.getTitle());
    }

    @Test
    void updateTitle_blank_throws() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        assertThrows(IllegalArgumentException.class, () -> controller.updateTitle(exam, ""));
    }

    @Test
    void updateDuration_changesDuration() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        controller.updateDuration(exam, 45);

        assertEquals(45, exam.getDurationLimit());
    }

    @Test
    void deleteTest_removesFromDatabase() {
        Exam exam = controller.createExam(teacher, "Midterm", 60);

        controller.deleteTest(exam);

        assertFalse(database.findTestById(exam.getTestID()).isPresent());
    }

    @Test
    void deleteTest_nullTest_doesNotThrow() {
        controller.deleteTest(null);
    }

    @Test
    void constructor_nullDatabase_throws() {
        assertThrows(IllegalArgumentException.class, () -> new ExamAuthoringController(null));
    }
}
