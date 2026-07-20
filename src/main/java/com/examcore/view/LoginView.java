package com.examcore.view;

import com.examcore.controller.AuthenticationController;
import com.examcore.controller.AuthenticationController.AuthenticationResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Login screen: credentials are handed to {@link AuthenticationController},
 * which parses them uniformly across roles and reports back where the
 * resulting session should be routed.
 */
public class LoginView {

    private final StackPane root;

    public LoginView(AuthenticationController authController, Consumer<AuthenticationResult> onSuccess,
                      Runnable onRegisterClick) {
        VBox logo = new VBox(4);
        logo.setAlignment(Pos.CENTER);
        Label subtitle = new Label("Sign in to continue");
        subtitle.getStyleClass().add("muted-text");
        logo.getChildren().addAll(UiComponents.logoImageView(40), subtitle);

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("field-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g. amil.ibrahimli");

        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("field-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-text");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);

        var loginButton = UiComponents.primaryButton("Login");
        loginButton.setMaxWidth(Double.MAX_VALUE);

        Runnable attemptLogin = () -> {
            AuthenticationResult result = authController.login(usernameField.getText(), passwordField.getText());
            if (result.isSuccess()) {
                errorLabel.setVisible(false);
                onSuccess.accept(result);
            } else {
                errorLabel.setText(result.getMessage());
                errorLabel.setVisible(true);
            }
        };

        loginButton.setOnAction(e -> attemptLogin.run());
        passwordField.setOnAction(e -> attemptLogin.run());

        Hyperlink registerLink = new Hyperlink("Don't have an account? Register");
        registerLink.setOnAction(e -> onRegisterClick.run());

        Label hint = new Label(
                "Demo accounts - Student: amil.ibrahimli / password123   Teacher: erdem.karacay / password123   Admin: admin / admin123");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        hint.setMaxWidth(360);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-text-alignment: center;");

        VBox form = new VBox(6, usernameLabel, usernameField, new Region(), passwordLabel, passwordField);
        form.setFillWidth(true);
        ((Region) form.getChildren().get(2)).setMinHeight(8);
        form.setPrefWidth(320);

        VBox card = new VBox(22, logo, form, errorLabel, loginButton, registerLink, hint);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(420);
        VBox.setVgrow(loginButton, Priority.NEVER);

        root = new StackPane(card);
        root.getStyleClass().add("app-bg");
        root.setPadding(new Insets(40));
    }

    public StackPane getRoot() {
        return root;
    }
}
