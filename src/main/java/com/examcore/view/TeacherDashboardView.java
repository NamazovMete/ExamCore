package com.examcore.view;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.examcore.controller.ClassroomController;
import com.examcore.controller.ExamAuthoringController;
import com.examcore.controller.FeedbackController;
import com.examcore.data.Database;
import com.examcore.model.Classroom;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Teacher;
import com.examcore.model.Test;
import com.examcore.model.TestType;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class TeacherDashboardView {

    private static final List<String> NAV_TABS =
            List.of("Classrooms", "Exams", "Quizzes", "Analytics & Feedback", "Violations");

    private final Database database;
    private final Teacher teacher;
    private final Runnable onLogout;
    private final BorderPane root;

    private final ExamAuthoringController examAuthoringController;
    private final ClassroomController classroomController;
    private final FeedbackController feedbackController;

    private int activeTab = 0;
    private boolean onProfile = false;

    public TeacherDashboardView(Database database, Teacher teacher, Runnable onLogout) {
        this.database = database;
        this.teacher = teacher;
        this.onLogout = onLogout;
        this.examAuthoringController = new ExamAuthoringController(database);
        this.classroomController = new ClassroomController(database);
        this.feedbackController = new FeedbackController(database);
        this.root = new BorderPane();
        this.root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(
                NAV_TABS,
                onProfile ? -1 : activeTab,
                teacher,
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
                case 1 -> buildExamsPage("My Exams", TestType.EXAM);
                case 2 -> buildExamsPage("My Quizzes", TestType.QUIZ);
                case 3 -> buildAnalyticsPage();
                case 4 -> buildViolationsPage();
                default -> buildClassroomsPage();
            };
        }

        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        var scrollPane = new javafx.scene.control.ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    // Classrooms 

    private VBox buildClassroomsPage() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("My Classrooms");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var newClassroomButton = UiComponents.outlineButton("+ New Classroom");
        newClassroomButton.setOnAction(e -> openNewClassroomDialog());
        header.getChildren().addAll(title, spacer, newClassroomButton);

        VBox list = new VBox(16);
        for (Classroom classroom : teacher.getManagedClassrooms()) {
            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            VBox textBox = new VBox(4);
            Label name = new Label(classroom.getClassName());
            name.getStyleClass().add("row-title");
            Label count = new Label("Total participants: " + classroom.getParticipantCount());
            count.getStyleClass().add("row-subtitle");
            List<Test> assigned = classroom.getAssignedExams();
            String assignedText = assigned.isEmpty() ? "No exams/quizzes assigned yet."
                    : "Assigned: " + assigned.stream().map(Test::getTitle).collect(Collectors.joining(", "));
            Label assignedLabel = new Label(assignedText);
            assignedLabel.getStyleClass().add("muted-text");
            assignedLabel.setWrapText(true);
            textBox.getChildren().addAll(name, count, assignedLabel);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var viewStudentsButton = UiComponents.outlineButton("View Students");
            viewStudentsButton.setOnAction(e -> openViewStudentsDialog(classroom));

            var addStudentButton = UiComponents.outlineButton("Add Student");
            addStudentButton.setOnAction(e -> openAddStudentDialog(classroom));

            row.getChildren().addAll(textBox, rowSpacer, viewStudentsButton, addStudentButton);
            list.getChildren().add(row);
        }
        if (teacher.getManagedClassrooms().isEmpty()) {
            Label empty = new Label("No classrooms yet. Create one to get started.");
            empty.getStyleClass().add("muted-text");
            list.getChildren().add(empty);
        }

        return new VBox(24, header, list);
    }

    private void openNewClassroomDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Classroom");
        dialog.setHeaderText("Create a new classroom");
        dialog.setContentText("Class name:");
        dialog.showAndWait().ifPresent(className -> {
            if (className.isBlank()) {
                return;
            }
            String classID = generateClassID(className);
            try {
                classroomController.createClassroom(teacher, classID, className.trim());
                render();
            } catch (IllegalStateException | IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private String generateClassID(String className) {
        String base = className.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        String candidate = base;
        int suffix = 1;
        while (database.findClassroomById(candidate).isPresent()) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private void openAddStudentDialog(Classroom classroom) {
        List<Student> candidates = database.getAllUsers().stream()
                .filter(u -> u instanceof Student)
                .map(u -> (Student) u)
                .filter(s -> !classroom.getStudentList().contains(s))
                .sorted(Comparator.comparing(UiComponents::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (candidates.isEmpty()) {
            showInfo("There are no students available to add (everyone is already enrolled, or no students are registered yet).");
            return;
        }

        TextField search = new TextField();
        search.setPromptText("🔍 Search by name or username");

        javafx.scene.control.ListView<Student> listView = new javafx.scene.control.ListView<>();
        listView.getItems().setAll(candidates);
        listView.setPrefHeight(220);
        listView.setPrefWidth(320);
        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Student s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : UiComponents.fullName(s) + " (" + s.getUsername() + ")");
            }
        });
        listView.getSelectionModel().selectFirst();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Student");
        dialog.setHeaderText("Add a student to " + classroom.getClassName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        search.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.trim().toLowerCase();
            List<Student> filtered = candidates.stream()
                    .filter(s -> query.isEmpty()
                            || UiComponents.fullName(s).toLowerCase().contains(query)
                            || s.getUsername().toLowerCase().contains(query))
                    .toList();
            listView.getItems().setAll(filtered);
            if (!filtered.isEmpty()) {
                listView.getSelectionModel().selectFirst();
            }
        });
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).fire();
            }
        });

        VBox content = new VBox(10, search, listView);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(search::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Student selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                classroomController.addStudent(teacher, classroom, selected);
                render();
            }
        }
    }

    private void openViewStudentsDialog(Classroom classroom) {
        openViewStudentsDialog(classroom, "");
    }

    private void openViewStudentsDialog(Classroom classroom, String initialQuery) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Students");
        dialog.setHeaderText(classroom.getClassName() + " — " + classroom.getParticipantCount() + " student(s)");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField search = new TextField(initialQuery == null ? "" : initialQuery);
        search.setPromptText("🔍 Search by name or username");

        VBox rosterList = new VBox(10);
        Runnable refresh = () -> rosterList.getChildren()
                .setAll(buildStudentRosterRows(classroom, dialog, search.getText()));
        search.textProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        var scrollPane = new javafx.scene.control.ScrollPane(rosterList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(380, 260);

        VBox content = new VBox(10, search, scrollPane);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(search::requestFocus);

        dialog.showAndWait();
    }

    private List<HBox> buildStudentRosterRows(Classroom classroom, Dialog<Void> dialog, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<Student> roster = classroom.getStudentList().stream()
                .filter(s -> normalizedQuery.isEmpty()
                        || UiComponents.fullName(s).toLowerCase().contains(normalizedQuery)
                        || s.getUsername().toLowerCase().contains(normalizedQuery))
                .sorted(Comparator.comparing(UiComponents::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<HBox> rows = new java.util.ArrayList<>();
        if (roster.isEmpty()) {
            Label empty = new Label(classroom.getStudentList().isEmpty()
                    ? "No students enrolled yet."
                    : "No students match your search.");
            empty.getStyleClass().add("muted-text");
            HBox emptyRow = new HBox(empty);
            emptyRow.setPadding(new Insets(8));
            rows.add(emptyRow);
            return rows;
        }

        for (Student student : roster) {
            HBox row = new HBox(12);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(UiComponents.fullName(student) + " (" + student.getUsername() + ")");
            name.getStyleClass().add("row-title");

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var removeButton = UiComponents.outlineButton("Remove");
            removeButton.setOnAction(e -> confirmRemoveStudent(classroom, student, dialog, query));

            row.getChildren().addAll(name, rowSpacer, removeButton);
            rows.add(row);
        }
        return rows;
    }

    private void confirmRemoveStudent(Classroom classroom, Student student, Dialog<Void> dialog, String currentQuery) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + UiComponents.fullName(student) + " from " + classroom.getClassName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Remove Student");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.YES) {
                classroomController.removeStudent(classroom, student);
                dialog.close();
                render();
                openViewStudentsDialog(classroom, currentQuery);
            }
        });
    }

    // Exams / Quizzes

    private VBox buildExamsPage(String title, TestType type) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = UiComponents.pageTitle(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("🔍 Search");
        search.setPrefWidth(220);
        var newButton = UiComponents.outlineButton(type == TestType.QUIZ ? "New Quiz" : "New Exam");
        newButton.setOnAction(e -> openExamEditor(null, type));
        header.getChildren().addAll(titleLabel, spacer, search, newButton);

        VBox listContainer = new VBox(16);
        Runnable refresh = () -> listContainer.getChildren().setAll(buildExamRows(search.getText(), type));
        search.textProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        return new VBox(24, header, listContainer);
    }

    private List<HBox> buildExamRows(String query, TestType type) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<HBox> rows = new java.util.ArrayList<>();

        for (Test test : database.getAllTests()) {
            if (!teacher.equals(test.getOwner()) || test.getTestType() != type) {
                continue;
            }
            if (!normalizedQuery.isEmpty() && !test.getTitle().toLowerCase().contains(normalizedQuery)) {
                continue;
            }

            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            List<Classroom> classrooms = classroomsForTest(test);
            String classroomText = classrooms.isEmpty() ? ""
                    : "  •  " + classrooms.stream().map(Classroom::getClassName).collect(Collectors.joining(", "));

            VBox textBox = new VBox(4);
            Label name = new Label(test.getTitle());
            name.getStyleClass().add("row-title");
            Label subtitle = new Label(test.getDurationLimit() + " min  •  " + test.getQuestions().size()
                    + " questions" + classroomText);
            subtitle.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, subtitle);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var assignButton = UiComponents.outlineButton("Assign");
            assignButton.setOnAction(e -> openAssignDialog(test));
            var editButton = UiComponents.outlineButton("✎");
            editButton.getStyleClass().add("btn-icon-green");
            editButton.setOnAction(e -> openExamEditor(test, test.getTestType()));
            var deleteButton = UiComponents.outlineButton("🗑");
            deleteButton.getStyleClass().add("btn-icon-pink");
            deleteButton.setOnAction(e -> confirmDeleteTest(test));

            row.getChildren().addAll(textBox, rowSpacer, assignButton, editButton, deleteButton);
            rows.add(row);
        }

        if (rows.isEmpty()) {
            Label empty = new Label(type == TestType.QUIZ ? "No quizzes yet. Create one to get started."
                    : "No exams yet. Create one to get started.");
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

    private void openExamEditor(Test existingTest, TestType type) {
        int tabIndex = type == TestType.QUIZ ? 2 : 1;
        ExamEditorView editorView = new ExamEditorView(examAuthoringController, teacher, NAV_TABS, tabIndex, teacher,
                existingTest, type, () -> {
            activeTab = tabIndex;
            onProfile = false;
            render();
        });
        root.setCenter(editorView.getRoot());
    }

    private void confirmDeleteTest(Test test) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + test.getTitle() + "'? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete " + (test.getTestType() == TestType.QUIZ ? "Quiz" : "Exam"));
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.YES) {
                examAuthoringController.deleteTest(test);
                render();
            }
        });
    }

    private void openAssignDialog(Test test) {
        if (teacher.getManagedClassrooms().isEmpty()) {
            showInfo("You don't manage any classrooms yet. Create one first from the Classrooms tab.");
            return;
        }

        ComboBox<Classroom> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(teacher.getManagedClassrooms());
        comboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Classroom c) {
                return c == null ? "" : c.getClassName();
            }

            @Override
            public Classroom fromString(String string) {
                return null;
            }
        });
        comboBox.getSelectionModel().selectFirst();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Assign " + (test.getTestType() == TestType.QUIZ ? "Quiz" : "Exam"));
        dialog.setHeaderText("Assign '" + test.getTitle() + "' to a classroom");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        VBox content = new VBox(10, new Label("Classroom:"), comboBox);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Classroom selected = comboBox.getValue();
            if (selected != null) {
                classroomController.assignTest(teacher, test, selected);
                showInfo("Assigned to " + selected.getClassName() + " (" + selected.getParticipantCount()
                        + " student(s)).");
            }
        }
    }

    // ---- Analytics & Feedback ----

    private VBox buildAnalyticsPage() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("Analytics & Feedback");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All", "Exams", "Quizzes");
        typeFilter.setValue("All");
        header.getChildren().addAll(title, headerSpacer, typeFilter);

        VBox list = new VBox(16);
        Runnable refresh = () -> list.getChildren().setAll(buildAnalyticsRows(typeFilter.getValue()));
        typeFilter.valueProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        return new VBox(24, header, list);
    }

    private List<HBox> buildAnalyticsRows(String typeFilter) {
        List<HBox> rows = new java.util.ArrayList<>();
        for (Test test : database.getAllTests()) {
            if (!teacher.equals(test.getOwner())) {
                continue;
            }
            if (typeFilter.equals("Exams") && test.getTestType() != TestType.EXAM) {
                continue;
            }
            if (typeFilter.equals("Quizzes") && test.getTestType() != TestType.QUIZ) {
                continue;
            }

            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(test.getTitle());
            name.getStyleClass().add("row-title");

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var viewButton = UiComponents.primaryButton("View Analytics");
            viewButton.setOnAction(e -> openAnalytics(test));

            row.getChildren().addAll(name, rowSpacer, viewButton);
            rows.add(row);
        }

        if (rows.isEmpty()) {
            Label empty = new Label("No tests match this filter.");
            empty.getStyleClass().add("muted-text");
            HBox emptyRow = new HBox(empty);
            emptyRow.setPadding(new Insets(8));
            rows.add(emptyRow);
        }
        return rows;
    }

    private void openAnalytics(Test test) {
        AnalyticsDetailView analyticsView = new AnalyticsDetailView(database, teacher, NAV_TABS, 3, test, () -> {
            activeTab = 3;
            onProfile = false;
            render();
        }, this::openFeedbackList);
        root.setCenter(analyticsView.getRoot());
    }

    private void openFeedbackList(Test test) {
        FeedbackListView feedbackView = new FeedbackListView(database, feedbackController, teacher, test, NAV_TABS, 3,
                () -> openAnalytics(test));
        root.setCenter(feedbackView.getRoot());
    }

    // Exam Violations

    private VBox buildViolationsPage() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("Exam Violations");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("All", "Exams", "Quizzes");
        typeFilter.setValue("All");
        header.getChildren().addAll(title, headerSpacer, typeFilter);

        VBox list = new VBox(16);
        Runnable refresh = () -> list.getChildren().setAll(buildViolationRows(typeFilter.getValue()));
        typeFilter.valueProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        return new VBox(24, header, list);
    }

    private List<HBox> buildViolationRows(String typeFilter) {
        List<HBox> rows = new java.util.ArrayList<>();

        List<Submission> violations = database.getAllSubmissions().stream()
                .filter(s -> teacher.equals(s.getTest().getOwner()))
                .filter(s -> s.getFocusLossCount() > 0)
                .filter(s -> typeFilter.equals("All")
                        || (typeFilter.equals("Exams") && s.getTest().getTestType() == TestType.EXAM)
                        || (typeFilter.equals("Quizzes") && s.getTest().getTestType() == TestType.QUIZ))
                .sorted(Comparator.comparingInt(Submission::getFocusLossCount).reversed())
                .toList();

        for (Submission submission : violations) {
            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            row.getChildren().add(UiComponents.iconBadge("🚩", "icon-badge-pink"));

            List<Classroom> classrooms = classroomsForStudentAndTest(submission.getStudent(), submission.getTest());
            String classroomText = classrooms.isEmpty() ? "no classroom on record"
                    : classrooms.stream().map(Classroom::getClassName).collect(Collectors.joining(", "));
            int count = submission.getFocusLossCount();

            VBox textBox = new VBox(4);
            Label name = new Label(UiComponents.fullName(submission.getStudent()) + " left the screen during '"
                    + submission.getTest().getTitle() + "'");
            name.getStyleClass().add("row-title");
            Label subtitle = new Label(count + (count == 1 ? " time" : " times") + "  •  " + classroomText);
            subtitle.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, subtitle);

            row.getChildren().add(textBox);
            rows.add(row);
        }

        if (rows.isEmpty()) {
            Label empty = new Label("No focus-loss violations recorded.");
            empty.getStyleClass().add("muted-text");
            HBox emptyRow = new HBox(empty);
            emptyRow.setPadding(new Insets(8));
            rows.add(emptyRow);
        }
        return rows;
    }

    // The classrooms through which this student was assigned this test
    private List<Classroom> classroomsForStudentAndTest(Student student, Test test) {
        return database.getAllClassrooms().stream()
                .filter(c -> c.getStudentList().contains(student) && c.getAssignedExams().contains(test))
                .toList();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText("Couldn't complete that action");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // Profile

    private VBox buildProfilePage() {
        Label title = UiComponents.pageTitle("Profile Settings");

        Label pictureLabel = new Label("Picture");
        pictureLabel.getStyleClass().add("field-label");
        var avatar = UiComponents.avatarView(teacher, 60);
        var uploadButton = UiComponents.outlineButton("Upload Picture");
        uploadButton.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Choose Profile Picture");
            chooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            java.io.File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
            if (file != null) {
                teacher.setProfilePicturePath(file.getAbsolutePath());
                render();
            }
        });
        VBox pictureBox = new VBox(10, pictureLabel, avatar, uploadButton);

        TextField nameField = labeledField("Name", teacher.getFirstName());
        TextField middleField = labeledField("Middle name", teacher.getMiddleName());
        TextField surnameField = labeledField("Surname", teacher.getLastName());
        TextField schoolField = labeledField("School", teacher.getSchool());
        TextField departmentField = labeledField("Department", teacher.getDepartment());
        TextField gradeField = labeledField("Grade", "N/A");
        gradeField.setEditable(false);
        TextField emailField = labeledField("Email", teacher.getEmail());
        TextField roleField = labeledField("Role", "Teacher");
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
            teacher.setFirstName(blankToNull(nameField.getText()));
            teacher.setMiddleName(blankToNull(middleField.getText()));
            teacher.setLastName(blankToNull(surnameField.getText()));
            teacher.setSchool(blankToNull(schoolField.getText()));
            teacher.setDepartment(blankToNull(departmentField.getText()));
            teacher.setEmail(blankToNull(emailField.getText()));
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
        return new VBox(6, label, field);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}