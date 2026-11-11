package registrar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Public tests for the queries lab. Every test starts from fresh tables in this run's own lab
 * database. Task 1 runs your seed.sql with the real psql client; Tasks 2 to 6 load the same
 * registrar data themselves, so each task can pass on its own.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class QueriesLabTest {
    private static Connection db;

    @BeforeAll
    static void createDatabase() throws Exception {
        db = LabDatabase.create();
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        LabDatabase.drop(db);
    }

    private static List<List<String>> table(String sql) throws SQLException {
        return LabDatabase.rows(db, sql);
    }

    private static final String COURSES = "SELECT code, title, capacity, prerequisite_code FROM courses ORDER BY code";
    private static final String STUDENTS = "SELECT id, name, email, city FROM students ORDER BY id";
    private static final String ENROLLMENTS =
            "SELECT student_id, course_code, enrolled_at, grade FROM enrollments ORDER BY student_id, course_code";

    private static String sql(String task, String file) throws Exception {
        String text = LabDatabase.learnerSql(file);
        if (text == null) {
            fail(task + ": " + file + " has no SQL yet. Write it below the TODO line (README.md, " + task + ").");
        }
        return text;
    }

    private static List<List<String>> report(String task, String file, List<String> columns) throws Exception {
        String text = sql(task, file);
        List<List<String>> result;
        try {
            List<String> actual = LabDatabase.columns(db, text);
            assertEquals(columns, actual, task + ": " + file + " must return the columns " + columns
                    + " in that order (name them with AS); it returned " + actual + ".");
            result = LabDatabase.rows(db, text);
        } catch (SQLException e) {
            String advice = String.valueOf(e.getMessage()).contains("must appear in the GROUP BY clause")
                    ? " Every selected column must either be listed in GROUP BY or be inside an aggregate function."
                    : "";
            fail(task + ": " + file + " did not run. PostgreSQL said: " + e.getMessage() + advice);
            return null;
        }
        return result;
    }

    private static List<String> row(String... values) {
        return List.of(values);
    }

    private static void sameRows(String task, String file, List<List<String>> expected, List<List<String>> actual, String hint) {
        assertEquals(expected, actual, task + ": " + file + " returned the wrong rows or order. "
                + (hint == null ? "" : hint + " ") + "Expected " + expected + " but got " + actual + ".");
    }

    @Test
    void task1Seed() throws Exception {
        sql("Task 1", "seed.sql");
        LabDatabase.emptyTables(db);
        LabDatabase.PsqlRun run = LabDatabase.psql("seed.sql");
        if (run.exitCode() != 0) {
            fail("Task 1: psql stopped on an error in seed.sql (exit " + run.exitCode() + "). psql said: " + run.output()
                    + " -- if it names a foreign key, insert the parent rows (courses, students) before the rows that refer to them.");
        }
        List<List<String>> courses = table(COURSES);
        List<List<String>> students = table(STUDENTS);
        List<List<String>> enrollments = table(ENROLLMENTS);

        LabDatabase.registrarData(db);
        List<List<String>> wantCourses = table(COURSES);
        List<List<String>> wantStudents = table(STUDENTS);
        List<List<String>> wantEnrollments = table(ENROLLMENTS);

        assertEquals(wantCourses, courses, "Task 1 (courses): after seed.sql, courses should hold exactly the four courses in README.md, "
                + "Task 1. Expected " + wantCourses + " but found " + courses + ".");
        List<List<String>> csvStudents = students.stream().filter(r -> Integer.parseInt(r.get(0)) < 900).toList();
        List<List<String>> wantCsvStudents = wantStudents.stream().filter(r -> Integer.parseInt(r.get(0)) < 900).toList();
        assertEquals(wantCsvStudents, csvStudents, "Task 1 (students from CSV): students 1-8 should match ../fixtures/students.csv, "
                + "with an empty city loaded as NULL. Expected " + wantCsvStudents + " but found " + csvStudents + ".");
        List<List<String>> guests = students.stream().filter(r -> Integer.parseInt(r.get(0)) >= 900).toList();
        List<List<String>> wantGuests = wantStudents.stream().filter(r -> Integer.parseInt(r.get(0)) >= 900).toList();
        assertEquals(wantGuests, guests, "Task 1 (guest students): generate_series should add exactly students 901-905 named "
                + "'Guest 901' ... with emails guest901@registrar.test ... and no city. Expected " + wantGuests + " but found " + guests + ".");
        assertEquals(wantEnrollments, enrollments, "Task 1 (enrollments): enrollments should match ../fixtures/enrollments.csv, "
                + "with an empty grade loaded as NULL. Expected " + wantEnrollments + " but found " + enrollments + ".");

        String lower = LabDatabase.codeOnly("seed.sql").toLowerCase(Locale.ROOT);
        int copies = lower.split("\\\\copy", -1).length - 1;
        assertTrue(copies >= 2, "Task 1 (static check of the file text): load both CSV files with the psql \\copy command, "
                + "as the task asks; seed.sql contains " + copies + " \\copy command(s).");
        assertTrue(lower.contains("generate_series"), "Task 1 (static check of the file text): add the guest students with "
                + "INSERT ... SELECT and generate_series, as the task asks.");
    }

    @Test
    void task2Changes() throws Exception {
        String text = sql("Task 2", "changes.sql");
        LabDatabase.registrarData(db);
        // Two extra enrollments that only this test adds: a WHERE that matches more than the one intended row
        // (for example only student_id = 7, or only the course and an empty grade) also changes one of these.
        try (Statement s = db.createStatement()) {
            s.execute("INSERT INTO enrollments (student_id, course_code, enrolled_at, grade) VALUES "
                    + "(7, 'PGA110', '2026-02-02 14:00', 65), (8, 'SQL201', '2026-03-04 10:00', NULL)");
        }
        List<List<String>> before = table(ENROLLMENTS);
        List<List<String>> students = table(STUDENTS);
        List<List<String>> courses = table(COURSES);

        String code = LabDatabase.codeOnly("changes.sql").toUpperCase(Locale.ROOT);
        if (code.matches("(?s).*\\b(BEGIN|START\\s+TRANSACTION|COMMIT|ROLLBACK)\\b.*") || code.matches("(?s)(.*\\n)?\\s*END\\s*;.*")) {
            fail("Task 2: leave BEGIN, COMMIT and other transaction statements out of changes.sql; the test runs the file "
                    + "as it is and checks the table afterwards. Keep only the UPDATE and the DELETE, each with RETURNING.");
        }
        List<List<List<String>>> returned = new ArrayList<>();
        int counts = 0;
        try (Statement s = db.createStatement()) {
            boolean isResult = s.execute(text);
            while (true) {
                if (isResult) {
                    returned.add(LabDatabase.rows(s.getResultSet()));
                } else if (s.getUpdateCount() == -1) {
                    break;
                } else {
                    counts++;
                }
                isResult = s.getMoreResults();
            }
        } catch (SQLException e) {
            fail("Task 2: changes.sql did not run. PostgreSQL said: " + e.getMessage());
        }

        List<List<String>> expected = new ArrayList<>();
        for (List<String> r : before) {
            if (r.get(0).equals("7") && r.get(1).equals("JDB301")) {
                continue;
            }
            if (r.get(0).equals("2") && r.get(1).equals("SQL201")) {
                expected.add(row(r.get(0), r.get(1), r.get(2), "79"));
            } else {
                expected.add(r);
            }
        }
        List<List<String>> after = table(ENROLLMENTS);
        List<String> problems = new ArrayList<>();
        if (after.size() > expected.size()) {
            problems.add("student 7's JDB301 enrollment should be deleted and nothing else");
        } else if (after.size() < expected.size()) {
            problems.add("only student 7's JDB301 enrollment should be deleted: the DELETE's WHERE matched more rows");
        }
        assertEquals(expected, after, "Task 2: after changes.sql the enrollments are not as asked. "
                + String.join("; ", problems) + (problems.isEmpty() ? "" : ". ")
                + "Only student 2's SQL201 grade should become 79 and only student 7's JDB301 row should go; "
                + "preview each WHERE with a SELECT first. Expected " + expected + " but found " + after + ".");
        assertEquals(students, table(STUDENTS), "Task 2: changes.sql must not change the students table.");
        assertEquals(courses, table(COURSES), "Task 2: changes.sql must not change the courses table.");

        assertEquals(0, counts, "Task 2: end both the UPDATE and the DELETE with RETURNING, so each shows the row it changed; "
                + counts + " statement(s) returned only a row count.");
        assertEquals(2, returned.size(), "Task 2: changes.sql should hold exactly two statements, the UPDATE and then the DELETE, "
                + "each with RETURNING (remove preview SELECTs once you have checked them); it produced " + returned.size() + " result(s).");
        assertEquals(1, returned.get(0).size(), "Task 2: the UPDATE's RETURNING should show exactly the one changed row.");
        assertTrue(returned.get(0).get(0).contains("79"), "Task 2: the first statement should be the UPDATE; its RETURNING row "
                + "should include the new grade 79. It returned " + returned.get(0) + ".");
        assertEquals(1, returned.get(1).size(), "Task 2: the DELETE's RETURNING should show exactly the one deleted row.");
        assertTrue(returned.get(1).get(0).contains("JDB301"), "Task 2: the second statement should be the DELETE; its RETURNING row "
                + "should include JDB301. It returned " + returned.get(1) + ".");
    }

    @Test
    void task3Roster() throws Exception {
        LabDatabase.registrarData(db);
        List<List<String>> actual = report("Task 3", "reports/roster.sql", List.of("course_code", "name", "city", "grade", "result"));
        List<List<String>> expected = List.of(
                row("SQL101", "Ada Lovelace", "London", "92", "pass"),
                row("SQL101", "Grace Hopper", "New York", "75", "pass"),
                row("SQL101", "Alan Turing", "London", "64", "pass"),
                row("SQL101", "Barbara Liskov", "New York", "48", "fail"),
                row("SQL101", "Katherine Johnson", "not given", "NULL", "pending"),
                row("SQL201", "Edsger Dijkstra", "Rotterdam", "95", "pass"),
                row("SQL201", "Ada Lovelace", "London", "88.5", "pass"),
                row("SQL201", "Barbara Liskov", "New York", "70", "pass"),
                row("SQL201", "Grace Hopper", "New York", "NULL", "pending"));
        String hint = null;
        if (actual.stream().anyMatch(r -> r.get(0).equals("JDB301") || r.get(0).equals("PGA110"))) {
            hint = "Keep only the SQL-track courses, whose codes start with SQL: use LIKE with a % wildcard.";
        } else if (actual.stream().anyMatch(r -> r.get(2).equals("NULL"))) {
            hint = "A city is NULL: replace a missing city with 'not given' using COALESCE.";
        } else if (actual.stream().anyMatch(r -> r.get(4).equals("NULL"))) {
            hint = "A result is NULL: give the CASE expression a branch for a missing grade (IS NULL) that says 'pending'.";
        } else if (!actual.isEmpty() && actual.get(0).get(3).equals("NULL")) {
            hint = "An ungraded student comes first: descending order puts NULL first unless you add NULLS LAST.";
        } else if (actual.size() == expected.size() && !actual.get(0).get(0).equals("SQL101")) {
            hint = "Sort by course_code first, then grade, then name.";
        }
        sameRows("Task 3", "reports/roster.sql", expected, actual, hint);
    }

    @Test
    void task4CourseLoad() throws Exception {
        LabDatabase.registrarData(db);
        List<List<String>> actual = report("Task 4", "reports/course_load.sql",
                List.of("code", "title", "enrolled", "graded", "seats_left"));
        List<List<String>> expected = List.of(
                row("JDB301", "JDBC Data Access", "3", "2", "1"),
                row("PGA110", "PostgreSQL Admin Basics", "0", "0", "2"),
                row("SQL101", "SQL Foundations", "5", "4", "1"),
                row("SQL201", "Reporting with SQL", "4", "3", "0"));
        String hint = null;
        if (actual.stream().noneMatch(r -> r.get(0).equals("PGA110"))) {
            hint = "PGA110 is missing: it has no enrollments, so only a LEFT JOIN from courses keeps it.";
        } else if (actual.stream().anyMatch(r -> r.get(0).equals("PGA110") && r.get(2).equals("1"))) {
            hint = "PGA110 shows 1 enrolled: COUNT(*) counts the LEFT JOIN's row of NULLs; count a column from enrollments instead.";
        } else if (actual.stream().anyMatch(r -> r.get(2).equals(r.get(3)) && !r.get(2).equals("0"))) {
            hint = "graded should count only enrollments that have a grade: COUNT(column) skips NULLs.";
        }
        sameRows("Task 4", "reports/course_load.sql", expected, actual, hint);
    }

    @Test
    void task5CityAverages() throws Exception {
        LabDatabase.registrarData(db);
        List<List<String>> actual = report("Task 5", "reports/city_averages.sql",
                List.of("city", "graded", "average_grade", "best_grade"));
        List<List<String>> expected = List.of(
                row("London", "4", "81.375", "92"),
                row("New York", "4", "70.75", "90"));
        String hint = null;
        if (actual.stream().anyMatch(r -> r.get(0).equals("Rotterdam") || r.get(0).equals("not given") || r.get(0).equals("NULL"))) {
            hint = "Keep only cities with at least 2 graded enrollments: filter the groups with HAVING on COUNT(grade).";
        }
        sameRows("Task 5", "reports/city_averages.sql", expected, actual, hint);
    }

    @Test
    void task6PrerequisiteGaps() throws Exception {
        LabDatabase.registrarData(db);
        List<List<String>> actual = report("Task 6", "reports/prerequisite_gaps.sql",
                List.of("name", "course_code", "prerequisite_code", "prerequisite_title"));
        List<List<String>> expected = List.of(
                row("Alan Turing", "JDB301", "SQL201", "Reporting with SQL"),
                row("Donald Knuth", "JDB301", "SQL201", "Reporting with SQL"),
                row("Edsger Dijkstra", "SQL201", "SQL101", "SQL Foundations"));
        String hint = null;
        if (actual.stream().anyMatch(r -> r.get(0).equals("Barbara Liskov") || r.get(0).equals("Ada Lovelace"))) {
            hint = "Barbara and Ada took their prerequisites: keep a row only when NOT EXISTS finds the student's enrollment "
                    + "in the prerequisite course.";
        } else if (actual.stream().anyMatch(r -> r.get(1).equals("SQL101"))) {
            hint = "SQL101 has no prerequisite: join courses to itself on prerequisite_code so only courses with one remain.";
        }
        sameRows("Task 6", "reports/prerequisite_gaps.sql", expected, actual, hint);
    }
}
