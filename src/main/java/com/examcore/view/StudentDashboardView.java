package com.examcore.view;

import com.examcore.controller.ExamRunnerController;
import com.examcore.controller.FeedbackController;
import com.examcore.data.Database;
import com.examcore.model.ActivityLog;
import com.examcore.model.Classroom;
import com.examcore.model.LeaderBoard;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import com.examcore.service.GradingService;
import com.examcore.service.TimerService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudentDashboardView {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Database database;
    private final Student student;
    private final Runnable onLogout;
    private final BorderPane root;

    private int activeTab = 0;
    private boolean onProfile = false;

    public StudentDashboardView(Database database, Student student, Runnable onLogout) {
        this.database = database;
        this.student = student;
        this.onLogout = onLogout;
        this.root = new BorderPane();
        this.root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(
                List.of("Exams", "Quizzes", "Leaderboard", "Activity"),
                onProfile ? -1 : activeTab,
                student,
                idx -> {
                    onProfile = false;
                    activeTab = idx;
                    render();
                },
                () -> {
                    onProfile = true;
                    render();
                });

        VBox page;
        if (onProfile) {
            page = buildProfilePage();
        } else {
            page = switch (activeTab) {
                case 1 -> buildTestListPage();
                case 2 -> buildLeaderboardPage();
                case 3 -> buildActivityPage();
                default -> buildTestListPage();
            };
        }

        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }
}