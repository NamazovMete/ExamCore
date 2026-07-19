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

    private VBox buildLeaderboardPage() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = UiComponents.pageTitle("Leaderboard");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("🔍 Search");
        search.setPrefWidth(220);
        header.getChildren().addAll(titleLabel, spacer, search);

        VBox tableContainer = new VBox();
        Runnable refresh = () -> tableContainer.getChildren().setAll(buildLeaderboardTable(search.getText()));
        search.textProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        return new VBox(24, header, tableContainer);
    }

    private VBox buildLeaderboardTable(String query) {
        LeaderBoard leaderBoard = database.getLeaderBoard();
        List<Student> ranked = leaderBoard.fetchTopStudents(Integer.MAX_VALUE);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();

        VBox table = new VBox(2);
        HBox headerRow = new HBox();
        headerRow.getStyleClass().add("leaderboard-header");
        headerRow.setPadding(new Insets(14, 20, 14, 20));
        headerRow.getChildren().addAll(
                columnLabel("Rank", 90), columnLabel("Student", 420), columnLabel("Exam", 120),
                columnLabel("Quiz", 120), columnLabel("Total", 120));
        table.getChildren().add(headerRow);

        int rank = 1;
        int matches = 0;
        for (Student s : ranked) {
            int currentRank = rank++;
            String fullName = UiComponents.fullName(s);
            if (!normalizedQuery.isEmpty() && !fullName.toLowerCase().contains(normalizedQuery)
                    && !s.getUsername().toLowerCase().contains(normalizedQuery)) {
                continue;
            }
            matches++;

            HBox row = new HBox();
            row.getStyleClass().add(s.equals(student) ? "leaderboard-row-highlight" : "leaderboard-row");
            row.setPadding(new Insets(14, 20, 14, 20));
            String name = s.equals(student) ? fullName + " (me)" : fullName;
            row.getChildren().addAll(
                    columnLabel(String.valueOf(currentRank), 90), columnLabel(name, 420),
                    columnLabel(String.valueOf(s.getExamScore()), 120),
                    columnLabel(String.valueOf(s.getQuizScore()), 120),
                    columnLabel(String.valueOf(s.getTotalScore()), 120));
            table.getChildren().add(row);
        }

        if (matches == 0 && !normalizedQuery.isEmpty()) {
            Label empty = new Label("No students match \"" + query.trim() + "\".");
            empty.getStyleClass().add("muted-text");
            empty.setPadding(new Insets(14, 20, 14, 20));
            table.getChildren().add(empty);
        }

        VBox card = UiComponents.card(0);
        card.getChildren().add(table);
        card.setPadding(Insets.EMPTY);
        return card;
    }

    private Label columnLabel(String text, double width) {
        Label label = new Label(text);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.getStyleClass().add("row-title");
        return label;
    }

    private VBox buildProfilePage() {
        Label title = UiComponents.pageTitle("Profile Settings");

        Label pictureLabel = new Label("Picture");
        pictureLabel.getStyleClass().add("field-label");
        var avatar = UiComponents.avatarView(student, 60);
        var uploadButton = UiComponents.outlineButton("Upload Picture");
        uploadButton.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Choose Profile Picture");
            chooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            java.io.File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
            if (file != null) {
                student.setProfilePicturePath(file.getAbsolutePath());
                render();
            }
        });
        VBox pictureBox = new VBox(10, pictureLabel, avatar, uploadButton);

        TextField nameField = labeledField("Name", student.getFirstName());
        TextField middleField = labeledField("Middle name", student.getMiddleName());
        TextField surnameField = labeledField("Surname", student.getLastName());
        TextField schoolField = labeledField("School", student.getSchool());
        TextField departmentField = labeledField("Department", student.getDepartment());
        TextField gradeField = labeledField("Grade", student.getGrade());
        TextField emailField = labeledField("Email", student.getEmail());
        TextField roleField = labeledField("Role", "Student");
        roleField.setEditable(false);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(28);
        grid.setVgap(18);
        grid.add(fieldBox(nameField), 1, 0);
        grid.add(fieldBox(middleField), 2, 0);
        grid.add(fieldBox(surnameField), 3, 0);
        grid.add(fieldBox(schoolField), 1, 1);
        grid.add(fieldBox(departmentField), 2, 1);
        grid.add(fieldBox(gradeField), 3, 1);
        grid.add(fieldBox(emailField), 1, 2);
        grid.add(fieldBox(roleField), 2, 2);

        var saveButton = UiComponents.greenButton("Save Changes");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(e -> {
            student.setFirstName(blankToNull(nameField.getText()));
            student.setMiddleName(blankToNull(middleField.getText()));
            student.setLastName(blankToNull(surnameField.getText()));
            student.setSchool(blankToNull(schoolField.getText()));
            student.setDepartment(blankToNull(departmentField.getText()));
            student.setGrade(blankToNull(gradeField.getText()));
            student.setEmail(blankToNull(emailField.getText()));
            render();
        });

        var logoutButton = UiComponents.pinkButton("Log Out");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setOnAction(e -> onLogout.run());

        VBox actions = new VBox(12, saveButton, logoutButton);
        actions.setMaxWidth(220);

        VBox left = new VBox(24, pictureBox, actions);

        HBox layout = new HBox(48, left, grid);
        return new VBox(28, title, layout);
    }

    private TextField labeledField(String label, String value) {
        TextField field = new TextField(value == null ? "" : value);
        field.setUserData(label);
        return field;
    }

    private VBox fieldBox(TextInputControl field) {
        Label label = new Label((String) field.getUserData());
        label.getStyleClass().add("field-label");
        field.setPrefWidth(260);
        VBox box = new VBox(6, label, field);
        return box;
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private VBox buildActivityPage() {
        Label title = UiComponents.pageTitle("Personal Activity");

        VBox heatmapCard = UiComponents.card(16);
        Label heatmapTitle = new Label("Activity Heatmap (last 12 weeks)");
        heatmapTitle.getStyleClass().add("section-title");
        heatmapTitle.setWrapText(true);

        ActivityLog log = student.getActivityLog();
        Map<LocalDate, Integer> heatmap = log.generateHeatMap(12);

        LocalDate cutoff = LocalDate.now().minusWeeks(12);
        Map<LocalDate, Integer> examCounts = new java.util.HashMap<>();
        Map<LocalDate, Integer> quizCounts = new java.util.HashMap<>();
        for (Submission submission : log.getCompletionHistory()) {
            if (submission.getSubmittedAt() == null) {
                continue;
            }
            LocalDate day = submission.getSubmittedAt().toLocalDate();
            if (day.isBefore(cutoff)) {
                continue;
            }
            Map<LocalDate, Integer> target = submission.getTest().getTestType() == TestType.QUIZ
                    ? quizCounts : examCounts;
            target.merge(day, 1, Integer::sum);
        }

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        LocalDate start = LocalDate.now().minusWeeks(11).with(java.time.DayOfWeek.MONDAY);
        for (int week = 0; week < 12; week++) {
            for (int day = 0; day < 7; day++) {
                LocalDate cellDate = start.plusWeeks(week).plusDays(day);
                int count = heatmap.getOrDefault(cellDate, 0);
                Rectangle cell = new Rectangle(20, 20);
                cell.setArcWidth(6);
                cell.setArcHeight(6);
                cell.setFill(heatColor(count));
                if (count > 0) {
                    int examCount = examCounts.getOrDefault(cellDate, 0);
                    int quizCount = quizCounts.getOrDefault(cellDate, 0);
                    List<String> parts = new java.util.ArrayList<>();
                    if (examCount > 0) {
                        parts.add(examCount + (examCount == 1 ? " exam" : " exams"));
                    }
                    if (quizCount > 0) {
                        parts.add(quizCount + (quizCount == 1 ? " quiz" : " quizzes"));
                    }
                    Tooltip tooltip = new Tooltip(String.join(", ", parts) + " done");
                    tooltip.setShowDelay(javafx.util.Duration.millis(50));
                    tooltip.setShowDuration(javafx.util.Duration.seconds(10));
                    Tooltip.install(cell, tooltip);
                }
                grid.add(cell, week, day);
            }
        }
        heatmapCard.getChildren().addAll(heatmapTitle, grid);

        VBox historyCard = UiComponents.card(16);
        Label historyTitle = new Label("Completed Exams & Quizzes");
        historyTitle.getStyleClass().add("section-title");

        javafx.scene.layout.GridPane historyGrid = new javafx.scene.layout.GridPane();
        historyGrid.setHgap(24);
        historyGrid.setVgap(10);
        String[] cols = {"Exam Name", "Date", "Status", "Point"};
        for (int i = 0; i < cols.length; i++) {
            Label headerLabel = new Label(cols[i]);
            headerLabel.getStyleClass().add("row-subtitle");
            historyGrid.add(headerLabel, i, 0);
        }
        int rowIndex = 1;
        for (Submission submission : log.getCompletionHistory()) {
            historyGrid.add(new Label(submission.getTest().getTitle()), 0, rowIndex);
            String dateText = submission.getSubmittedAt() != null
                    ? submission.getSubmittedAt().format(DATE_FORMAT) : "-";
            historyGrid.add(new Label(dateText), 1, rowIndex);
            historyGrid.add(new Label(submission.isGraded() ? "Completed" : "Pending"), 2, rowIndex);
            historyGrid.add(new Label(String.valueOf(submission.getScore())), 3, rowIndex);
            rowIndex++;
        }
        historyCard.getChildren().addAll(historyTitle, historyGrid);

        HBox columns = new HBox(24, heatmapCard, historyCard);
        HBox.setHgrow(heatmapCard, Priority.ALWAYS);
        HBox.setHgrow(historyCard, Priority.ALWAYS);

        return new VBox(24, title, columns);
    }

    private Color heatColor(int count) {
        return switch (Math.min(count, 3)) {
            case 0 -> Color.web("#E3E4E6");
            case 1 -> Color.web("#9BEAF0");
            case 2 -> Color.web("#3FD8E6");
            default -> Color.web("#0FB8C9");
        };
    }
}
