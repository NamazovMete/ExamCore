package com.examcore.app;

import com.examcore.controller.AuthenticationController;
import com.examcore.controller.AuthenticationController.AuthenticationResult;
import com.examcore.data.Database;
import com.examcore.model.Admin;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import com.examcore.view.AdminDashboardView;
import com.examcore.view.LoginView;
import com.examcore.view.RegisterView;
import com.examcore.view.StudentDashboardView;
import com.examcore.view.TeacherDashboardView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.net.URL;


// ExamCore desktop application entry point.

public class MainApp extends Application {
    //Size
    private static final double WINDOW_WIDTH = 1400;
    private static final double WINDOW_HEIGHT = 880;

    private final Database database = Database.getInstance();
    private final AuthenticationController authController = new AuthenticationController(database);

    private Stage primaryStage;
    private Scene scene;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("ExamCore");
        // Set the app icon if we can find it. Not required to run.
        URL iconUrl = getClass().getResource("/com/examcore/images/logo-icon.png");
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }

        // Show the login screen first.
        showLogin();

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(WINDOW_WIDTH, visualBounds.getWidth());
        double height = Math.min(WINDOW_HEIGHT, visualBounds.getHeight());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setMinWidth(Math.min(1100, visualBounds.getWidth()));
        stage.setMinHeight(Math.min(700, visualBounds.getHeight()));
        stage.setX(visualBounds.getMinX() + (visualBounds.getWidth() - width) / 2);
        stage.setY(visualBounds.getMinY() + (visualBounds.getHeight() - height) / 2);
        if (width < WINDOW_WIDTH || height < WINDOW_HEIGHT) {
            stage.setMaximized(true);
        }
        stage.show();
    }

    private void setRoot(Parent root) {
        if (scene == null) {
            scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            URL cssUrl = getClass().getResource("/com/examcore/view/examcore.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }

    private void showLogin() {
        LoginView loginView = new LoginView(authController, this::onLoginSuccess, this::showRegister);
        setRoot(loginView.getRoot());
    }

    private void showRegister() {
        RegisterView registerView = new RegisterView(authController, this::onLoginSuccess, this::showLogin);
        setRoot(registerView.getRoot());
    }

    private void onLoginSuccess(AuthenticationResult result) {
        var dashboard = result.getDashboard().orElseThrow();
        var user = result.getUser().orElseThrow();

        switch (dashboard) {
            case STUDENT_DASHBOARD -> {
                StudentDashboardView view = new StudentDashboardView(database, (Student) user, this::handleLogout);
                setRoot(view.getRoot());
            }
            case TEACHER_DASHBOARD -> {
                TeacherDashboardView view = new TeacherDashboardView(database, (Teacher) user, this::handleLogout);
                setRoot(view.getRoot());
            }
            case ADMIN_DASHBOARD -> {
                AdminDashboardView view = new AdminDashboardView(database, (Admin) user, this::handleLogout);
                setRoot(view.getRoot());
            }
        }
    }

    // Logs the user out and returns to login.
    private void handleLogout() {
        authController.logout();
        showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
