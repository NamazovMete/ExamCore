package com.examcore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Classroom extends BaseEntity {

    private final String classID;
    private String className;
    private final List<Student> studentList = new ArrayList<>();

    public Classroom(String classID, String className) {
        super();
        if (classID == null || classID.isBlank()) {
            throw new IllegalArgumentException("Class ID cannot be blank");
        }
        this.classID = classID;
        this.className = className;
    }

    public void addStudent(Student student) {
        if (student == null) {
            throw new IllegalArgumentException("Student cannot be null");
        }
        if (!studentList.contains(student)) {
            studentList.add(student);
            touch();
        }
    }

    public void removeStudent(Student student) {
        studentList.remove(student);
        touch();
    }

    public int getParticipantCount() {
        return studentList.size();
    }

    public List<Student> getStudentList() {
        return Collections.unmodifiableList(studentList);
    }

    public String getClassID() {
        return classID;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
        touch();
    }
}
