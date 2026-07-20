package com.examcore.view;

import com.examcore.controller.AuthenticationController;
import com.examcore.controller.AuthenticationController.AuthenticationResult;
import com.examcore.controller.AuthenticationController.RegistrationResult;
import com.examcore.model.Role;
import com.examcore.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Self-registration screen for new Student or Teacher accounts. On success
 * it immediately logs the new account in (via {@link AuthenticationController#login})
 * and hands the resulting session to the same success callback the login
 * screen uses, so the caller only needs one dashboard-routing code path.
 */
public class RegisterView {

    private final StackPane root;

    public RegisterView(AuthenticationController authController, Consumer<AuthenticationResult> onSuccess,
                         Runnable onBackToLogin) {
        VBox logo = new VBox(4);
        logo.setAlignment(Pos.CENTER);
        Label subtitle = new Label("Create your account");
        subtitle.getStyleClass().add("muted-text");
        logo.getChildren().addAll(UiComponents.logoImageView(40), subtitle);

        TextField firstNameField = new TextField();
        firstNameField.setPromptText("First name");
        TextField lastNameField = new TextField();
        lastNameField.setPromptText("Surname");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password (min. 6 characters)");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");

        ToggleGroup roleGroup = new ToggleGroup();
        RadioButton studentRadio = new RadioButton("Student");
        studentRadio.setToggleGroup(roleGroup);
        studentRadio.setSelected(true);
        studentRadio.setUserData(Role.STUDENT);
        RadioButton teacherRadio = new RadioButton("Teacher");
        teacherRadio.setToggleGroup(roleGroup);
        teacherRadio.setUserData(Role.TEACHER);
        HBox roleBox = new HBox(20, studentRadio, teacherRadio);
        roleBox.setAlignment(Pos.CENTER_LEFT);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        var registerButton = UiComponents.primaryButton("Register");
        registerButton.setMaxWidth(Double.MAX_VALUE);

        registerButton.setOnAction(e -> {
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String username = usernameField.getText();
            String email = emailField.getText();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();
            Role role = (Role) roleGroup.getSelectedToggle().getUserData();

            if (password == null || !password.equals(confirmPassword)) {
                errorLabel.setText("Passwords do not match");
                errorLabel.setVisible(true);
                return;
            }

            RegistrationResult result = authController.register(username, email, password, role);
            if (!result.isSuccess()) {
                errorLabel.setText(result.getMessage());
                errorLabel.setVisible(true);
                return;
            }

            User newUser = result.getUser().orElseThrow();
            if (firstName != null && !firstName.isBlank()) {
                newUser.setFirstName(firstName);
            }
            if (lastName != null && !lastName.isBlank()) {
                newUser.setLastName(lastName);
            }

            errorLabel.setVisible(false);
            AuthenticationResult loginResult = authController.login(username, password);
            if (loginResult.isSuccess()) {
                onSuccess.accept(loginResult);
            } else {
                Alert info = new Alert(Alert.AlertType.INFORMATION,
                        "Your account was created, but you can't sign in yet: " + loginResult.getMessage());
                info.setHeaderText("Account created");
                info.showAndWait();
                onBackToLogin.run();
            }
        });

        Hyperlink backToLoginLink = new Hyperlink("Already have an account? Log in");
        backToLoginLink.setOnAction(e -> onBackToLogin.run());

        VBox form = new VBox(10,
                firstNameField, lastNameField, usernameField, emailField,
                passwordField, confirmPasswordField, roleBox);
        form.setFillWidth(true);
        form.setPrefWidth(320);

        VBox card = new VBox(20, logo, form, errorLabel, registerButton, backToLoginLink);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(440);
        VBox.setVgrow(registerButton, Priority.NEVER);

        root = new StackPane(card);
        root.getStyleClass().add("app-bg");
        root.setPadding(new Insets(40));
    }

    public StackPane getRoot() {
        return root;
    }
}
