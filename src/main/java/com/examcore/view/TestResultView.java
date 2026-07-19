package com.examcore.view;

import com.examcore.data.Database;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.service.GradingService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestResultView {

    private final BorderPane root = new BorderPane();

    public TestResultView(Database database, GradingService gradingService, Student student, Test test,
                           Runnable onBack) {
        root.getStyleClass().add("app-bg");

        HBox nav = UiComponents.navBar(List.of("Exams", "Quizzes", "Leaderboard", "Activity"), -1, student,
                idx -> onBack.run(), onBack);

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle(test.getTitle());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        var backButton = UiComponents.pinkButton("‹ Back");
        backButton.setOnAction(e -> onBack.run());
        header.getChildren().addAll(title, headerSpacer, backButton);

        String classroomNames = database.getAllClassrooms().stream()
                .filter(c -> c.getAssignedExams().contains(test))
                .map(c -> c.getClassName())
                .collect(Collectors.joining(", "));
        Label classroomLabel = new Label(classroomNames.isEmpty() ? "Not assigned to a classroom"
                : "Classroom: " + classroomNames);
        classroomLabel.getStyleClass().add("muted-text");

        List<Submission> ranked = gradingService.rankSubmissionsForTest(test);
        int totalQuestions = test.getQuestions().size();
        double average = ranked.stream().mapToInt(Submission::getScore).average().orElse(0.0);

        Submission mySubmission = ranked.stream().filter(s -> s.getStudent().equals(student)).findFirst().orElse(null);
        int myRank = mySubmission == null ? -1 : ranked.indexOf(mySubmission) + 1;

        VBox summaryCard = UiComponents.card(10);
        Label summaryTitle = new Label("Your Result");
        summaryTitle.getStyleClass().add("section-title");
        Label scoreLabel = new Label(mySubmission == null ? "No submission found"
                : "Score: " + mySubmission.getScore() + " / " + totalQuestions
                        + "   •   Rank: " + myRank + " of " + ranked.size());
        scoreLabel.getStyleClass().add("row-title");
        summaryCard.getChildren().addAll(summaryTitle, scoreLabel);

        VBox statsCard = UiComponents.card(10);
        Label statsTitle = new Label("Statistics");
        statsTitle.getStyleClass().add("section-title");
        Label statsLabel = new Label(String.format("Participants: %d   •   Average score: %.1f / %d",
                ranked.size(), average, totalQuestions));
        statsLabel.getStyleClass().add("row-subtitle");
        statsCard.getChildren().addAll(statsTitle, statsLabel);

        VBox tagCard = buildTagPerformanceCard(gradingService, test, mySubmission);

        VBox leaderboardCard = UiComponents.card(0);
        VBox table = new VBox(2);
        HBox headerRow = new HBox();
        headerRow.getStyleClass().add("leaderboard-header");
        headerRow.setPadding(new Insets(14, 20, 14, 20));
        headerRow.getChildren().addAll(
                columnLabel("Rank", 90), columnLabel("Student", 420), columnLabel("Score", 120));
        table.getChildren().add(headerRow);

        int rank = 1;
        for (Submission submission : ranked) {
            Student rowStudent = submission.getStudent();
            HBox row = new HBox();
            row.getStyleClass().add(rowStudent.equals(student) ? "leaderboard-row-highlight" : "leaderboard-row");
            row.setPadding(new Insets(14, 20, 14, 20));
            String name = rowStudent.equals(student) ? UiComponents.fullName(rowStudent) + " (me)"
                    : UiComponents.fullName(rowStudent);
            row.getChildren().addAll(
                    columnLabel(String.valueOf(rank), 90), columnLabel(name, 420),
                    columnLabel(submission.getScore() + " / " + totalQuestions, 120));
            table.getChildren().add(row);
            rank++;
        }
        leaderboardCard.getChildren().add(table);
        leaderboardCard.setPadding(Insets.EMPTY);

        Label leaderboardTitle = new Label("Leaderboard for this " + (totalQuestions == 0 ? "test" : test.getTestType()));
        leaderboardTitle.getStyleClass().add("section-title");

        VBox page = new VBox(24, header, classroomLabel, summaryCard, statsCard, tagCard, leaderboardTitle,
                leaderboardCard);
        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private VBox buildTagPerformanceCard(GradingService gradingService, Test test, Submission mySubmission) {
        VBox tagCard = UiComponents.card(10);
        Label tagTitle = new Label("Performance by Tag");
        tagTitle.getStyleClass().add("section-title");
        tagCard.getChildren().add(tagTitle);

        Map<String, int[]> breakdown = mySubmission == null ? Map.of()
                : gradingService.computeTagBreakdown(test, List.of(mySubmission));
        for (Map.Entry<String, int[]> entry : breakdown.entrySet()) {
            int correct = entry.getValue()[0];
            int total = entry.getValue()[1];
            double rate = total == 0 ? 0.0 : (correct * 100.0) / total;

            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            Label tagLabel = new Label(entry.getKey());
            tagLabel.setMinWidth(140);
            tagLabel.getStyleClass().add("row-title");
            HBox bar = UiComponents.progressBar(rate, 260);
            Label countLabel = new Label(correct + " / " + total);
            countLabel.getStyleClass().add("row-subtitle");
            countLabel.setMinWidth(60);

            row.getChildren().addAll(tagLabel, bar, countLabel);
            tagCard.getChildren().add(row);
        }
        if (breakdown.isEmpty()) {
            Label empty = new Label("No tagged questions on this test.");
            empty.getStyleClass().add("muted-text");
            tagCard.getChildren().add(empty);
        }
        return tagCard;
    }

    private Label columnLabel(String text, double width) {
        Label label = new Label(text);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.getStyleClass().add("row-title");
        return label;
    }

    public BorderPane getRoot() {
        return root;
    }
}
