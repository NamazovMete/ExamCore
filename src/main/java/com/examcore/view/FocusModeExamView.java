package com.examcore.view;

import com.examcore.controller.ExamRunnerController;
import com.examcore.controller.FeedbackController;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Question;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;

public class FocusModeExamView {

    public interface OnSubmitted {
        void handle(Submission submission, int score, boolean dueToTimeout);
    }

    private final ExamRunnerController controller;
    private final FeedbackController feedbackController;
    private final Test test;
    private final Student student;
    private final OnSubmitted onSubmitted;

    private final BorderPane root = new BorderPane();
    private final Label timerLabel = new Label();
    private final Label focusWarningLabel = new Label();
    private final Label saveStatusLabel = new Label();
    private boolean timerHidden = false;
    private long lastSecondsRemaining;

    private int currentQuestionIndex = 0;

    private Stage lockedStage;
    private boolean lockdownApplied = false;
    private javafx.beans.value.ChangeListener<Boolean> stageFocusListener;

    public FocusModeExamView(ExamRunnerController controller, FeedbackController feedbackController, Test test,
                              Student student, OnSubmitted onSubmitted) {
        this.controller = controller;
        this.feedbackController = feedbackController;
        this.test = test;
        this.student = student;
        this.onSubmitted = onSubmitted;

        root.getStyleClass().add("app-bg");
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::blockClipboardShortcuts);
        root.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, javafx.event.Event::consume);
        focusWarningLabel.setVisible(false);
        focusWarningLabel.setManaged(false);
        controller.setListener(buildListener());
        render();
        attachLockdownWhenReady();
    }

    public BorderPane getRoot() {
        return root;
    }

    private ExamRunnerController.ExamRunnerListener buildListener() {
        return new ExamRunnerController.ExamRunnerListener() {
            @Override
            public void onExamStarted(Test test, int durationLimitMinutes) {
                lastSecondsRemaining = durationLimitMinutes * 60L;
                Platform.runLater(() -> updateTimerLabel(lastSecondsRemaining));
            }

            // this method's body is empty because the view already updates the answer in real time, so no UI update is needed when the controller saves it

            @Override
            public void onAnswerSaved(String questionID, String answer){}

            @Override
            public void onTimeAlert(long secondsRemaining) {
                Platform.runLater(() -> flashTimerWarning());
            }

            @Override
            public void onTick(long secondsRemaining) {
                lastSecondsRemaining = secondsRemaining;
                Platform.runLater(() -> updateTimerLabel(secondsRemaining));
            }

            @Override
            public void onExamSubmitted(Submission submission, int score, boolean dueToTimeout) {
                Platform.runLater(() -> {
                    restoreWindow();
                    if (dueToTimeout) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                "Time is up! Your exam was submitted automatically.\nScore: " + score + "/"
                                        + test.getQuestions().size());
                        alert.setHeaderText("Time Expired");
                        alert.showAndWait();
                    }
                    promptForFeedback(score);
                    onSubmitted.handle(submission, score, dueToTimeout);
                });
            }
        };
    }

    private void promptForFeedback(int score) {
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
        prompt.setHeaderText("Score: " + score + "/" + test.getQuestions().size());
        prompt.setContentText("Would you like to leave feedback on this "
                + (test.getTestType() == TestType.QUIZ ? "quiz" : "exam") + "?");
        ButtonType giveFeedback = new ButtonType("Give Feedback");
        ButtonType skip = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
        prompt.getButtonTypes().setAll(giveFeedback, skip);

        Optional<ButtonType> choice = prompt.showAndWait();
        if (choice.isEmpty() || choice.get() != giveFeedback) {
            return;
        }

        String draft = "";
        boolean retry = true;
        while (retry) {
            retry = false;

            TextArea feedbackArea = new TextArea(draft);
            feedbackArea.setPromptText("What did you think of this "
                    + (test.getTestType() == TestType.QUIZ ? "quiz" : "exam") + "?");
            feedbackArea.setPrefRowCount(4);
            feedbackArea.setWrapText(true);

            Alert feedbackDialog = new Alert(Alert.AlertType.NONE);
            feedbackDialog.setHeaderText("Your feedback on " + test.getTitle());
            feedbackDialog.getDialogPane().setContent(feedbackArea);
            feedbackDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = feedbackDialog.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK && !feedbackArea.getText().isBlank()) {
                try {
                    feedbackController.submitFeedback(student, test, feedbackArea.getText());
                } catch (IllegalArgumentException ex) {
                    draft = feedbackArea.getText();
                    Alert error = new Alert(Alert.AlertType.WARNING, ex.getMessage());
                    error.setHeaderText("Feedback not submitted");
                    error.showAndWait();
                    retry = true;
                }
            }
        }
    }

    private void updateTimerLabel(long secondsRemaining) {
        if (timerHidden) {
            timerLabel.setText("---");
            return;
        }
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;
        timerLabel.setText(String.format("%d:%02d left", minutes, seconds));
    }

    private void flashTimerWarning() {
        timerLabel.getStyleClass().setAll("timer-pill-warning");
        PauseTransition revert = new PauseTransition(Duration.seconds(3));
        revert.setOnFinished(e -> timerLabel.getStyleClass().setAll("timer-pill"));
        revert.play();
    }

    private void attachLockdownWhenReady() {
        Scene scene = root.getScene();
        if (scene != null) {
            attachToWindow(scene);
            return;
        }
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                attachToWindow(newScene);
            }
        });
    }

    private void attachToWindow(Scene scene) {
        Window window = scene.getWindow();
        if (window != null) {
            applyLockdown(window);
            return;
        }
        scene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (newWindow != null) {
                applyLockdown(newWindow);
            }
        });
    }

    private void applyLockdown(Window window) {
        if (lockdownApplied || !(window instanceof Stage stage)) {
            return;
        }
        lockdownApplied = true;
        lockedStage = stage;

        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreen(true);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(event -> {
            event.consume();
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "You cannot close the window during an exam. Submit your answers first.");
            alert.setHeaderText("Exam in progress");
            alert.showAndWait();
        });

        stageFocusListener = (obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                int count = controller.recordFocusLost();
                Platform.runLater(() -> flashFocusWarning(count));
            }
        };
        stage.focusedProperty().addListener(stageFocusListener);
    }

    // this methods restores the window to normal state after the exam is done

    private void restoreWindow() {
        if (!lockdownApplied || lockedStage == null) {
            return;
        }
        if (stageFocusListener != null) {
            lockedStage.focusedProperty().removeListener(stageFocusListener);
            stageFocusListener = null;
        }
        lockedStage.setOnCloseRequest(null);
        lockedStage.setAlwaysOnTop(false);
        lockedStage.setFullScreen(false);
        lockdownApplied = false;
    }

    private void flashFocusWarning(int count) {
        focusWarningLabel.setText("⚠ Left exam window (" + count + ")");
        focusWarningLabel.setVisible(true);
        focusWarningLabel.setManaged(true);
        PauseTransition hide = new PauseTransition(Duration.seconds(5));
        hide.setOnFinished(e -> {
            focusWarningLabel.setVisible(false);
            focusWarningLabel.setManaged(false);
        });
        hide.play();
    }

    // this method blocks copy/cut/paste 

    private void blockClipboardShortcuts(KeyEvent event) {
        KeyCode code = event.getCode();
        boolean shortcutDown = event.isControlDown() || event.isMetaDown();
        boolean isCopy = (shortcutDown && code == KeyCode.C) || (shortcutDown && code == KeyCode.INSERT);
        boolean isPaste = (shortcutDown && code == KeyCode.V) || (event.isShiftDown() && code == KeyCode.INSERT);
        boolean isCut = (shortcutDown && code == KeyCode.X) || (event.isShiftDown() && code == KeyCode.DELETE);
        if (isCopy || isPaste || isCut) {
            event.consume();
            controller.recordClipboardBlocked(code.getName());
        }
    }

    private void render() {
        root.setTop(buildHeader());

        ScrollPane scrollPane = new ScrollPane(buildBody());
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        root.setCenter(scrollPane);

        root.setBottom(buildPager());
    }

    private HBox buildHeader() {
        HBox header = new HBox(20);
        header.getStyleClass().add("navbar");
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(test.getTitle());
        title.getStyleClass().add("section-title");

        focusWarningLabel.getStyleClass().setAll("timer-pill-warning");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        saveStatusLabel.setText("Ready");
        saveStatusLabel.getStyleClass().setAll("row-subtitle");

        timerLabel.getStyleClass().add("timer-pill");
        updateTimerLabel(lastSecondsRemaining == 0 ? test.getDurationLimit() * 60L : lastSecondsRemaining);

        var toggleButton = UiComponents.outlineButton(timerHidden ? "Show" : "Hide");
        toggleButton.setOnAction(e -> {
            timerHidden = !timerHidden;
            toggleButton.setText(timerHidden ? "Show" : "Hide");
            updateTimerLabel(lastSecondsRemaining);
        });

        var avatar = UiComponents.avatarView(student, 16);
        Label studentLabel = new Label(UiComponents.displayName(student));
        studentLabel.getStyleClass().add("nav-username");

        header.getChildren().addAll(title, focusWarningLabel, spacer, saveStatusLabel, timerLabel, toggleButton, avatar, studentLabel);
        return header;
    }

    private VBox buildBody() {
        List<Question> questions = test.getQuestions();
        if (questions.isEmpty()) {
            VBox empty = new VBox(new Label("This test has no questions."));
            empty.setPadding(new Insets(40));
            return empty;
        }
        Question question = questions.get(currentQuestionIndex);
        Submission submission = controller.getCurrentSubmission().orElseThrow();

        VBox questionCard = UiComponents.card(16);
        Label questionText = new Label(question.getContent());
        questionText.setWrapText(true);
        questionText.getStyleClass().add("row-title");
        questionCard.getChildren().add(questionText);
        questionCard.setMinHeight(360);

        VBox answerCard = UiComponents.card(16);
        answerCard.setMinHeight(360);

        if (question instanceof MultipleChoiceQuestion mcQuestion) {
            Label prompt = new Label("Choose the correct variant.");
            prompt.getStyleClass().add("row-subtitle");
            answerCard.getChildren().add(prompt);

            ToggleGroup group = new ToggleGroup();
            String existingAnswer = submission.getAnswer(question.getQuestionID());
            char letter = 'A';
            for (String choice : mcQuestion.getChoices()) {
                RadioButton radio = new RadioButton(letter + "   " + choice);
                radio.setToggleGroup(group);
                radio.setUserData(choice);
                if (choice.equals(existingAnswer)) {
                    radio.setSelected(true);
                }
                radio.selectedProperty().addListener((obs, was, isNow) -> {
                    if (isNow) {
                        saveAnswerSafely(question.getQuestionID(), choice);
                    }
                });
                answerCard.getChildren().add(radio);
                letter++;
            }
        } else {
            Label prompt = new Label("Enter your answer here:");
            prompt.getStyleClass().add("row-subtitle");
            TextField answerField = new TextField(submission.getAnswer(question.getQuestionID()));
            answerField.setPromptText("An integer value");
            answerField.textProperty().addListener((obs, oldVal, newVal) ->
                    saveAnswerSafely(question.getQuestionID(), newVal));
            answerCard.getChildren().addAll(prompt, answerField);
        }

        HBox columns = new HBox(24, questionCard, answerCard);
        HBox.setHgrow(questionCard, Priority.ALWAYS);
        HBox.setHgrow(answerCard, Priority.ALWAYS);
        questionCard.setPrefWidth(0);
        answerCard.setPrefWidth(0);

        VBox body = new VBox(24, columns);
        body.setPadding(new Insets(28, 40, 28, 40));
        return body;
    }

    private void saveAnswerSafely(String questionID, String answer) {
        try {
            controller.saveAnswer(questionID, answer);
            saveStatusLabel.setText("Saved");
            saveStatusLabel.setStyle("-fx-text-fill: #0B3B22;");
        } catch (RuntimeException ex) {
            saveStatusLabel.setText("Not saved - connection lost");
            saveStatusLabel.setStyle("-fx-text-fill: #C62828;");
        }
    }

    private void submitExamSafely() {
        if (!controller.isDatabaseAvailable()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "You need an internet connection to submit the exam. Please reconnect and try again.");
            alert.setHeaderText("Cannot submit without connection");
            alert.showAndWait();
            return;
        }

        try {
            controller.submitExam();
        } catch (RuntimeException ex) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Submission failed because the database connection is unavailable. Please reconnect and try again.");
            alert.setHeaderText("Submission failed");
            alert.showAndWait();
        }
    }

    private HBox buildPager() {
        HBox pager = new HBox(10);
        pager.setAlignment(Pos.CENTER);
        pager.setPadding(new Insets(0, 40, 32, 40));

        var prevButton = UiComponents.outlineButton("‹");
        prevButton.setDisable(currentQuestionIndex == 0);
        prevButton.setOnAction(e -> {
            currentQuestionIndex--;
            render();
        });
        pager.getChildren().add(prevButton);

        List<Question> questions = test.getQuestions();
        javafx.scene.layout.FlowPane numberFlow = new javafx.scene.layout.FlowPane(8, 8);
        numberFlow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < questions.size(); i++) {
            int index = i;
            var numberButton = new javafx.scene.control.Button(String.valueOf(i + 1));
            numberButton.getStyleClass().add(i == currentQuestionIndex ? "pager-btn-active" : "pager-btn");
            numberButton.setOnAction(e -> {
                currentQuestionIndex = index;
                render();
            });
            numberFlow.getChildren().add(numberButton);
        }
        HBox.setHgrow(numberFlow, Priority.ALWAYS);
        pager.getChildren().add(numberFlow);

        var nextButton = UiComponents.outlineButton("›");
        nextButton.setDisable(currentQuestionIndex >= questions.size() - 1);
        nextButton.setOnAction(e -> {
            currentQuestionIndex++;
            render();
        });
        pager.getChildren().add(nextButton);

        var submitButton = UiComponents.primaryButton("Submit");
        submitButton.setOnAction(e -> submitExamSafely());
        pager.getChildren().add(submitButton);

        return pager;
    }
}
