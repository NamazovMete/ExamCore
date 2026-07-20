package com.examcore.data;


//to work with NEON
import java.nio.file.Files; 
import java.net.URI;
import java.util.Optional;
import java.util.Properties;


import com.examcore.model.Admin;
import com.examcore.model.Classroom;
import com.examcore.model.Exam;
import com.examcore.model.Feedback;
import com.examcore.model.FillInBlankQuestion;
import com.examcore.model.LeaderBoard;
import com.examcore.model.MultipleChoiceQuestion;
import com.examcore.model.Question;
import com.examcore.model.QuestionType;
import com.examcore.model.Quiz;
import com.examcore.model.Role;
import com.examcore.model.Student;
import com.examcore.model.Submission;
import com.examcore.model.Teacher;
import com.examcore.model.Test;
import com.examcore.model.TestType;
import com.examcore.model.User;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class Database {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long AUTO_FLUSH_INTERVAL_SECONDS = 5;

    private static final Database INSTANCE = createPersistentInstance();

    private Connection connection;
    private final String neonUrl;

    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Test> testsById = new ConcurrentHashMap<>();
    private final Map<String, Classroom> classroomsById = new ConcurrentHashMap<>();
    private final List<Submission> submissions = new ArrayList<>();
    private final List<Feedback> feedbackList = new ArrayList<>();
    private final List<String> systemLogs = new ArrayList<>();
    private final LeaderBoard leaderBoard = new LeaderBoard();

    /** Runs persistence (flush) in the background so callers (e.g. the UI thread) never block on the Neon round-trip. */
    private final ExecutorService flushExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "examcore-db-flush");
        t.setDaemon(true);
        return t;
    });

    private boolean dirty = true;
    private boolean usersDirty = true;
    private boolean testsDirty = true;
    private boolean classroomsDirty = true;
    private boolean teacherClassroomsDirty = true;
    private boolean studentAssignedTestsDirty = true;
    private boolean submissionsDirty = true;
    private boolean feedbackDirty = true;
    private boolean systemLogsDirty = true;
    private boolean leaderboardDirty = true;
    private boolean adminLogEntriesDirty = true;


    


     private static final String NEON_DATABASE_URL_ENV = "NEON_DATABASE_URL";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found on the classpath", e);
        }
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "PostgreSQL JDBC driver not found on the classpath. Add the org.postgresql:postgresql "
                            + "dependency to pom.xml.", e);
        }
    }


    public Database() {
        this(openEphemeralConnection(), false, null);
    }

    private Database(Connection connection, boolean persistent, String neonUrl) {
        this.connection = connection;
        this.neonUrl = neonUrl;
        initializeSchema();
        if (hasPersistedData()) {
            loadFromDisk();
        } else {
            seedData();
        }
        if (persistent) {
            startBackgroundFlush();
            registerShutdownHook();
        }
    }

    public static Database getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isConnectionAvailable() {
        try {
            reconnectIfNeeded();
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private synchronized void reconnectIfNeeded() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return;
        }

        if (neonUrl == null || neonUrl.isBlank()) {
            return;
        }

        connection = openNeonConnection(neonUrl);
    }

    private static Database createPersistentInstance() {
        try {
            String neonUrl = System.getenv(NEON_DATABASE_URL_ENV);
            Connection connection = (neonUrl != null && !neonUrl.isBlank())
                    ? openNeonConnection(neonUrl)
                    : openLocalSqliteConnection();
            return new Database(connection, true, neonUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize the persistent ExamCore database", e);
        }
    }

    private static Connection openNeonConnection(String neonUrl) throws SQLException {
        URI uri = URI.create(neonUrl);
        String userInfo = uri.getUserInfo();
        Properties props = new Properties();
        props.setProperty("sslmode", "require");
        if (userInfo != null && userInfo.contains(":")) {
            String[] parts = userInfo.split(":", 2);
            props.setProperty("user", parts[0]);
            props.setProperty("password", parts[1]);
        }
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    props.setProperty(kv[0], kv[1]);
                }
            }
        }
        int port = uri.getPort() != -1 ? uri.getPort() : 5432;
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
        return DriverManager.getConnection(jdbcUrl, props);
    }

    private static Connection openLocalSqliteConnection() throws SQLException, java.io.IOException {
        String appData = System.getenv("APPDATA");
        Path dbFile = (appData != null && !appData.isBlank())
                ? Path.of(appData, "ExamCore", "examcore.db")
                : Path.of(System.getProperty("user.home"), ".examcore-app", "examcore.db");
        Files.createDirectories(dbFile.getParent());
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile);
    }

    private static Connection openEphemeralConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite::memory:");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to open an in-memory ExamCore database", e);
        }
    }

    private void startBackgroundFlush() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "examcore-db-autosave");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flush, AUTO_FLUSH_INTERVAL_SECONDS, AUTO_FLUSH_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                flush();
            } catch (RuntimeException ignored) {
                // nothing more we can do at shutdown.
            }
        }, "examcore-db-shutdown-flush"));
    }

    // ---- Users ----

    public synchronized void saveUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        usersByUsername.put(user.getUsername(), user);
        usersById.put(user.getId(), user);
        markUsersDirty();
        scheduleFlush();
    }

    public Optional<User> findUserByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public Optional<User> findUserById(int id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public Collection<User> getAllUsers() {
        return List.copyOf(usersById.values());
    }

    // ---- Tests (Exams / Quizzes) ----

    public synchronized void saveTest(Test test) {
        if (test == null) {
            throw new IllegalArgumentException("Test cannot be null");
        }
        testsById.put(test.getTestID(), test);
        markTestsDirty();
        scheduleFlush();
    }

    public Optional<Test> findTestById(String testID) {
        if (testID == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(testsById.get(testID));
    }

    public Collection<Test> getAllTests() {
        return List.copyOf(testsById.values());
    }

    public synchronized void removeTest(String testID) {
        if (testID != null) {
            testsById.remove(testID);
            markTestsDirty();
            scheduleFlush();
        }
    }

    // ---- Classrooms ----

    public synchronized void saveClassroom(Classroom classroom) {
        if (classroom == null) {
            throw new IllegalArgumentException("Classroom cannot be null");
        }
        classroomsById.put(classroom.getClassID(), classroom);
        markClassroomsDirty();
        scheduleFlush();
    }

    public Optional<Classroom> findClassroomById(String classID) {
        return Optional.ofNullable(classroomsById.get(classID));
    }

    public Collection<Classroom> getAllClassrooms() {
        return List.copyOf(classroomsById.values());
    }

    // ---- Submissions ----

    /** Upserts a submission: registers it if new, otherwise a no-op since the tracked instance is mutated in place. */
    public synchronized void saveSubmission(Submission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission cannot be null");
        }
        if (!submissions.contains(submission)) {
            submissions.add(submission);
        }
        markSubmissionsDirty();
        scheduleFlush();
    }

    public synchronized List<Submission> getSubmissionsForStudent(Student student) {
        List<Submission> result = new ArrayList<>();
        for (Submission s : submissions) {
            if (s.getStudent().equals(student)) {
                result.add(s);
            }
        }
        return result;
    }

    public synchronized List<Submission> getSubmissionsForTest(Test test) {
        List<Submission> result = new ArrayList<>();
        for (Submission s : submissions) {
            if (s.getTest().equals(test)) {
                result.add(s);
            }
        }
        return result;
    }

    public synchronized List<Submission> getAllSubmissions() {
        return new ArrayList<>(submissions);
    }

    public synchronized Optional<Submission> findSubmission(Student student, Test test) {
        for (Submission s : submissions) {
            if (s.getStudent().equals(student) && s.getTest().equals(test) && s.isGraded()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }


    public synchronized Optional<Submission> findInProgressSubmission(Student student, Test test) {
        for (Submission s : submissions) {
            if (s.getStudent().equals(student)
                    && s.getTest().equals(test)
                    && !s.isSubmitted()
                    && !s.isGraded()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    // ---- Feedback ----

    public synchronized void saveFeedback(Feedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback cannot be null");
        }
        if (!feedbackList.contains(feedback)) {
            feedbackList.add(feedback);
        }
        markFeedbackDirty();
        scheduleFlush();
    }

    public synchronized List<Feedback> getFeedbackForTest(Test test) {
        List<Feedback> result = new ArrayList<>();
        for (Feedback f : feedbackList) {
            if (f.getTest().equals(test)) {
                result.add(f);
            }
        }
        return result;
    }

    // ---- System log ----

    public synchronized void logSystemEvent(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        systemLogs.add(LocalDateTime.now() + " - " + message);
        markSystemLogsDirty();
        scheduleFlush();
    }

    /** Most-recent-first view of every system-wide event recorded so far. */
    public synchronized List<String> getSystemLogs() {
        List<String> copy = new ArrayList<>(systemLogs);
        Collections.reverse(copy);
        return copy;
    }

    // ---- Leaderboard ----

    public LeaderBoard getLeaderBoard() {
        return leaderBoard;
    }


    /** Persists asynchronously so the calling thread (e.g. the UI) never blocks on the Neon round-trip. */
    private void scheduleFlush() {
        flushExecutor.submit(() -> {
            try {
                flush();
            } catch (RuntimeException e) {
                System.err.println("Background flush failed: " + e.getMessage());
            }
        });
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        try {
            reconnectIfNeeded();
            connection.setAutoCommit(false);
            try {
                if (usersDirty) {
                    writeUsers();
                }
                if (testsDirty) {
                    writeTests();
                }
                if (classroomsDirty) {
                    writeClassrooms();
                }
                if (teacherClassroomsDirty) {
                    writeTeacherClassrooms();
                }
                if (studentAssignedTestsDirty) {
                    writeStudentAssignedTests();
                }
                if (submissionsDirty) {
                    writeSubmissions();
                }
                if (feedbackDirty) {
                    writeFeedback();
                }
                if (systemLogsDirty) {
                    writeSystemLogs();
                }
                if (leaderboardDirty) {
                    writeLeaderboard();
                }
                if (adminLogEntriesDirty) {
                    writeAdminLogEntries();
                }
                connection.commit();
                dirty = false;
                usersDirty = false;
                testsDirty = false;
                classroomsDirty = false;
                teacherClassroomsDirty = false;
                studentAssignedTestsDirty = false;
                submissionsDirty = false;
                feedbackDirty = false;
                systemLogsDirty = false;
                leaderboardDirty = false;
                adminLogEntriesDirty = false;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw new IllegalStateException("Failed to persist ExamCore data", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist ExamCore data", e);
        }
    }

    /** Wipes all in-memory state and reloads the seed data. Intended for tests. */
    public synchronized void reset() {
        usersByUsername.clear();
        usersById.clear();
        testsById.clear();
        classroomsById.clear();
        submissions.clear();
        feedbackList.clear();
        systemLogs.clear();
        leaderBoard.getRankings().keySet().forEach(s -> leaderBoard.recordScore(s, 0));
        markAllDirty();
        seedData();
        flush();
    }

    // Schema

    private void initializeSchema() {
        String[] statements = {
                "CREATE TABLE IF NOT EXISTS users (" +
                        "username TEXT PRIMARY KEY, role TEXT NOT NULL, email TEXT, password_hash TEXT NOT NULL, " +
                        "is_active INTEGER NOT NULL, first_name TEXT, middle_name TEXT, last_name TEXT, school TEXT, " +
                        "department TEXT, profile_picture_path TEXT, exam_score INTEGER, quiz_score INTEGER, " +
                        "grade TEXT, is_verified INTEGER, admin_level INTEGER)",
                "CREATE TABLE IF NOT EXISTS tests (" +
                        "test_id TEXT PRIMARY KEY, test_type TEXT NOT NULL, title TEXT, duration_limit INTEGER, " +
                        "owner_username TEXT, topic TEXT, show_answers INTEGER)",
                "CREATE TABLE IF NOT EXISTS test_topics (test_id TEXT, position INTEGER, topic TEXT)",
                "CREATE TABLE IF NOT EXISTS test_tags (test_id TEXT, position INTEGER, tag TEXT)",
                "CREATE TABLE IF NOT EXISTS questions (" +
                        "test_id TEXT, question_id TEXT, position INTEGER, type TEXT, content TEXT, " +
                        "solution TEXT, hint TEXT, explanation TEXT, PRIMARY KEY (test_id, question_id))",
                "CREATE TABLE IF NOT EXISTS question_tags (test_id TEXT, question_id TEXT, position INTEGER, tag TEXT)",
                "CREATE TABLE IF NOT EXISTS question_choices (test_id TEXT, question_id TEXT, position INTEGER, choice TEXT)",
                "CREATE TABLE IF NOT EXISTS classrooms (class_id TEXT PRIMARY KEY, class_name TEXT)",
                "CREATE TABLE IF NOT EXISTS classroom_students (class_id TEXT, position INTEGER, username TEXT)",
                "CREATE TABLE IF NOT EXISTS classroom_tests (class_id TEXT, position INTEGER, test_id TEXT)",
                "CREATE TABLE IF NOT EXISTS teacher_classrooms (teacher_username TEXT, position INTEGER, class_id TEXT)",
                "CREATE TABLE IF NOT EXISTS student_assigned_tests (student_username TEXT, position INTEGER, test_id TEXT)",
                "CREATE TABLE IF NOT EXISTS submissions (" +
                        "submission_key INTEGER PRIMARY KEY, student_username TEXT, test_id TEXT, started_at TEXT, " +
                        "submitted_at TEXT, score INTEGER, graded INTEGER, time_expired INTEGER)",
                "CREATE TABLE IF NOT EXISTS submission_answers (" +
                        "submission_key INTEGER, question_id TEXT, answer TEXT, first_answered_at TEXT)",
                "CREATE TABLE IF NOT EXISTS submission_focus_loss (" +
                        "submission_key INTEGER, position INTEGER, occurred_at TEXT)",
                "CREATE TABLE IF NOT EXISTS feedback (" +
                        "feedback_key INTEGER PRIMARY KEY, student_username TEXT, test_id TEXT, content TEXT, " +
                        "is_read INTEGER, reported INTEGER)",
                "CREATE TABLE IF NOT EXISTS system_logs (log_key INTEGER PRIMARY KEY, entry TEXT)",
                "CREATE TABLE IF NOT EXISTS admin_log_entries (admin_username TEXT, position INTEGER, entry TEXT)",
                "CREATE TABLE IF NOT EXISTS leaderboard (student_username TEXT PRIMARY KEY, position INTEGER, score INTEGER)"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize the ExamCore schema", e);
        }
        migrateSchema();
    }

    private void migrateSchema() {
        tryAlterTable("ALTER TABLE tests ADD COLUMN show_answers INTEGER");
        tryAlterTable("ALTER TABLE questions ADD COLUMN explanation TEXT");
    }

    private void tryAlterTable(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            // Column already exists on a previously-migrated database; nothing to do.
        }
    }

    private boolean hasPersistedData() {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect the ExamCore database", e);
        }
    }

    // Loading 

    private void loadFromDisk() {
        loadUsers();
        loadTests();
        loadClassrooms();
        loadTeacherClassrooms();
        loadStudentAssignedTests();
        loadSubmissions();
        loadFeedback();
        loadLeaderboard();
        loadSystemLogs();
        loadAdminLogEntries();
    }

    private void loadUsers() {
        String sql = "SELECT * FROM users";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String username = rs.getString("username");
                String email = rs.getString("email");
                String passwordHash = rs.getString("password_hash");
                boolean isActive = rs.getInt("is_active") != 0;
                String firstName = rs.getString("first_name");
                String middleName = rs.getString("middle_name");
                String lastName = rs.getString("last_name");
                String school = rs.getString("school");
                String department = rs.getString("department");
                String profilePicturePath = rs.getString("profile_picture_path");

                User user = switch (Role.valueOf(rs.getString("role"))) {
                    case STUDENT -> Student.restoreFromStorage(username, email, passwordHash, isActive,
                            firstName, middleName, lastName, school, department, profilePicturePath,
                            rs.getInt("exam_score"), rs.getInt("quiz_score"), rs.getString("grade"));
                    case TEACHER -> Teacher.restoreFromStorage(username, email, passwordHash, isActive,
                            firstName, middleName, lastName, school, department, profilePicturePath,
                            rs.getInt("is_verified") != 0);
                    case ADMIN -> Admin.restoreFromStorage(username, email, passwordHash, isActive,
                            firstName, middleName, lastName, school, department, profilePicturePath,
                            rs.getInt("admin_level"));
                };
                usersByUsername.put(user.getUsername(), user);
                usersById.put(user.getId(), user);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load users", e);
        }
    }

    private void loadTests() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM tests")) {
            while (rs.next()) {
                String testId = rs.getString("test_id");
                String title = rs.getString("title");
                int durationLimit = rs.getInt("duration_limit");

                Test test = TestType.valueOf(rs.getString("test_type")) == TestType.EXAM
                        ? new Exam(testId, title, durationLimit)
                        : new Quiz(testId, title, durationLimit, rs.getString("topic"));
                test.setShowAnswersAfterExam(rs.getInt("show_answers") != 0);

                String ownerUsername = rs.getString("owner_username");
                if (ownerUsername != null && usersByUsername.get(ownerUsername) instanceof Teacher teacher) {
                    test.setOwner(teacher);
                }
                testsById.put(test.getTestID(), test);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load tests", e);
        }

        loadTestTopics();
        loadTestTags();
        loadQuestions();
    }

    private void loadTestTopics() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT test_id, topic FROM test_topics ORDER BY test_id, position")) {
            while (rs.next()) {
                if (testsById.get(rs.getString("test_id")) instanceof Exam exam) {
                    exam.addTopic(rs.getString("topic"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load test topics", e);
        }
    }

    private void loadTestTags() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT test_id, tag FROM test_tags ORDER BY test_id, position")) {
            while (rs.next()) {
                Test test = testsById.get(rs.getString("test_id"));
                if (test != null) {
                    test.addTag(rs.getString("tag"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load test tags", e);
        }
    }

    private void loadQuestions() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM questions ORDER BY test_id, position")) {
            while (rs.next()) {
                Test test = testsById.get(rs.getString("test_id"));
                if (test == null) {
                    continue;
                }
                String questionId = rs.getString("question_id");
                String content = rs.getString("content");
                String solution = rs.getString("solution");

                Question question = QuestionType.valueOf(rs.getString("type")) == QuestionType.MULTIPLE_CHOICE
                        ? new MultipleChoiceQuestion(questionId, content, solution)
                        : new FillInBlankQuestion(questionId, content, solution);
                question.setHint(rs.getString("hint"));
                question.setExplanation(rs.getString("explanation"));
                test.addQuestion(question);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load questions", e);
        }

        Map<String, Question> questionsByKey = new HashMap<>();
        for (Test test : testsById.values()) {
            for (Question question : test.getQuestions()) {
                questionsByKey.put(questionKey(test.getTestID(), question.getQuestionID()), question);
            }
        }
        loadQuestionChoices(questionsByKey);
        loadQuestionTags(questionsByKey);
    }

    private static String questionKey(String testId, String questionId) {
        return testId + "nul" + questionId;
    }

    private void loadQuestionChoices(Map<String, Question> questionsByKey) {
        String sql = "SELECT test_id, question_id, choice FROM question_choices ORDER BY test_id, question_id, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String key = questionKey(rs.getString("test_id"), rs.getString("question_id"));
                if (questionsByKey.get(key) instanceof MultipleChoiceQuestion mcq) {
                    mcq.addChoice(rs.getString("choice"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load question choices", e);
        }
    }

    private void loadQuestionTags(Map<String, Question> questionsByKey) {
        Map<String, List<String>> tagsByQuestion = new LinkedHashMap<>();
        String sql = "SELECT test_id, question_id, tag FROM question_tags ORDER BY test_id, question_id, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String key = questionKey(rs.getString("test_id"), rs.getString("question_id"));
                tagsByQuestion.computeIfAbsent(key, k -> new ArrayList<>()).add(rs.getString("tag"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load question tags", e);
        }
        tagsByQuestion.forEach((key, tags) -> {
            Question question = questionsByKey.get(key);
            if (question != null) {
                question.setTags(tags);
            }
        });
    }

    private void loadClassrooms() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM classrooms")) {
            while (rs.next()) {
                Classroom classroom = new Classroom(rs.getString("class_id"), rs.getString("class_name"));
                classroomsById.put(classroom.getClassID(), classroom);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load classrooms", e);
        }

        String studentSql = "SELECT class_id, username FROM classroom_students ORDER BY class_id, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(studentSql)) {
            while (rs.next()) {
                Classroom classroom = classroomsById.get(rs.getString("class_id"));
                User user = usersByUsername.get(rs.getString("username"));
                if (classroom != null && user instanceof Student student) {
                    classroom.addStudent(student);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load classroom rosters", e);
        }

        String testSql = "SELECT class_id, test_id FROM classroom_tests ORDER BY class_id, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(testSql)) {
            while (rs.next()) {
                Classroom classroom = classroomsById.get(rs.getString("class_id"));
                Test test = testsById.get(rs.getString("test_id"));
                if (classroom != null && test != null) {
                    classroom.assignTest(test);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load classroom test assignments", e);
        }
    }

    private void loadTeacherClassrooms() {
        String sql = "SELECT teacher_username, class_id FROM teacher_classrooms ORDER BY teacher_username, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                User user = usersByUsername.get(rs.getString("teacher_username"));
                Classroom classroom = classroomsById.get(rs.getString("class_id"));
                if (user instanceof Teacher teacher && classroom != null) {
                    teacher.addManagedClassroom(classroom);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load teacher classrooms", e);
        }
    }

    private void loadStudentAssignedTests() {
        String sql = "SELECT student_username, test_id FROM student_assigned_tests ORDER BY student_username, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                User user = usersByUsername.get(rs.getString("student_username"));
                Test test = testsById.get(rs.getString("test_id"));
                if (user instanceof Student student && test != null) {
                    student.assignTest(test);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load student assigned tests", e);
        }
    }

    private void loadSubmissions() {
        String sql = "SELECT * FROM submissions ORDER BY submission_key";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                long key = rs.getLong("submission_key");
                User user = usersByUsername.get(rs.getString("student_username"));
                Test test = testsById.get(rs.getString("test_id"));
                if (!(user instanceof Student student) || test == null) {
                    continue;
                }

                LocalDateTime startedAt = parseTimestamp(rs.getString("started_at"));
                LocalDateTime submittedAt = parseTimestamp(rs.getString("submitted_at"));
                int score = rs.getInt("score");
                boolean graded = rs.getInt("graded") != 0;
                boolean timeExpired = rs.getInt("time_expired") != 0;

                Map<String, String> answers = new LinkedHashMap<>();
                Map<String, LocalDateTime> firstAnsweredAt = new LinkedHashMap<>();
                String answerSql = "SELECT question_id, answer, first_answered_at FROM submission_answers "
                        + "WHERE submission_key = ?";
                try (PreparedStatement ps = connection.prepareStatement(answerSql)) {
                    ps.setLong(1, key);
                    try (ResultSet ars = ps.executeQuery()) {
                        while (ars.next()) {
                            String questionId = ars.getString("question_id");
                            answers.put(questionId, ars.getString("answer"));
                            firstAnsweredAt.put(questionId, parseTimestamp(ars.getString("first_answered_at")));
                        }
                    }
                }

                List<LocalDateTime> focusLossEvents = new ArrayList<>();
                String focusSql = "SELECT occurred_at FROM submission_focus_loss WHERE submission_key = ? "
                        + "ORDER BY position";
                try (PreparedStatement ps = connection.prepareStatement(focusSql)) {
                    ps.setLong(1, key);
                    try (ResultSet frs = ps.executeQuery()) {
                        while (frs.next()) {
                            focusLossEvents.add(parseTimestamp(frs.getString("occurred_at")));
                        }
                    }
                }

                Submission submission = Submission.restoreFromStorage(student, test, startedAt, submittedAt, score,
                        graded, timeExpired, answers, firstAnsweredAt, focusLossEvents);
                submissions.add(submission);
                if (graded) {
                    student.getActivityLog().logCompletedTest(submission);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load submissions", e);
        }
    }

    private void loadFeedback() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM feedback ORDER BY feedback_key")) {
            while (rs.next()) {
                User user = usersByUsername.get(rs.getString("student_username"));
                Test test = testsById.get(rs.getString("test_id"));
                if (!(user instanceof Student student) || test == null) {
                    continue;
                }
                Feedback feedback = new Feedback(student, test, rs.getString("content"));
                if (rs.getInt("is_read") != 0) {
                    feedback.markRead();
                }
                if (rs.getInt("reported") != 0) {
                    feedback.markReported();
                }
                feedbackList.add(feedback);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load feedback", e);
        }
    }

    private void loadLeaderboard() {
        String sql = "SELECT student_username, score FROM leaderboard ORDER BY position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                if (usersByUsername.get(rs.getString("student_username")) instanceof Student student) {
                    leaderBoard.recordScore(student, rs.getInt("score"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load the leaderboard", e);
        }
    }

    private void loadSystemLogs() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT entry FROM system_logs ORDER BY log_key")) {
            while (rs.next()) {
                systemLogs.add(rs.getString("entry"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load system logs", e);
        }
    }

    private void loadAdminLogEntries() {
        Map<String, List<String>> entriesByAdmin = new LinkedHashMap<>();
        String sql = "SELECT admin_username, entry FROM admin_log_entries ORDER BY admin_username, position";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                entriesByAdmin.computeIfAbsent(rs.getString("admin_username"), k -> new ArrayList<>())
                        .add(rs.getString("entry"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load admin log entries", e);
        }
        entriesByAdmin.forEach((username, entries) -> {
            if (usersByUsername.get(username) instanceof Admin admin) {
                admin.loadSystemLogEntries(entries);
            }
        });
    }

    // Writing 

    private void writeUsers() throws SQLException {
        executeUpdate("DELETE FROM users");
        String sql = "INSERT INTO users (username, role, email, password_hash, is_active, first_name, "
                + "middle_name, last_name, school, department, profile_picture_path, exam_score, quiz_score, "
                + "grade, is_verified, admin_level) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (User user : usersById.values()) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getRole().name());
                ps.setString(3, user.getEmail());
                ps.setString(4, user.getPasswordHash());
                ps.setInt(5, user.isActive() ? 1 : 0);
                ps.setString(6, user.getFirstName());
                ps.setString(7, user.getMiddleName());
                ps.setString(8, user.getLastName());
                ps.setString(9, user.getSchool());
                ps.setString(10, user.getDepartment());
                ps.setString(11, user.getProfilePicturePath());
                if (user instanceof Student student) {
                    ps.setInt(12, student.getExamScore());
                    ps.setInt(13, student.getQuizScore());
                    ps.setString(14, student.getGrade());
                } else {
                    ps.setNull(12, Types.INTEGER);
                    ps.setNull(13, Types.INTEGER);
                    ps.setNull(14, Types.VARCHAR);
                }
                ps.setNull(15, Types.INTEGER);
                ps.setNull(16, Types.INTEGER);
                if (user instanceof Teacher teacher) {
                    ps.setInt(15, teacher.isVerified() ? 1 : 0);
                }
                if (user instanceof Admin admin) {
                    ps.setInt(16, admin.getAdminLevel());
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeTests() throws SQLException {
        executeUpdate("DELETE FROM tests");
        executeUpdate("DELETE FROM test_topics");
        executeUpdate("DELETE FROM test_tags");
        executeUpdate("DELETE FROM questions");
        executeUpdate("DELETE FROM question_choices");
        executeUpdate("DELETE FROM question_tags");

        String testSql = "INSERT INTO tests (test_id, test_type, title, duration_limit, owner_username, topic, "
                + "show_answers) VALUES (?,?,?,?,?,?,?)";
        String topicSql = "INSERT INTO test_topics (test_id, position, topic) VALUES (?,?,?)";
        String tagSql = "INSERT INTO test_tags (test_id, position, tag) VALUES (?,?,?)";
        String questionSql = "INSERT INTO questions (test_id, question_id, position, type, content, solution, "
                + "hint, explanation) VALUES (?,?,?,?,?,?,?,?)";
        String choiceSql = "INSERT INTO question_choices (test_id, question_id, position, choice) VALUES (?,?,?,?)";
        String questionTagSql = "INSERT INTO question_tags (test_id, question_id, position, tag) VALUES (?,?,?,?)";

        try (PreparedStatement testPs = connection.prepareStatement(testSql);
             PreparedStatement topicPs = connection.prepareStatement(topicSql);
             PreparedStatement tagPs = connection.prepareStatement(tagSql);
             PreparedStatement questionPs = connection.prepareStatement(questionSql);
             PreparedStatement choicePs = connection.prepareStatement(choiceSql);
             PreparedStatement questionTagPs = connection.prepareStatement(questionTagSql)) {

            for (Test test : testsById.values()) {
                testPs.setString(1, test.getTestID());
                testPs.setString(2, test.getTestType().name());
                testPs.setString(3, test.getTitle());
                testPs.setInt(4, test.getDurationLimit());
                testPs.setString(5, test.getOwner() != null ? test.getOwner().getUsername() : null);
                testPs.setString(6, test instanceof Quiz quiz ? quiz.getTopic() : null);
                testPs.setInt(7, test.isShowAnswersAfterExam() ? 1 : 0);
                testPs.addBatch();

                if (test instanceof Exam exam) {
                    int position = 0;
                    for (String topic : exam.getTopics()) {
                        topicPs.setString(1, test.getTestID());
                        topicPs.setInt(2, position++);
                        topicPs.setString(3, topic);
                        topicPs.addBatch();
                    }
                }

                int tagPosition = 0;
                for (String tag : test.getTags()) {
                    tagPs.setString(1, test.getTestID());
                    tagPs.setInt(2, tagPosition++);
                    tagPs.setString(3, tag);
                    tagPs.addBatch();
                }

                int questionPosition = 0;
                for (Question question : test.getQuestions()) {
                    questionPs.setString(1, test.getTestID());
                    questionPs.setString(2, question.getQuestionID());
                    questionPs.setInt(3, questionPosition++);
                    questionPs.setString(4, question.getType().name());
                    questionPs.setString(5, question.getContent());
                    questionPs.setString(6, question.getSolution());
                    questionPs.setString(7, question.getHint());
                    questionPs.setString(8, question.getExplanation());
                    questionPs.addBatch();

                    if (question instanceof MultipleChoiceQuestion mcq) {
                        int choicePosition = 0;
                        for (String choice : mcq.getChoices()) {
                            choicePs.setString(1, test.getTestID());
                            choicePs.setString(2, question.getQuestionID());
                            choicePs.setInt(3, choicePosition++);
                            choicePs.setString(4, choice);
                            choicePs.addBatch();
                        }
                    }

                    int questionTagPosition = 0;
                    for (String tag : question.getTags()) {
                        questionTagPs.setString(1, test.getTestID());
                        questionTagPs.setString(2, question.getQuestionID());
                        questionTagPs.setInt(3, questionTagPosition++);
                        questionTagPs.setString(4, tag);
                        questionTagPs.addBatch();
                    }
                }
            }

            testPs.executeBatch();
            topicPs.executeBatch();
            tagPs.executeBatch();
            questionPs.executeBatch();
            choicePs.executeBatch();
            questionTagPs.executeBatch();
        }
    }

    private void writeClassrooms() throws SQLException {
        executeUpdate("DELETE FROM classrooms");
        executeUpdate("DELETE FROM classroom_students");
        executeUpdate("DELETE FROM classroom_tests");

        String classroomSql = "INSERT INTO classrooms (class_id, class_name) VALUES (?,?)";
        String studentSql = "INSERT INTO classroom_students (class_id, position, username) VALUES (?,?,?)";
        String testSql = "INSERT INTO classroom_tests (class_id, position, test_id) VALUES (?,?,?)";

        try (PreparedStatement classroomPs = connection.prepareStatement(classroomSql);
             PreparedStatement studentPs = connection.prepareStatement(studentSql);
             PreparedStatement testPs = connection.prepareStatement(testSql)) {

            for (Classroom classroom : classroomsById.values()) {
                classroomPs.setString(1, classroom.getClassID());
                classroomPs.setString(2, classroom.getClassName());
                classroomPs.addBatch();

                int studentPosition = 0;
                for (Student student : classroom.getStudentList()) {
                    studentPs.setString(1, classroom.getClassID());
                    studentPs.setInt(2, studentPosition++);
                    studentPs.setString(3, student.getUsername());
                    studentPs.addBatch();
                }

                int testPosition = 0;
                for (Test test : classroom.getAssignedExams()) {
                    testPs.setString(1, classroom.getClassID());
                    testPs.setInt(2, testPosition++);
                    testPs.setString(3, test.getTestID());
                    testPs.addBatch();
                }
            }

            classroomPs.executeBatch();
            studentPs.executeBatch();
            testPs.executeBatch();
        }
    }

    private void writeTeacherClassrooms() throws SQLException {
        executeUpdate("DELETE FROM teacher_classrooms");
        String sql = "INSERT INTO teacher_classrooms (teacher_username, position, class_id) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (User user : usersById.values()) {
                if (user instanceof Teacher teacher) {
                    int position = 0;
                    for (Classroom classroom : teacher.getManagedClassrooms()) {
                        ps.setString(1, teacher.getUsername());
                        ps.setInt(2, position++);
                        ps.setString(3, classroom.getClassID());
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void writeStudentAssignedTests() throws SQLException {
        executeUpdate("DELETE FROM student_assigned_tests");
        String sql = "INSERT INTO student_assigned_tests (student_username, position, test_id) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (User user : usersById.values()) {
                if (user instanceof Student student) {
                    int position = 0;
                    for (Test test : student.getAssignedExams()) {
                        ps.setString(1, student.getUsername());
                        ps.setInt(2, position++);
                        ps.setString(3, test.getTestID());
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void writeSubmissions() throws SQLException {
        executeUpdate("DELETE FROM submissions");
        executeUpdate("DELETE FROM submission_answers");
        executeUpdate("DELETE FROM submission_focus_loss");

        String submissionSql = "INSERT INTO submissions (submission_key, student_username, test_id, started_at, "
                + "submitted_at, score, graded, time_expired) VALUES (?,?,?,?,?,?,?,?)";
        String answerSql = "INSERT INTO submission_answers (submission_key, question_id, answer, "
                + "first_answered_at) VALUES (?,?,?,?)";
        String focusSql = "INSERT INTO submission_focus_loss (submission_key, position, occurred_at) VALUES (?,?,?)";

        try (PreparedStatement submissionPs = connection.prepareStatement(submissionSql);
             PreparedStatement answerPs = connection.prepareStatement(answerSql);
             PreparedStatement focusPs = connection.prepareStatement(focusSql)) {

            long key = 0;
            for (Submission submission : submissions) {
                key++;
                submissionPs.setLong(1, key);
                submissionPs.setString(2, submission.getStudent().getUsername());
                submissionPs.setString(3, submission.getTest().getTestID());
                submissionPs.setString(4, formatTimestamp(submission.getStartedAt()));
                submissionPs.setString(5, formatTimestamp(submission.getSubmittedAt()));
                submissionPs.setInt(6, submission.getScore());
                submissionPs.setInt(7, submission.isGraded() ? 1 : 0);
                submissionPs.setInt(8, submission.isTimeExpired() ? 1 : 0);
                submissionPs.addBatch();

                for (Map.Entry<String, String> entry : submission.getAnswers().entrySet()) {
                    Duration timeToFirst = submission.getTimeToFirstAnswer(entry.getKey());
                    LocalDateTime firstAnsweredAt = timeToFirst == null
                            ? null : submission.getStartedAt().plus(timeToFirst);
                    answerPs.setLong(1, key);
                    answerPs.setString(2, entry.getKey());
                    answerPs.setString(3, entry.getValue());
                    answerPs.setString(4, formatTimestamp(firstAnsweredAt));
                    answerPs.addBatch();
                }

                int position = 0;
                for (LocalDateTime occurredAt : submission.getFocusLossEvents()) {
                    focusPs.setLong(1, key);
                    focusPs.setInt(2, position++);
                    focusPs.setString(3, formatTimestamp(occurredAt));
                    focusPs.addBatch();
                }
            }

            submissionPs.executeBatch();
            answerPs.executeBatch();
            focusPs.executeBatch();
        }
    }

    private void writeFeedback() throws SQLException {
        executeUpdate("DELETE FROM feedback");
        String sql = "INSERT INTO feedback (feedback_key, student_username, test_id, content, is_read, reported) "
                + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            long key = 0;
            for (Feedback feedback : feedbackList) {
                key++;
                ps.setLong(1, key);
                ps.setString(2, feedback.getStudent().getUsername());
                ps.setString(3, feedback.getTest().getTestID());
                ps.setString(4, feedback.getContent());
                ps.setInt(5, feedback.isRead() ? 1 : 0);
                ps.setInt(6, feedback.isReported() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeSystemLogs() throws SQLException {
        executeUpdate("DELETE FROM system_logs");
        String sql = "INSERT INTO system_logs (log_key, entry) VALUES (?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            long key = 0;
            for (String entry : systemLogs) {
                key++;
                ps.setLong(1, key);
                ps.setString(2, entry);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeLeaderboard() throws SQLException {
        executeUpdate("DELETE FROM leaderboard");
        String sql = "INSERT INTO leaderboard (student_username, position, score) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int position = 0;
            for (Map.Entry<Student, Integer> entry : leaderBoard.getRankings().entrySet()) {
                ps.setString(1, entry.getKey().getUsername());
                ps.setInt(2, position++);
                ps.setInt(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void writeAdminLogEntries() throws SQLException {
        executeUpdate("DELETE FROM admin_log_entries");
        String sql = "INSERT INTO admin_log_entries (admin_username, position, entry) VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (User user : usersById.values()) {
                if (user instanceof Admin admin) {
                    int position = 0;
                    for (String entry : admin.viewSystemLogs()) {
                        ps.setString(1, admin.getUsername());
                        ps.setInt(2, position++);
                        ps.setString(3, entry);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
    }

    private void markUsersDirty() {
        dirty = true;
        usersDirty = true;
    }

    private void markTestsDirty() {
        dirty = true;
        testsDirty = true;
    }

    private void markClassroomsDirty() {
        dirty = true;
        classroomsDirty = true;
        teacherClassroomsDirty = true;
        studentAssignedTestsDirty = true;
    }

    private void markSubmissionsDirty() {
        dirty = true;
        submissionsDirty = true;
    }

    private void markFeedbackDirty() {
        dirty = true;
        feedbackDirty = true;
    }

    private void markSystemLogsDirty() {
        dirty = true;
        systemLogsDirty = true;
        adminLogEntriesDirty = true;
    }

    private void markAllDirty() {
        dirty = true;
        usersDirty = true;
        testsDirty = true;
        classroomsDirty = true;
        teacherClassroomsDirty = true;
        studentAssignedTestsDirty = true;
        submissionsDirty = true;
        feedbackDirty = true;
        systemLogsDirty = true;
        leaderboardDirty = true;
        adminLogEntriesDirty = true;
    }

    private void executeUpdate(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String formatTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(TIMESTAMP_FORMAT);
    }

    private static LocalDateTime parseTimestamp(String text) {
        return text == null ? null : LocalDateTime.parse(text, TIMESTAMP_FORMAT);
    }

    // Seed data

    private void seedData() {
        Student amil = new Student("amil.ibrahimli", "amil.ibrahimli@ug.bilkent.edu.tr", "password123");
        amil.setFirstName("Amil");
        amil.setLastName("Ibrahimli");
        amil.setSchool("Bilkent University");
        amil.setDepartment("CS");
        amil.setGrade("2");
        saveUser(amil);

        Teacher erdem = new Teacher("erdem.karacay", "erdem.karacay@bilkent.edu.tr", "password123");
        erdem.setFirstName("Ali");
        erdem.setMiddleName("Erdem");
        erdem.setLastName("Karacay");
        erdem.setSchool("Bilkent University");
        erdem.setDepartment("CS");
        erdem.setVerified(true);
        saveUser(erdem);

        Admin admin = new Admin("admin1", "admin@examcore.local", "admin123");
        admin.setFirstName("System");
        admin.setLastName("Admin");
        saveUser(admin);

        String[] leaderboardNames = {
                "Boyukagha Aghayev", "Mete Namazov", "Yusif Ahmedzade",
                "Javidan Garagozov", "Amil Ibrahimli (me)", "Ogtay Talishkhanli"
        };
        for (String name : leaderboardNames) {
            Student s;
            if (name.startsWith("Amil")) {
                s = amil;
            } else {
                String username = name.toLowerCase().replace(" ", ".");
                s = new Student(username, username + "@ug.bilkent.edu.tr", "password123");
                String[] parts = name.split(" ", 2);
                s.setFirstName(parts[0]);
                if (parts.length > 1) {
                    s.setLastName(parts[1]);
                }
                saveUser(s);
            }
            s.addExamScore(70);
            s.addQuizScore(20);
            leaderBoard.recordScore(s, s.getTotalScore());
        }

        Exam math101 = new Exam("MATH101", "MATH101", 120);
        addPlaceholderQuestions(math101, 30);
        math101.setOwner(erdem);
        saveTest(math101);

        Exam cs211 = new Exam("CS211", "CS211", 60);
        addPlaceholderQuestions(cs211, 30);
        cs211.setOwner(erdem);
        saveTest(cs211);

        Exam phys101 = new Exam("PHYS101", "PHYS101", 120);
        addPlaceholderQuestions(phys101, 30);
        phys101.setOwner(erdem);
        saveTest(phys101);

        Quiz integralQuiz = new Quiz("QUIZ-INTEGRAL", "Applications of Integral", 20, "Calculus");
        addPlaceholderQuestions(integralQuiz, 4);
        integralQuiz.setOwner(erdem);
        saveTest(integralQuiz);

        Quiz exceptionQuiz = new Quiz("QUIZ-EXCEPTION", "Exception Handling", 20, "Java");
        addPlaceholderQuestions(exceptionQuiz, 4);
        exceptionQuiz.setOwner(erdem);
        saveTest(exceptionQuiz);

        Quiz newtonQuiz = new Quiz("QUIZ-NEWTON", "Newton's Laws", 30, "Physics");
        addPlaceholderQuestions(newtonQuiz, 10);
        newtonQuiz.setOwner(erdem);
        saveTest(newtonQuiz);

        Quiz math132Quiz3 = new Quiz("MATH132-QUIZ3", "MATH132 - QUIZ 3", 18, "Combinatorics");
        FillInBlankQuestion pigeonhole = new FillInBlankQuestion(
                "MATH132-Q1",
                "A company stores products in a warehouse. Storage bins in this warehouse are specified by "
                        + "their aisle, location in the aisle, and shelf. There are 50 aisles, 85 horizontal "
                        + "locations in each aisle, and 5 shelves throughout the warehouse. What is the least "
                        + "number of products the company can have so that at least two products must be "
                        + "stored in the same bin?",
                "21251");
        math132Quiz3.addQuestion(pigeonhole);
        MultipleChoiceQuestion subsets = new MultipleChoiceQuestion(
                "MATH132-Q2",
                "What is the total number of distinct subsets that can be formed from a set containing "
                        + "exactly 5 elements?",
                "32");
        subsets.addChoice("10");
        subsets.addChoice("25");
        subsets.addChoice("31");
        subsets.addChoice("32");
        math132Quiz3.addQuestion(subsets);
        math132Quiz3.setOwner(erdem);
        saveTest(math132Quiz3);

        Quiz cs102Quiz2 = new Quiz("CS102-QUIZ2", "CS102 - QUIZ 2", 20, "OOP");
        MultipleChoiceQuestion encapsulation = new MultipleChoiceQuestion(
                "CS102-Q1",
                "Which object-oriented programming principle describes the practice of keeping the "
                        + "internal data of an object hidden from the outside and only allowing access "
                        + "through specifically designated public methods?",
                "Encapsulation");
        encapsulation.addChoice("Inheritance");
        encapsulation.addChoice("Polymorphism");
        encapsulation.addChoice("Encapsulation");
        encapsulation.addChoice("Abstraction");
        cs102Quiz2.addQuestion(encapsulation);
        cs102Quiz2.setOwner(erdem);
        saveTest(cs102Quiz2);

        Exam oop = new Exam("OOP-EXAM", "OOP", 120);
        addPlaceholderQuestions(oop, 10);
        oop.setOwner(erdem);
        saveTest(oop);

        Exam javaCollections = new Exam("JCF-EXAM", "Java Collections Framework", 60);
        addPlaceholderQuestions(javaCollections, 30);
        javaCollections.setOwner(erdem);
        saveTest(javaCollections);

        Exam recursion = new Exam("RECURSION-EXAM", "Recursion", 120);
        addPlaceholderQuestions(recursion, 5);
        recursion.setOwner(erdem);
        saveTest(recursion);

        Classroom cs1021 = new Classroom("CS102-1", "CS102-1");
        Classroom cs1022 = new Classroom("CS102-2", "CS102-2");
        Classroom cs1023 = new Classroom("CS102-3", "CS102-3");
        cs1021.addStudent(amil);
        cs1021.assignTest(math132Quiz3);
        cs1021.assignTest(cs102Quiz2);
        cs1021.assignTest(recursion);
        amil.assignTest(math132Quiz3);
        amil.assignTest(cs102Quiz2);
        amil.assignTest(recursion);
        erdem.addManagedClassroom(cs1021);
        erdem.addManagedClassroom(cs1022);
        erdem.addManagedClassroom(cs1023);
        saveClassroom(cs1021);
        saveClassroom(cs1022);
        saveClassroom(cs1023);
    }

    private void addPlaceholderQuestions(Test test, int count) {
        for (int i = 1; i <= count; i++) {
            MultipleChoiceQuestion q = new MultipleChoiceQuestion(
                    test.getTestID() + "-Q" + i,
                    "Sample question " + i + " for " + test.getTitle(),
                    "A");
            q.addChoice("A");
            q.addChoice("B");
            q.addChoice("C");
            q.addChoice("D");
            test.addQuestion(q);
        }
    }
}