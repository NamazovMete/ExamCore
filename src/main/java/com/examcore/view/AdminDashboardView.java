package com.examcore.view;

import java.util.List;
import java.util.stream.Collectors;

import com.examcore.controller.AdminController;
import com.examcore.controller.ExamAuthoringController;
import com.examcore.data.Database;
import com.examcore.model.Admin;
import com.examcore.model.Classroom;
import com.examcore.model.Role;
import com.examcore.model.Teacher;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import com.examcore.model.User;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;


public class AdminDashboardView {

    static final List<String> NAV_TABS = List.of("Pending Teachers", "All Users", "All Exams", "All Quizzes",
            "System Logs");

    private final Database database;
    private final Admin admin;
    private final Runnable onLogout;
    private final AdminController adminController;
    private final ExamAuthoringController examAuthoringController;
    private final BorderPane root;

    private int activeTab = 0;

    public AdminDashboardView(Database database, Admin admin, Runnable onLogout) {
        this.database = database;
        this.admin = admin;
        this.onLogout = onLogout;
        this.adminController = new AdminController(database);
        this.examAuthoringController = new ExamAuthoringController(database);
        this.root = new BorderPane();
        root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(
                NAV_TABS,
                activeTab,
                admin,
                idx -> {
                    activeTab = idx;
                    render();
                },
                () -> openUserDetail(admin, activeTab));

        VBox page = switch (activeTab) {
            case 1 -> buildAllUsersPage();
            case 2 -> buildExamsPage("All Exams", TestType.EXAM);
            case 3 -> buildExamsPage("All Quizzes", TestType.QUIZ);
            case 4 -> buildSystemLogsPage();
            default -> buildPendingTeachersPage();
        };

        var logoutButton = UiComponents.pinkButton("Log Out");
        logoutButton.setOnAction(e -> onLogout.run());
        HBox actions = new HBox(logoutButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(20, 0, 0, 0));

        VBox container = new VBox(28, nav, page, actions);
        container.setPadding(new Insets(28, 40, 28, 40));

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private VBox buildPendingTeachersPage() {
        Label title = UiComponents.pageTitle("Pending Teacher Verifications");

        VBox list = new VBox(16);
        for (Teacher teacher : adminController.getPendingTeachers()) {
            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            VBox textBox = new VBox(4);
            Label name = new Label(UiComponents.fullName(teacher));
            name.getStyleClass().add("row-title");
            Label email = new Label(teacher.getEmail() == null ? teacher.getUsername() : teacher.getEmail());
            email.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, email);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            var verifyButton = UiComponents.greenButton("Verify");
            verifyButton.setOnAction(e -> {
                adminController.verifyTeacher(admin, teacher);
                render();
            });

            row.getChildren().addAll(textBox, spacer, verifyButton);
            list.getChildren().add(row);
        }
        if (adminController.getPendingTeachers().isEmpty()) {
            Label empty = new Label("No teacher accounts are awaiting verification.");
            empty.getStyleClass().add("muted-text");
            list.getChildren().add(empty);
        }

        return new VBox(24, title, list);
    }

    private VBox buildAllUsersPage() {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("All Users");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("🔍 Search");
        search.setPrefWidth(220);
        header.getChildren().addAll(title, headerSpacer, search);

        VBox listContainer = new VBox();
        Runnable refresh = () -> listContainer.getChildren().setAll(buildUserList(search.getText()));
        search.textProperty().addListener((obs, oldVal, newVal) -> refresh.run());
        refresh.run();

        return new VBox(24, header, listContainer);
    }

    private VBox buildUserList(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        VBox list = new VBox(16);
        int matches = 0;

        for (User user : adminController.getAllUsers()) {
            String fullName = UiComponents.fullName(user);
            if (!normalizedQuery.isEmpty() && !fullName.toLowerCase().contains(normalizedQuery)
                    && !user.getUsername().toLowerCase().contains(normalizedQuery)
                    && !user.getRole().name().toLowerCase().contains(normalizedQuery)) {
                continue;
            }
            matches++;

            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            HBox identityArea = new HBox(20);
            identityArea.setAlignment(Pos.CENTER_LEFT);
            identityArea.setCursor(javafx.scene.Cursor.HAND);
            identityArea.setOnMouseClicked(e -> openUserDetail(user, 1));
            identityArea.getChildren().add(UiComponents.avatarView(user, 22));

            VBox textBox = new VBox(4);
            Label name = new Label(fullName);
            name.getStyleClass().add("row-title");
            String roleText = user.getRole().name();
            String statusText = user.isActive() ? "Active" : "Deactivated";
            if (user.getRole() == Role.TEACHER && user instanceof Teacher t) {
                statusText += t.isVerified() ? "" : " • Unverified";
            }
            Label meta = new Label(roleText + "  •  " + statusText + "  •  " + user.getUsername());
            meta.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, meta);
            identityArea.getChildren().add(textBox);
            row.getChildren().add(identityArea);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            var viewButton = UiComponents.outlineButton("View");
            viewButton.setOnAction(e -> openUserDetail(user, 1));

            if (user.getId() != admin.getId()) {
                if (user.isActive()) {
                    var deactivateButton = UiComponents.pinkButton("Deactivate");
                    deactivateButton.setOnAction(e -> {
                        adminController.deactivateUser(admin, user);
                        render();
                    });
                    row.getChildren().addAll(spacer, viewButton, deactivateButton);
                } else {
                    var reactivateButton = UiComponents.greenButton("Reactivate");
                    reactivateButton.setOnAction(e -> {
                        adminController.reactivateUser(admin, user);
                        render();
                    });
                    row.getChildren().addAll(spacer, viewButton, reactivateButton);
                }
            } else {
                Label youLabel = new Label("(you)");
                youLabel.getStyleClass().add("muted-text");
                row.getChildren().addAll(spacer, viewButton, youLabel);
            }

            list.getChildren().add(row);
        }

        if (matches == 0 && !normalizedQuery.isEmpty()) {
            Label empty = new Label("No users match \"" + query.trim() + "\".");
            empty.getStyleClass().add("muted-text");
            list.getChildren().add(empty);
        }

        return list;
    }

    private void openUserDetail(User user, int returnTab) {
        AdminUserDetailView detailView = new AdminUserDetailView(adminController, admin, user, () -> {
            activeTab = returnTab;
            render();
        }, onLogout);
        root.setCenter(detailView.getRoot());
    }

    // ---- All Exams / All Quizzes ----

    private VBox buildExamsPage(String title, TestType type) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = UiComponents.pageTitle(title);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        TextField search = new TextField();
        search.setPromptText("🔍 Search");
        search.setPrefWidth(220);
        header.getChildren().addAll(titleLabel, headerSpacer, search);

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
            if (test.getTestType() != type) {
                continue;
            }
            String ownerName = test.getOwner() == null ? "Unassigned" : UiComponents.fullName(test.getOwner());
            if (!normalizedQuery.isEmpty() && !test.getTitle().toLowerCase().contains(normalizedQuery)
                    && !ownerName.toLowerCase().contains(normalizedQuery)) {
                continue;
            }

            HBox row = new HBox(20);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            List<Classroom> classrooms = database.getAllClassrooms().stream()
                    .filter(c -> c.getAssignedExams().contains(test))
                    .toList();
            String classroomText = classrooms.isEmpty() ? ""
                    : "  •  " + classrooms.stream().map(Classroom::getClassName).collect(Collectors.joining(", "));

            VBox textBox = new VBox(4);
            Label name = new Label(test.getTitle());
            name.getStyleClass().add("row-title");
            Label subtitle = new Label(test.getTestType() + "  •  by " + ownerName + "  •  "
                    + test.getDurationLimit() + " min  •  " + test.getQuestions().size() + " questions" + classroomText);
            subtitle.getStyleClass().add("row-subtitle");
            textBox.getChildren().addAll(name, subtitle);

            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            var analyticsButton = UiComponents.outlineButton("📊");
            analyticsButton.setOnAction(e -> openAdminAnalytics(test));
            var editButton = UiComponents.outlineButton("✎");
            editButton.getStyleClass().add("btn-icon-green");
            editButton.setOnAction(e -> openAdminExamEditor(test));
            var deleteButton = UiComponents.outlineButton("🗑");
            deleteButton.getStyleClass().add("btn-icon-pink");
            deleteButton.setOnAction(e -> confirmDeleteTest(test));

            row.getChildren().addAll(textBox, rowSpacer, analyticsButton, editButton, deleteButton);
            rows.add(row);
        }

        if (rows.isEmpty()) {
            Label empty = new Label(type == TestType.QUIZ ? "No quizzes match your search."
                    : "No exams match your search.");
            empty.getStyleClass().add("muted-text");
            HBox emptyRow = new HBox(empty);
            emptyRow.setPadding(new Insets(8));
            rows.add(emptyRow);
        }
        return rows;
    }

    private void openAdminExamEditor(Test test) {
        int tabIndex = test.getTestType() == TestType.QUIZ ? 3 : 2;
        ExamEditorView editorView = new ExamEditorView(examAuthoringController, admin, NAV_TABS, tabIndex, null, test,
                test.getTestType(), () -> {
                    activeTab = tabIndex;
                    render();
                });
        root.setCenter(editorView.getRoot());
    }

    private void openAdminAnalytics(Test test) {
        int tabIndex = test.getTestType() == TestType.QUIZ ? 3 : 2;
        AnalyticsDetailView analyticsView = new AnalyticsDetailView(database, admin, NAV_TABS, tabIndex, test, () -> {
            activeTab = tabIndex;
            render();
        }, null);
        root.setCenter(analyticsView.getRoot());
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

    private VBox buildSystemLogsPage() {
        Label title = UiComponents.pageTitle("System Logs");

        List<String> entries = new java.util.ArrayList<>(admin.viewSystemLogs());
        entries.addAll(database.getSystemLogs());
        entries.sort(java.util.Comparator.reverseOrder());

        VBox logCard = UiComponents.card(10);
        for (String entry : entries) {
            Label entryLabel = new Label(entry);
            entryLabel.getStyleClass().add("row-subtitle");
            entryLabel.setWrapText(true);
            logCard.getChildren().add(entryLabel);
        }
        if (entries.isEmpty()) {
            Label empty = new Label("No system events recorded yet.");
            empty.getStyleClass().add("muted-text");
            logCard.getChildren().add(empty);
        }

        return new VBox(24, title, logCard);
    }
}
