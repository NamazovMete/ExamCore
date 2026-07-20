package com.examcore.view;

import com.examcore.controller.FeedbackController;
import com.examcore.data.Database;
import com.examcore.model.Classroom;
import com.examcore.model.Feedback;
import com.examcore.model.Student;
import com.examcore.model.User;
import com.examcore.model.Test;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;


public class FeedbackListView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final BorderPane root = new BorderPane();
    private final Database database;
    private final FeedbackController feedbackController;
    private final User navUser;
    private final int activeNavTab;
    
    private final Test test;
    private final List<String> navTabs;
    private final Runnable onBack;

    public FeedbackListView(Database database, FeedbackController feedbackController, User navUser, Test test,
                            List<String> navTabs, int activeNavTab, Runnable onBack) {
        this.database = database;
        this.feedbackController = feedbackController;
        this.navUser = navUser;
        this.activeNavTab = activeNavTab;
        this.test = test;
        this.navTabs = navTabs;
        this.onBack = onBack;
        root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(navTabs, activeNavTab, navUser, idx -> onBack.run(), onBack);

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("Feedbacks for " + test.getTitle());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var backButton = UiComponents.pinkButton("‹ Back");
        backButton.setOnAction(e -> onBack.run());
        header.getChildren().addAll(title, spacer, backButton);

        VBox list = new VBox(16);
        List<Feedback> feedbackList = feedbackController.getFeedbackForTest(test);
        for (Feedback feedback : feedbackList) {
            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            row.getChildren().add(UiComponents.iconBadge("💬", "icon-badge-orange"));

            VBox textBox = new VBox(4);
            Label name = new Label("Feedback by " + UiComponents.fullName(feedback.getStudent())
                    + (feedback.isRead() ? "" : "  •  new")
                    + (feedback.isReported() ? "  •  reported" : ""));
            name.getStyleClass().add("row-title");
            Label date = new Label(feedback.getCreatedAt().format(DATE_FORMAT)
                    + classroomSuffix(feedback.getStudent()));
            date.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, date);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var readButton = UiComponents.outlineButton(feedback.isRead() ? "✓ Read" : "Mark Read");
            readButton.getStyleClass().add("btn-icon-green");
            readButton.setDisable(feedback.isRead());
            readButton.setOnAction(e -> {
                feedbackController.markRead(feedback);
                render();
            });

            var viewButton = UiComponents.outlineButton("View");
            viewButton.getStyleClass().add("btn-icon-pink");
            viewButton.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, feedback.getContent());
                alert.setHeaderText("Feedback from " + UiComponents.fullName(feedback.getStudent()));
                alert.showAndWait();
                feedbackController.markRead(feedback);
                render();
            });

            var reportButton = UiComponents.outlineButton(feedback.isReported() ? "🚩 Reported" : "🚩 Report");
            reportButton.setDisable(feedback.isReported());
            reportButton.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Report this feedback from " + UiComponents.fullName(feedback.getStudent())
                                + " to the admin as inappropriate?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Report Feedback");
                confirm.showAndWait().ifPresent(button -> {
                    if (button == ButtonType.YES) {
                        feedbackController.reportFeedback(navUser, feedback);
                        render();
                    }
                });
            });

            row.getChildren().addAll(textBox, rowSpacer, readButton, reportButton, viewButton);
            list.getChildren().add(row);
        }
        if (feedbackList.isEmpty()) {
            Label empty = new Label("No feedback has been submitted for this test yet.");
            empty.getStyleClass().add("muted-text");
            list.getChildren().add(empty);
        }

        VBox page = new VBox(24, header, list);
        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        var scrollPane = new javafx.scene.control.ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private String classroomSuffix(Student student) {
        List<Classroom> classrooms = database.getAllClassrooms().stream()
                .filter(c -> c.getStudentList().contains(student) && c.getAssignedExams().contains(test))
                .toList();
        if (classrooms.isEmpty()) {
            return "";
        }
        return "  •  " + classrooms.stream().map(Classroom::getClassName).collect(Collectors.joining(", "));
    }
}
