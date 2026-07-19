package com.examcore.view;

import com.examcore.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.io.File;
import java.util.List;
import java.util.function.IntConsumer;

public final class UiComponents {

    private static final Image LOGO_FULL = new Image(
            UiComponents.class.getResourceAsStream("/com/examcore/images/logo-full.png"));

    private static final String[] AVATAR_COLORS = {
            "#3D2FE0", "#0FB8C9", "#E0637F", "#E08A2F", "#2FA35E", "#8A4FE0"
    };

    private UiComponents() {
    }

    public static ImageView logoImageView(double height) {
        ImageView view = new ImageView(LOGO_FULL);
        view.setPreserveRatio(true);
        view.setFitHeight(height);
        return view;
    }

    public static HBox navBar(List<String> tabs, int activeIndex, User user, IntConsumer onTabClick,
                               Runnable onProfileClick) {
        HBox bar = new HBox();
        bar.getStyleClass().add("navbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(28);

        ImageView logo = logoImageView(30);

        HBox tabsBox = new HBox(6);
        tabsBox.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < tabs.size(); i++) {
            Label tabLabel = new Label(tabs.get(i));
            int index = i;
            tabLabel.getStyleClass().add(i == activeIndex ? "nav-link-active" : "nav-link");
            tabLabel.setOnMouseClicked(e -> onTabClick.accept(index));
            tabsBox.getChildren().add(tabLabel);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox userBox = new HBox(10);
        userBox.setAlignment(Pos.CENTER_RIGHT);
        userBox.setCursor(javafx.scene.Cursor.HAND);
        Label userLabel = new Label(displayName(user));
        userLabel.getStyleClass().add("nav-username");
        userBox.getChildren().addAll(avatarView(user, 18), userLabel);
        userBox.setOnMouseClicked(e -> onProfileClick.run());

        bar.getChildren().addAll(logo, tabsBox, spacer, userBox);
        return bar;
    }

    public static String displayName(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        if (first == null || first.isBlank()) {
            return user.getUsername();
        }
        return (last != null && !last.isBlank()) ? first + " " + last.charAt(0) + "." : first;
    }

    public static String fullName(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        if (first == null || first.isBlank()) {
            return user.getUsername();
        }
        return (last != null && !last.isBlank()) ? first + " " + last : first;
    }

    public static String initials(User user) {
        String first = user.getFirstName();
        String last = user.getLastName();
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) {
            sb.append(Character.toUpperCase(first.charAt(0)));
        }
        if (last != null && !last.isBlank()) {
            sb.append(Character.toUpperCase(last.charAt(0)));
        }
        if (sb.length() == 0 && user.getUsername() != null && !user.getUsername().isBlank()) {
            sb.append(Character.toUpperCase(user.getUsername().charAt(0)));
        }
        return sb.toString();
    }

    public static StackPane avatarView(User user, double radius) {
        double diameter = radius * 2;
        StackPane pane = new StackPane();
        pane.setMinSize(diameter, diameter);
        pane.setMaxSize(diameter, diameter);
        pane.setClip(new Circle(radius, radius, radius));

        String path = user.getProfilePicturePath();
        if (path != null && !path.isBlank() && new File(path).isFile()) {
            ImageView imageView = new ImageView(new Image("file:" + path, diameter, diameter, false, true));
            imageView.setFitWidth(diameter);
            imageView.setFitHeight(diameter);
            pane.getChildren().add(imageView);
        } else {
            Circle background = new Circle(radius);
            background.setFill(Color.web(colorFor(user)));
            Label initialsLabel = new Label(initials(user));
            initialsLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: "
                    + Math.max(10, radius * 0.75) + "px;");
            pane.getChildren().addAll(background, initialsLabel);
        }
        return pane;
    }

    private static String colorFor(User user) {
        String key = user.getUsername() != null ? user.getUsername() : String.valueOf(user.getId());
        int index = Math.abs(key.hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    public static VBox iconBadge(String glyph, String colorStyleClass) {
        VBox badge = new VBox();
        badge.getStyleClass().addAll("icon-badge", colorStyleClass);
        badge.setAlignment(Pos.CENTER);
        Label label = new Label(glyph);
        label.getStyleClass().add("icon-glyph");
        badge.getChildren().add(label);
        return badge;
    }

    public static Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-primary");
        return button;
    }

    public static Button outlineButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-outline");
        return button;
    }

    public static Button greenButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-green");
        return button;
    }

    public static Button pinkButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("btn-pink");
        return button;
    }

    public static Label pageTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("page-title");
        return label;
    }

    public static VBox card(double spacing) {
        VBox box = new VBox(spacing);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(24));
        return box;
    }

    public static HBox progressBar(double percent, double width) {
        double clamped = Math.max(0, Math.min(100, percent));
        Rectangle track = new Rectangle(width, 16);
        track.setArcWidth(16);
        track.setArcHeight(16);
        track.setFill(Color.web("#EDEDEF"));

        Rectangle fill = new Rectangle(width * (clamped / 100.0), 16);
        fill.setArcWidth(16);
        fill.setArcHeight(16);
        fill.setFill(Color.web("#22D3EE"));

        StackPane stack = new StackPane(track, fill);
        stack.setAlignment(Pos.CENTER_LEFT);
        return new HBox(stack);
    }

    static {
        Font.font("Consolas");
    }
}
