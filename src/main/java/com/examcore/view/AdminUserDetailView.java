package com.examcore.view;

import com.examcore.controller.AdminController;
import com.examcore.model.Admin;
import com.examcore.model.Role;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import com.examcore.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Admin-only view of a single user's profile: lets an admin inspect and
 * edit any account's details, verify a pending teacher, and (de)activate
 * the account, matching "Admin should be able to click a user's profile
 * and view it / make changes to that user".
 */
public class AdminUserDetailView {

    private final AdminController adminController;
    private final Admin admin;
    private final User targetUser;
    private final Runnable onBack;
    private final Runnable onLogout;
    private final BorderPane root = new BorderPane();

    public AdminUserDetailView(AdminController adminController, Admin admin, User targetUser, Runnable onBack,
                                Runnable onLogout) {
        this.adminController = adminController;
        this.admin = admin;
        this.targetUser = targetUser;
        this.onBack = onBack;
        this.onLogout = onLogout;
        root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(AdminDashboardView.NAV_TABS, 1, admin, idx -> onBack.run(), onBack);

        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = UiComponents.pageTitle("Editing " + UiComponents.fullName(targetUser));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var backButton = UiComponents.pinkButton("‹ Back");
        backButton.setOnAction(e -> onBack.run());
        header.getChildren().addAll(title, spacer, backButton);

        VBox pictureBox = new VBox(10, sectionLabel("Picture"), UiComponents.avatarView(targetUser, 60));

        TextField nameField = labeledField("Name", targetUser.getFirstName());
        TextField middleField = labeledField("Middle name", targetUser.getMiddleName());
        TextField surnameField = labeledField("Surname", targetUser.getLastName());
        TextField schoolField = labeledField("School", targetUser.getSchool());
        TextField departmentField = labeledField("Department", targetUser.getDepartment());
        TextField gradeField = labeledField("Grade", targetUser instanceof Student s ? s.getGrade() : "N/A");
        gradeField.setEditable(targetUser instanceof Student);
        TextField emailField = labeledField("Email", targetUser.getEmail());
        TextField usernameField = labeledField("Username", targetUser.getUsername());
        usernameField.setEditable(false);
        TextField roleField = labeledField("Role", targetUser.getRole().name());
        roleField.setEditable(false);

        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(18);
        grid.add(fieldBox(nameField), 0, 0);
        grid.add(fieldBox(middleField), 1, 0);
        grid.add(fieldBox(surnameField), 2, 0);
        grid.add(fieldBox(schoolField), 0, 1);
        grid.add(fieldBox(departmentField), 1, 1);
        grid.add(fieldBox(gradeField), 2, 1);
        grid.add(fieldBox(emailField), 0, 2);
        grid.add(fieldBox(usernameField), 1, 2);
        grid.add(fieldBox(roleField), 2, 2);

        var saveButton = UiComponents.greenButton("Save Changes");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setOnAction(e -> {
            targetUser.setFirstName(blankToNull(nameField.getText()));
            targetUser.setMiddleName(blankToNull(middleField.getText()));
            targetUser.setLastName(blankToNull(surnameField.getText()));
            targetUser.setSchool(blankToNull(schoolField.getText()));
            targetUser.setDepartment(blankToNull(departmentField.getText()));
            targetUser.setEmail(blankToNull(emailField.getText()));
            if (targetUser instanceof Student s) {
                s.setGrade(blankToNull(gradeField.getText()));
            }
            Alert confirm = new Alert(Alert.AlertType.INFORMATION, "Changes saved.");
            confirm.setHeaderText(null);
            confirm.showAndWait();
            render();
        });

        VBox actions = new VBox(12, saveButton);
        actions.setMaxWidth(240);

        if (targetUser.getRole() == Role.TEACHER && targetUser instanceof Teacher teacher && !teacher.isVerified()) {
            var verifyButton = UiComponents.greenButton("Verify Teacher");
            verifyButton.setMaxWidth(Double.MAX_VALUE);
            verifyButton.setOnAction(e -> {
                adminController.verifyTeacher(admin, teacher);
                render();
            });
            actions.getChildren().add(verifyButton);
        }

        boolean isSelf = targetUser.getId() == admin.getId();
        if (!isSelf) {
            if (targetUser.isActive()) {
                var deactivateButton = UiComponents.pinkButton("Deactivate Account");
                deactivateButton.setMaxWidth(Double.MAX_VALUE);
                deactivateButton.setOnAction(e -> {
                    adminController.deactivateUser(admin, targetUser);
                    render();
                });
                actions.getChildren().add(deactivateButton);
            } else {
                var reactivateButton = UiComponents.greenButton("Reactivate Account");
                reactivateButton.setMaxWidth(Double.MAX_VALUE);
                reactivateButton.setOnAction(e -> {
                    adminController.reactivateUser(admin, targetUser);
                    render();
                });
                actions.getChildren().add(reactivateButton);
            }
        } else {
            var logoutButton = UiComponents.pinkButton("Log Out");
            logoutButton.setMaxWidth(Double.MAX_VALUE);
            logoutButton.setOnAction(e -> onLogout.run());
            actions.getChildren().add(logoutButton);
        }

        Label statusLabel = new Label("Status: " + (targetUser.isActive() ? "Active" : "Deactivated")
                + (targetUser instanceof Teacher t ? "  •  " + (t.isVerified() ? "Verified" : "Unverified") : ""));
        statusLabel.getStyleClass().add("muted-text");

        VBox left = new VBox(24, pictureBox, statusLabel, actions);
        HBox layout = new HBox(48, left, grid);

        VBox page = new VBox(24, header, layout);
        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        var scrollPane = new javafx.scene.control.ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
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
