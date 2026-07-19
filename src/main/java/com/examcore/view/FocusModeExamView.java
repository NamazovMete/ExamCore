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

    }

    public BorderPane getRoot() {
        return root;
    }

}
