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

/**
 * Student workspace: My Exams, My Quizzes, Leaderboard, Personal Activity,
 * and Profile Settings, matching the EXAMCORE_DEMO.pdf student screens.
 */
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
                case 1 -> buildTestListPage("My Quizzes", TestType.QUIZ);
                case 2 -> buildLeaderboardPage();
                case 3 -> buildActivityPage();
                default -> buildTestListPage("My Exams", TestType.EXAM);
            };
        }

        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private VBox buildTestListPage(String title, TestType type) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = UiComponents.pageTitle(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("🔍 Search");
        search.setPrefWidth(220);
        var filterButton = UiComponents.outlineButton("▽ Filter");
        header.getChildren().addAll(titleLabel, spacer, search, filterButton);

        List<Classroom> myClassrooms = database.getAllClassrooms().stream()
                .filter(c -> c.getStudentList().contains(student))
                .toList();
        String[] statusFilter = {"All"};
        String[] classroomFilter = {"All"};

        VBox listContainer = new VBox(16);
        Runnable refresh = () -> listContainer.getChildren().setAll(
                buildTestList(type, search.getText(), statusFilter[0], classroomFilter[0]));
        search.textProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        filterButton.setOnAction(e -> openFilterDialog(statusFilter, classroomFilter, myClassrooms, refresh));

        return new VBox(24, header, listContainer);
    }

    private List<HBox> buildTestList(TestType type, String query, String statusFilter, String classroomFilter) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<HBox> rows = new java.util.ArrayList<>();

        for (Test test : database.getAllTests()) {
            if (test.getTestType() != type) {
                continue;
            }
            if (!student.getAssignedExams().contains(test)) {
                continue;
            }
            if (!normalizedQuery.isEmpty() && !test.getTitle().toLowerCase().contains(normalizedQuery)) {
                continue;
            }
            boolean finished = database.findSubmission(student, test).isPresent();
            if (statusFilter.equals("Finished") && !finished) {
                continue;
            }
            if (statusFilter.equals("Unfinished") && finished) {
                continue;
            }
            List<Classroom> classrooms = classroomsForTest(test);
            if (!classroomFilter.equals("All") && classrooms.stream().noneMatch(c -> c.getClassName().equals(classroomFilter))) {
                continue;
            }
            rows.add(buildTestRow(test, finished, classrooms));
        }

        if (rows.isEmpty()) {
            Label empty = new Label("No tests match your search/filter.");
            empty.getStyleClass().add("muted-text");
            HBox emptyRow = new HBox(empty);
            emptyRow.setPadding(new Insets(8));
            rows.add(emptyRow);
        }
        return rows;
    }

    private List<Classroom> classroomsForTest(Test test) {
        return database.getAllClassrooms().stream()
                .filter(c -> c.getAssignedExams().contains(test))
                .toList();
    }

    private void openFilterDialog(String[] statusFilter, String[] classroomFilter, List<Classroom> myClassrooms,
                                   Runnable onApply) {
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("All", "Finished", "Unfinished");
        statusCombo.setValue(statusFilter[0]);

        ComboBox<String> classroomCombo = new ComboBox<>();
        classroomCombo.getItems().add("All");
        myClassrooms.forEach(c -> classroomCombo.getItems().add(c.getClassName()));
        classroomCombo.setValue(classroomFilter[0]);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Filter");
        dialog.setHeaderText("Filter this list");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        VBox content = new VBox(14,
                new VBox(6, new Label("Status:"), statusCombo),
                new VBox(6, new Label("Classroom:"), classroomCombo));
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.APPLY) {
                statusFilter[0] = statusCombo.getValue();
                classroomFilter[0] = classroomCombo.getValue();
                onApply.run();
            }
        });
    }

    private HBox buildTestRow(Test test, boolean finished, List<Classroom> classrooms) {
        HBox row = new HBox(20);
        row.getStyleClass().add("list-row");
        row.setAlignment(Pos.CENTER_LEFT);

        row.getChildren().add(iconBadgeFor(test));

        Label title = new Label(test.getTitle() + (finished ? "  •  Completed" : ""));
        title.getStyleClass().add("row-title");
        String classroomText = classrooms.isEmpty() ? ""
                : "  •  " + classrooms.stream().map(Classroom::getClassName).collect(Collectors.joining(", "));
        Label subtitle = new Label(test.getDurationLimit() + " min  •  " + test.getQuestions().size() + " questions"
                + classroomText);
        subtitle.getStyleClass().add("row-subtitle");
        VBox textBox = new VBox(4, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (finished) {
            var viewButton = UiComponents.outlineButton("View");
            viewButton.setOnAction(e -> openTestResult(test));
            row.getChildren().addAll(textBox, spacer, viewButton);
        } else {
            var startButton = UiComponents.primaryButton("Start  ›");
            startButton.setOnAction(e -> startTest(test));
            row.getChildren().addAll(textBox, spacer, startButton);
        }

        return row;
    }

    private void openTestResult(Test test) {
        GradingService gradingService = new GradingService(database);
        TestResultView resultView = new TestResultView(database, gradingService, student, test, () -> {
            onProfile = false;
            render();
        });
        root.setCenter(resultView.getRoot());
    }

    private javafx.scene.Node iconBadgeFor(Test test) {
        String upper = test.getTitle().toUpperCase();
        if (upper.contains("MATH") || upper.contains("CALC") || upper.contains("INTEGRAL")) {
            return UiComponents.iconBadge("π", "icon-badge-pink");
        }
        if (upper.contains("PHYS") || upper.contains("NEWTON")) {
            return UiComponents.iconBadge("⚛", "icon-badge-orange");
        }
        if (upper.contains("CS") || upper.contains("OOP") || upper.contains("JAVA") || upper.contains("RECURSION")
                || upper.contains("EXCEPTION") || upper.contains("COLLECTIONS")) {
            return UiComponents.iconBadge("</>", "icon-badge-green");
        }
        return UiComponents.iconBadge("📘", "icon-badge-blue");
    }

    private void startTest(Test test) {
        GradingService gradingService = new GradingService(database);
        TimerService timerService = new TimerService();
        ExamRunnerController controller = new ExamRunnerController(database, gradingService, timerService);

        try {
            controller.startExam(test.getTestID(), student);
        } catch (IllegalStateException ex) {
            controller.shutdown();
            Alert alert = new Alert(Alert.AlertType.INFORMATION, ex.getMessage());
            alert.setHeaderText("Already completed");
            alert.showAndWait();
            render();
            return;
        }
        FeedbackController feedbackController = new FeedbackController(database);
        FocusModeExamView examView = new FocusModeExamView(controller, feedbackController, test, student,
                (submission, score, timedOut) -> {
                    controller.shutdown();
                    render();
                });
        root.setCenter(examView.getRoot());
    }   
}
