package com.examcore.view;

import com.examcore.data.Database;
import com.examcore.model.AnalyticsReport;
import com.examcore.model.Question;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.model.User;
import com.examcore.service.GradingService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AnalyticsDetailView {

    private final BorderPane root = new BorderPane();

    public AnalyticsDetailView(Database database, User navUser, List<String> navTabs, int activeNavTab, Test test,
                                Runnable onBack, Consumer<Test> onSeeFeedbacks) {
        root.getStyleClass().add("app-bg");

        HBox nav = UiComponents.navBar(navTabs, activeNavTab, navUser, idx -> onBack.run(), onBack);

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("Analytics for " + test.getTitle());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var backButton = UiComponents.pinkButton("‹ Back");
        backButton.setOnAction(e -> onBack.run());
        header.getChildren().addAll(title, spacer, backButton);

        GradingService gradingService = new GradingService(database);
        List<Submission> submissions = database.getSubmissionsForTest(test);
        AnalyticsReport report = gradingService.buildAnalyticsReport(test, submissions);
        int totalQuestions = test.getQuestions().size();
        double averagePercent = totalQuestions == 0 ? 0.0 : (report.getAverageScore() / totalQuestions) * 100.0;

        VBox averageCard = UiComponents.card(14);
        Label averageTitle = new Label("Exam Average");
        averageTitle.getStyleClass().add("section-title");
        Label submissionCountLabel = new Label(report.getSubmissionCount() + " submission(s) graded");
        submissionCountLabel.getStyleClass().add("row-subtitle");
        HBox averageBar = UiComponents.progressBar(averagePercent, 400);
        Label averagePercentLabel = new Label(String.format("%.0f%%", averagePercent));
        averagePercentLabel.getStyleClass().add("row-title");
        averageCard.getChildren().addAll(averageTitle, submissionCountLabel, averageBar, averagePercentLabel);

        VBox perQuestionCard = UiComponents.card(14);
        Label perQuestionTitle = new Label("Per-question performance");
        perQuestionTitle.getStyleClass().add("section-title");
        perQuestionCard.getChildren().add(perQuestionTitle);

        int index = 1;
        for (Question question : test.getQuestions()) {
            double rate = report.getPerQuestionSuccessRate().getOrDefault(question.getQuestionID(), 0.0) * 100.0;

            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            Label qLabel = new Label("Q" + index);
            qLabel.setMinWidth(40);
            qLabel.getStyleClass().add("row-title");
            HBox bar = UiComponents.progressBar(rate, 260);
            Label pctLabel = new Label(String.format("%.0f%% correct", rate));
            pctLabel.getStyleClass().add("row-subtitle");
            pctLabel.setMinWidth(110);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var detailsButton = UiComponents.outlineButton("Details");
            int questionIndex = index;
            detailsButton.setOnAction(e -> showQuestionDetails(questionIndex, question, submissions, rate));

            row.getChildren().addAll(qLabel, bar, pctLabel, rowSpacer, detailsButton);
            perQuestionCard.getChildren().add(row);
            index++;
        }
        if (test.getQuestions().isEmpty()) {
            Label empty = new Label("This test has no questions yet.");
            empty.getStyleClass().add("muted-text");
            perQuestionCard.getChildren().add(empty);
        }

        VBox tagCard = buildTagPerformanceCard(gradingService, test, submissions);

        VBox page = new VBox(24, header, averageCard, perQuestionCard, tagCard);
        if (onSeeFeedbacks != null) {
            var seeFeedbacksButton = UiComponents.primaryButton("See Feedbacks");
            seeFeedbacksButton.setOnAction(e -> onSeeFeedbacks.accept(test));
            page.getChildren().add(seeFeedbacksButton);
        }

        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        var scrollPane = new javafx.scene.control.ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private VBox buildTagPerformanceCard(GradingService gradingService, Test test, List<Submission> submissions) {
        VBox tagCard = UiComponents.card(14);
        Label tagTitle = new Label("Performance by Tag");
        tagTitle.getStyleClass().add("section-title");
        tagCard.getChildren().add(tagTitle);

        Map<String, int[]> breakdown = gradingService.computeTagBreakdown(test, submissions);
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
            Label countLabel = new Label(correct + " / " + total + " correct");
            countLabel.getStyleClass().add("row-subtitle");
            countLabel.setMinWidth(120);

            row.getChildren().addAll(tagLabel, bar, countLabel);
            tagCard.getChildren().add(row);
        }
        if (breakdown.isEmpty()) {
            Label empty = new Label("No tagged questions on this test yet.");
            empty.getStyleClass().add("muted-text");
            tagCard.getChildren().add(empty);
        }
        return tagCard;
    }

    private void showQuestionDetails(int questionIndex, Question question, List<Submission> submissions, double rate) {
        List<Duration> times = submissions.stream()
                .map(s -> s.getTimeToFirstAnswer(question.getQuestionID()))
                .filter(d -> d != null)
                .toList();
        double avgSeconds = times.stream().mapToLong(Duration::getSeconds).average().orElse(0.0);

        String message = String.format(
                "Success rate: %.0f%%%nAnswered by: %d of %d submissions%nAverage time to answer: %.0f seconds",
                rate, times.size(), submissions.size(), avgSeconds);

        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText("Question " + questionIndex + " details");
        alert.showAndWait();
    }

    public BorderPane getRoot() {
        return root;
    }
}