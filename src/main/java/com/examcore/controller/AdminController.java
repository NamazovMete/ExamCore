package com.examcore.controller;

import com.examcore.data.Database;
import com.examcore.model.Admin;
import com.examcore.model.Teacher;
import com.examcore.model.User;

import java.util.List;

public class AdminController {

    private final Database database;

    public AdminController() {
        this(Database.getInstance());
    }

    public AdminController(Database database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
    }

    public void verifyTeacher(Admin admin, Teacher teacher) {
        requireAdmin(admin);
        if (teacher == null) {
            throw new IllegalArgumentException("Teacher cannot be null");
        }
        admin.verifyTeacher(teacher);
        database.saveUser(teacher);
        database.saveUser(admin);
        database.flush();
    }

    public void deactivateUser(Admin admin, User user) {
        requireAdmin(admin);
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        admin.deactivateUser(user);
        database.saveUser(user);
        database.saveUser(admin);
        database.flush();
    }

    public void reactivateUser(Admin admin, User user) {
        requireAdmin(admin);
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        admin.reactivateUser(user);
        database.saveUser(user);
        database.saveUser(admin);
        database.flush();
    }

    public List<Teacher> getPendingTeachers() {
        return database.getAllUsers().stream()
                .filter(u -> u instanceof Teacher)
                .map(u -> (Teacher) u)
                .filter(t -> !t.isVerified())
                .toList();
    }

    public List<User> getAllUsers() {
        return List.copyOf(database.getAllUsers());
    }

    private void requireAdmin(Admin admin) {
        if (admin == null) {
            throw new IllegalArgumentException("Admin cannot be null");
        }
    }
}
