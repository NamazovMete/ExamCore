package com.examcore.view;

import com.examcore.controller.ExamAuthoringController;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Question;
import com.examcore.model.QuestionType;
import com.examcore.model.Quiz;
import com.examcore.model.Teacher;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import com.examcore.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * "Creating a New Exam" / edit-exam screen: authors a Test's basic details
 * and questions (multiple-choice or short-answer) through
 * {@link ExamAuthoringController}, matching the two-panel layout from
 * EXAMCORE_DEMO.pdf. Reused by both the Teacher workspace (creating or
 * editing its own tests) and the Admin console (editing any test) — the
 * navigation chrome and the "create" affordance are parameterized so this
 * one view class serves both contexts.
 */
public class ExamEditorView {

    private final ExamAuthoringController controller;
    private final User navUser;
    private final List<String> navTabs;
    private final int activeNavTab;
    private final Teacher ownerForCreation;
    private final TestType type;
    private final Runnable onDone;

    private final BorderPane root = new BorderPane();
    private Test test;

    private final List<RadioButton> choiceCorrectButtons = new ArrayList<>();
    private final List<TextField> choiceFields = new ArrayList<>();
    private final List<String> currentTags = new ArrayList<>();
    private String editingQuestionId;

    private VBox choicesBox;
    private RadioButton mcTypeRadio;
    private RadioButton shortAnswerTypeRadio;
    private TextArea questionContentArea;
    private TextField shortAnswerField;
    private TextField tagInputField;
    private FlowPane tagChipsPane;
    private Label questionNumberLabel;
    private VBox questionListBox;
    private Button saveQuestionButton;
    private Button cancelEditButton;

    /**
     * @param ownerForCreation the teacher who will own a brand-new test; required only when
     *                         {@code existingTest} is null (creating), unused when editing
     * @param existingTest     the test to edit, or null to create a new one
     */
    public ExamEditorView(ExamAuthoringController controller, User navUser, List<String> navTabs, int activeNavTab,
                           Teacher ownerForCreation, Test existingTest, TestType type, Runnable onDone) {
        this.controller = controller;
        this.navUser = navUser;
        this.navTabs = navTabs;
        this.activeNavTab = activeNavTab;
        this.ownerForCreation = ownerForCreation;
        this.test = existingTest;
        this.type = type;
        this.onDone = onDone;

        root.getStyleClass().add("app-bg");
        render();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void render() {
        HBox nav = UiComponents.navBar(navTabs, activeNavTab, navUser, idx -> onDone.run(), onDone);

        Label title = UiComponents.pageTitle(test == null ? "Creating a New " + typeLabel() : "Editing " + test.getTitle());

        VBox detailsCard = buildDetailsCard();
        VBox body = test == null ? new VBox() : buildQuestionAuthoringBody();

        VBox page = new VBox(24, title, detailsCard, body);
        VBox container = new VBox(28, nav, page);
        container.setPadding(new Insets(28, 40, 28, 40));

        var scrollPane = new javafx.scene.control.ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        root.setCenter(scrollPane);
    }

    private String typeLabel() {
        return type == TestType.QUIZ ? "Quiz" : "Exam";
    }

    // ---- Basic details (title / duration / topic) ----

    private VBox buildDetailsCard() {
        VBox card = UiComponents.card(14);

        TextField titleField = new TextField(test != null ? test.getTitle() : "");
        titleField.setPromptText("Title, e.g. " + (type == TestType.QUIZ ? "CS102 - QUIZ 2" : "MATH101"));
        titleField.setPrefWidth(320);

        TextField durationField = new TextField(test != null ? String.valueOf(test.getDurationLimit()) : "");
        durationField.setPromptText("Duration (minutes)");
        durationField.setPrefWidth(160);

        TextField topicField = new TextField(test instanceof Quiz quiz ? quiz.getTopic() : "");
        topicField.setPromptText("Topic");
        topicField.setPrefWidth(220);

        HBox fieldsRow = new HBox(16);
        fieldsRow.setAlignment(Pos.CENTER_LEFT);
        fieldsRow.getChildren().addAll(labeled("Title", titleField), labeled("Duration (min)", durationField));
        if (type == TestType.QUIZ) {
            fieldsRow.getChildren().add(labeled("Topic", topicField));
        }

        Button actionButton = UiComponents.primaryButton(test == null ? "Create " + typeLabel() : "Update Details");
        actionButton.setOnAction(e -> {
            String titleText = titleField.getText();
            Integer duration = parsePositiveInt(durationField.getText());
            if (titleText == null || titleText.isBlank() || duration == null) {
                showError("Title is required and duration must be a positive number of minutes.");
                return;
            }
            if (test == null) {
                if (ownerForCreation == null) {
                    showError("Only a teacher can create a new test.");
                    return;
                }
                test = type == TestType.QUIZ
                        ? controller.createQuiz(ownerForCreation, titleText, duration, topicField.getText())
                        : controller.createExam(ownerForCreation, titleText, duration);
            } else {
                controller.updateTitle(test, titleText);
                controller.updateDuration(test, duration);
                if (test instanceof Quiz quiz && topicField.getText() != null) {
                    quiz.setTopic(topicField.getText());
                }
            }
            render();
        });

        HBox row = new HBox(16, fieldsRow, actionButton);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(row);
        return card;
    }

    private VBox labeled(String labelText, javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        return new VBox(6, label, field);
    }

    private Integer parsePositiveInt(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Question authoring ----

    private VBox buildQuestionAuthoringBody() {
        questionListBox = new VBox(12);
        refreshQuestionList();

        VBox questionCard = UiComponents.card(16);
        questionNumberLabel = new Label();
        questionNumberLabel.getStyleClass().add("section-title");
        updateQuestionNumberLabel();

        Label contentLabel = new Label("Add question here:");
        contentLabel.getStyleClass().add("field-label");
        questionContentArea = new TextArea();
        questionContentArea.setPromptText("Question text");
        questionContentArea.setPrefRowCount(3);
        questionContentArea.setWrapText(true);

        choicesBox = new VBox(10);
        rebuildChoiceRows(null, null);

        var addOptionButton = UiComponents.outlineButton("+ Add option");
        addOptionButton.setOnAction(e -> addChoiceRow());

        shortAnswerField = new TextField();
        shortAnswerField.setPromptText("Correct answer");

        VBox mcSection = new VBox(10, new Label("Options:"), choicesBox, addOptionButton);
        VBox shortAnswerSection = new VBox(10, new Label("Correct answer:"), shortAnswerField);
        shortAnswerSection.setVisible(false);
        shortAnswerSection.setManaged(false);

        questionCard.getChildren().addAll(questionNumberLabel, contentLabel, questionContentArea, mcSection,
                shortAnswerSection);

        VBox sidePanel = UiComponents.card(14);
        Label typeLabel = new Label("Question Type :");
        typeLabel.getStyleClass().add("field-label");
        ToggleGroup typeGroup = new ToggleGroup();
        mcTypeRadio = new RadioButton("Multiple Choice");
        mcTypeRadio.setToggleGroup(typeGroup);
        mcTypeRadio.setSelected(true);
        shortAnswerTypeRadio = new RadioButton("Short-answer");
        shortAnswerTypeRadio.setToggleGroup(typeGroup);
        mcTypeRadio.selectedProperty().addListener((obs, was, isNow) -> {
            mcSection.setVisible(isNow);
            mcSection.setManaged(isNow);
            shortAnswerSection.setVisible(!isNow);
            shortAnswerSection.setManaged(!isNow);
        });

        Label tagsLabel = new Label("Question Tags :");
        tagsLabel.getStyleClass().add("field-label");
        tagChipsPane = new FlowPane(8, 8);
        refreshTagChips();
        tagInputField = new TextField();
        tagInputField.setPromptText("e.g. arithmetic");
        var addTagButton = UiComponents.outlineButton("+ Add Tag");
        Runnable addTag = () -> {
            String value = tagInputField.getText();
            if (value != null && !value.isBlank() && !currentTags.contains(value.trim())) {
                currentTags.add(value.trim());
                tagInputField.clear();
                refreshTagChips();
            }
        };
        addTagButton.setOnAction(e -> addTag.run());
        tagInputField.setOnAction(e -> addTag.run());
        HBox tagInputRow = new HBox(8, tagInputField, addTagButton);

        saveQuestionButton = UiComponents.primaryButton("+ Add question");
        saveQuestionButton.setMaxWidth(Double.MAX_VALUE);
        saveQuestionButton.setOnAction(e -> saveQuestion());

        cancelEditButton = UiComponents.outlineButton("Cancel Edit");
        cancelEditButton.setMaxWidth(Double.MAX_VALUE);
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
        cancelEditButton.setOnAction(e -> {
            editingQuestionId = null;
            resetQuestionForm();
        });

        var saveExitButton = UiComponents.greenButton("Save & Exit");
        saveExitButton.setMaxWidth(Double.MAX_VALUE);
        saveExitButton.setOnAction(e -> onDone.run());

        sidePanel.getChildren().addAll(typeLabel, mcTypeRadio, shortAnswerTypeRadio, tagsLabel, tagChipsPane,
                tagInputRow, saveQuestionButton, cancelEditButton, saveExitButton);

        HBox columns = new HBox(24, questionCard, sidePanel);
        HBox.setHgrow(questionCard, Priority.ALWAYS);

        return new VBox(20, questionListBox, columns);
    }

    private void refreshTagChips() {
        tagChipsPane.getChildren().clear();
        for (String tag : currentTags) {
            HBox chip = new HBox(4);
            chip.getStyleClass().add("tag-chip");
            chip.setAlignment(Pos.CENTER_LEFT);
            Label tagLabel = new Label(tag);
            tagLabel.getStyleClass().add("tag-chip-label");
            var removeButton = new Button("×");
            removeButton.getStyleClass().add("tag-chip-remove");
            removeButton.setOnAction(e -> {
                currentTags.remove(tag);
                refreshTagChips();
            });
            chip.getChildren().addAll(tagLabel, removeButton);
            tagChipsPane.getChildren().add(chip);
        }
    }

    private void updateQuestionNumberLabel() {
        if (editingQuestionId != null) {
            questionNumberLabel.setText("Editing Question");
            return;
        }
        int nextNumber = test == null ? 1 : test.getQuestions().size() + 1;
        questionNumberLabel.setText("Question : " + nextNumber);
    }

    private void rebuildChoiceRows(List<String> existingChoices, String correctChoice) {
        choicesBox.getChildren().clear();
        choiceFields.clear();
        choiceCorrectButtons.clear();
        ToggleGroup correctGroup = new ToggleGroup();
        if (existingChoices == null || existingChoices.isEmpty()) {
            addChoiceRowInternal(correctGroup, "", true);
            addChoiceRowInternal(correctGroup, "", false);
        } else {
            for (String choice : existingChoices) {
                addChoiceRowInternal(correctGroup, choice, choice.equals(correctChoice));
            }
        }
    }

    private void addChoiceRow() {
        ToggleGroup existingGroup = choiceCorrectButtons.isEmpty() ? new ToggleGroup()
                : choiceCorrectButtons.get(0).getToggleGroup();
        addChoiceRowInternal(existingGroup, "", false);
    }

    private void addChoiceRowInternal(ToggleGroup correctGroup, String text, boolean selected) {
        char letter = (char) ('A' + choiceFields.size());
        RadioButton correctRadio = new RadioButton(String.valueOf(letter));
        correctRadio.setToggleGroup(correctGroup);
        correctRadio.setSelected(selected);
        TextField optionField = new TextField(text);
        optionField.setPromptText("Option " + letter);
        HBox.setHgrow(optionField, Priority.ALWAYS);
        HBox row = new HBox(10, correctRadio, optionField);
        row.setAlignment(Pos.CENTER_LEFT);
        choiceFields.add(optionField);
        choiceCorrectButtons.add(correctRadio);
        choicesBox.getChildren().add(row);
    }

    private void saveQuestion() {
        String content = questionContentArea.getText();
        if (content == null || content.isBlank()) {
            showError("Question text cannot be empty.");
            return;
        }

        try {
            if (mcTypeRadio.isSelected()) {
                List<String> choices = choiceFields.stream().map(TextField::getText).toList();
                int correctIndex = -1;
                for (int i = 0; i < choiceCorrectButtons.size(); i++) {
                    if (choiceCorrectButtons.get(i).isSelected()) {
                        correctIndex = i;
                        break;
                    }
                }
                if (correctIndex < 0 || choices.get(correctIndex) == null || choices.get(correctIndex).isBlank()) {
                    showError("Select which option is correct, and make sure it has text.");
                    return;
                }
                String correctChoice = choices.get(correctIndex);
                if (editingQuestionId != null) {
                    controller.updateMultipleChoiceQuestion(test, editingQuestionId, content, choices, correctChoice,
                            currentTags);
                } else {
                    controller.addMultipleChoiceQuestion(test, content, choices, correctChoice, currentTags);
                }
            } else {
                String correctAnswer = shortAnswerField.getText();
                if (correctAnswer == null || correctAnswer.isBlank()) {
                    showError("Correct answer cannot be empty.");
                    return;
                }
                if (editingQuestionId != null) {
                    controller.updateShortAnswerQuestion(test, editingQuestionId, content, correctAnswer,
                            currentTags);
                } else {
                    controller.addShortAnswerQuestion(test, content, correctAnswer, currentTags);
                }
            }
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        editingQuestionId = null;
        resetQuestionForm();
        refreshQuestionList();
    }

    private void resetQuestionForm() {
        questionContentArea.clear();
        currentTags.clear();
        refreshTagChips();
        rebuildChoiceRows(null, null);
        shortAnswerField.clear();
        mcTypeRadio.setSelected(true);
        saveQuestionButton.setText("+ Add question");
        cancelEditButton.setVisible(false);
        cancelEditButton.setManaged(false);
        updateQuestionNumberLabel();
    }

    private void editQuestion(Question question) {
        editingQuestionId = question.getQuestionID();
        questionContentArea.setText(question.getContent());
        currentTags.clear();
        currentTags.addAll(question.getTags());
        refreshTagChips();

        if (question instanceof MultipleChoiceQuestion mc) {
            mcTypeRadio.setSelected(true);
            rebuildChoiceRows(mc.getChoices(), mc.getSolution());
        } else {
            shortAnswerTypeRadio.setSelected(true);
            shortAnswerField.setText(question.getSolution());
        }

        saveQuestionButton.setText("Update question");
        cancelEditButton.setVisible(true);
        cancelEditButton.setManaged(true);
        updateQuestionNumberLabel();
    }

    private void refreshQuestionList() {
        questionListBox.getChildren().clear();
        if (test == null || test.getQuestions().isEmpty()) {
            return;
        }
        Label listTitle = new Label("Questions added so far (" + test.getQuestions().size() + ")");
        listTitle.getStyleClass().add("section-title");
        questionListBox.getChildren().add(listTitle);

        int index = 1;
        for (Question q : test.getQuestions()) {
            HBox row = new HBox(16);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            String typeText = q.getType() == QuestionType.MULTIPLE_CHOICE
                    ? "Multiple Choice" + (q instanceof MultipleChoiceQuestion mc ? " (" + mc.getChoices().size() + " options)" : "")
                    : "Short Answer";
            Label numberLabel = new Label(index + ".");
            numberLabel.getStyleClass().add("row-title");
            Label contentLabel = new Label(q.getContent());
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("row-subtitle");
            VBox textBox = new VBox(2, new Label(typeText) {
                {
                    getStyleClass().add("row-title");
                }
            }, contentLabel);
            if (!q.getTags().isEmpty()) {
                Label tagsLabel = new Label("Tags: " + String.join(", ", q.getTags()));
                tagsLabel.getStyleClass().add("muted-text");
                textBox.getChildren().add(tagsLabel);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            var editButton = UiComponents.outlineButton("Edit");
            editButton.getStyleClass().add("btn-icon-green");
            editButton.setOnAction(e -> editQuestion(q));

            var deleteButton = UiComponents.outlineButton("Remove");
            deleteButton.getStyleClass().add("btn-icon-pink");
            String questionId = q.getQuestionID();
            deleteButton.setOnAction(e -> {
                controller.removeQuestion(test, questionId);
                if (questionId.equals(editingQuestionId)) {
                    editingQuestionId = null;
                    resetQuestionForm();
                }
                updateQuestionNumberLabel();
                refreshQuestionList();
            });

            row.getChildren().addAll(numberLabel, textBox, spacer, editButton, deleteButton);
            questionListBox.getChildren().add(row);
            index++;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText("Check the form");
        alert.showAndWait();
    }
}
