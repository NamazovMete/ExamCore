package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Classroom;
import com.examcore.model.Student;
import com.examcore.model.Teacher;
import com.examcore.model.Test;

public class ClassroomController {

    private final Database database;

    public ClassroomController() {
        this(Database.getInstance());
    }

    public ClassroomController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }


    public Classroom createClassroom(Teacher teacher, String classID, String className) {
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher cannot be null");
        }
        if (classID == null || classID.isBlank()) {
            throw new IllegalArgumentException("Class ID cannot be blank");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Class name cannot be blank");
        }
        if (database.findClassroomById(classID).isPresent()) {
            throw new IllegalStateException("A classroom with ID '" + classID + "' already exists");
        }

        Classroom classroom = new Classroom(classID, className);
        teacher.addManagedClassroom(classroom);
        database.saveClassroom(classroom);
        return classroom;
    }

    public void addStudent(Teacher teacher, Classroom classroom, Student student) {
        if (teacher == null || classroom == null || student == null) {
            throw new IllegalArgumentException("Teacher, classroom, and student cannot be null");
        }
        teacher.addStudentToClass(student, classroom);
        database.saveClassroom(classroom);
    }

    public void removeStudent(Classroom classroom, Student student) {
        if (classroom == null || student == null) {
            throw new IllegalArgumentException("Classroom and student cannot be null");
        }
        classroom.removeStudent(student);
        database.flush();
    }

    public void renameClassroom(Classroom classroom, String newClassName) {
        if (classroom == null) {
            throw new IllegalArgumentException("Classroom cannot be null");
        }
        if (newClassName == null || newClassName.isBlank()) {
            throw new IllegalArgumentException("Class name cannot be blank");
        }
        classroom.setClassName(newClassName.trim());
        database.saveClassroom(classroom);
    }

    public void assignTest(Teacher teacher, Test test, Classroom classroom) {
        if (teacher == null || test == null || classroom == null) {
            throw new IllegalArgumentException("Teacher, test, and classroom cannot be null");
        }
        teacher.assignExam(test, classroom);
        for (Student student : classroom.getStudentList()) {
            student.assignTest(test);
        }
        database.flush();
    }
}