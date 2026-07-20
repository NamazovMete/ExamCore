package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public abstract class Question extends BaseEntity {

    private final QuestionType type;
    private final String questionID;
    private final List<String> tags = new ArrayList<>();
    private String content;
    private String solution;
    private String hint;
    private String explanation;


    protected Question(QuestionType type, String questionID, String content, String solution) {
        super();
        if (type == null) {
            throw new IllegalArgumentException("Question type cannot be null");
        }
        if (questionID == null || questionID.isBlank()) {
            throw new IllegalArgumentException("Question ID cannot be blank");
        }
        this.type = type;
        this.questionID = questionID;
        this.content = content;
        this.solution = solution;
    }

    public abstract boolean validateAnswer(String submittedAnswer);

    public QuestionType getType() {
        return type;
    }

    public String getQuestionID() {
        return questionID;
    }

    public void addTag(String tag) {
        if (tag != null && !tag.isBlank() && !tags.contains(tag.trim())) {
            tags.add(tag.trim());
            touch();
        }
    }

    public void removeTag(String tag) {
        if (tags.remove(tag)) {
            touch();
        }
    }

    public void setTags(List<String> newTags) {
        tags.clear();
        if (newTags != null) {
            tags.addAll(new LinkedHashSet<>(newTags.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .toList()));
        }
        touch();
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        touch();
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
        touch();
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
        touch();
    }
    
    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
        touch();
    }

    public boolean hasExplanation() {
        return explanation != null && !explanation.isBlank();
    }

}
