"""Trainer-authored visible tests — restored over the learner checkout at grade time.

Every test starts from fresh tables in its own new database. Task 1 runs your seed.sql;
Tasks 2 to 6 load the same registrar data themselves, so each task can pass on its own.
"""

import psycopg
import pytest

from test.support import (COURSES, ENROLLMENTS, STUDENTS, check_changed_enrollments, empty_tables, import_tables,
                          learner_sql, registrar_data, report, run_changes, same_rows, table)


def test_task1_seed(db):
    sql = learner_sql("Task 1", "seed.sql")
    empty_tables(db)
    import_tables(db)
    problem = None
    try:
        db.execute(sql)
    except psycopg.Error as error:
        problem = str(error).strip()
    if problem is not None:
        pytest.fail("Task 1: seed.sql stopped on an error, so none of its rows were kept. PostgreSQL said: " + problem
                    + " -- if it names a foreign key, insert the parent rows (courses, students) before the rows that refer to them.",
                    pytrace=False)
    courses, students, enrollments = table(db, COURSES), table(db, STUDENTS), table(db, ENROLLMENTS)

    registrar_data(db)
    want_courses, want_students, want_enrollments = table(db, COURSES), table(db, STUDENTS), table(db, ENROLLMENTS)

    assert courses == want_courses, ("Task 1 (courses): after seed.sql, courses should hold exactly the four courses in the "
                                     f"lab README, Task 1. Expected {want_courses} but found {courses}.")
    csv_students = [r for r in students if int(r[0]) < 900]
    want_csv_students = [r for r in want_students if int(r[0]) < 900]
    assert csv_students == want_csv_students, (
        "Task 1 (students from CSV): students 1-8 should match students.csv, with an empty city loaded as NULL. "
        f"Expected {want_csv_students} but found {csv_students}.")
    guests = [r for r in students if int(r[0]) >= 900]
    want_guests = [r for r in want_students if int(r[0]) >= 900]
    assert guests == want_guests, (
        "Task 1 (guest students): generate_series should add exactly students 901-905 named 'Guest 901' ... "
        f"with emails guest901@registrar.test ... and no city. Expected {want_guests} but found {guests}.")
    assert enrollments == want_enrollments, (
        "Task 1 (enrollments): enrollments should match enrollments.csv, with an empty grade loaded as NULL. "
        f"Expected {want_enrollments} but found {enrollments}.")


def test_task2_changes(db):
    registrar_data(db)
    before = table(db, ENROLLMENTS)
    students = table(db, STUDENTS)
    courses = table(db, COURSES)

    results = run_changes(db)

    check_changed_enrollments(db, before)
    assert table(db, STUDENTS) == students, "Task 2: changes.sql must not change the students table."
    assert table(db, COURSES) == courses, "Task 2: changes.sql must not change the courses table."

    counts = sum(1 for result in results if result is None)
    returned = [result for result in results if result is not None]
    assert counts == 0, ("Task 2: end both the UPDATE and the DELETE with RETURNING, so each shows the row it changed; "
                         f"{counts} statement(s) returned only a row count.")
    assert len(returned) == 2, ("Task 2: changes.sql should hold exactly two statements, the UPDATE and then the DELETE, "
                                "each with RETURNING (remove preview SELECTs once you have checked them); "
                                f"it produced {len(returned)} result(s).")
    assert len(returned[0]) == 1, "Task 2: the UPDATE's RETURNING should show exactly the one changed row."
    assert "79" in returned[0][0], ("Task 2: the first statement should be the UPDATE; its RETURNING row should include "
                                    f"the new grade 79. It returned {returned[0]}.")
    assert len(returned[1]) == 1, "Task 2: the DELETE's RETURNING should show exactly the one deleted row."
    assert "JDB301" in returned[1][0], ("Task 2: the second statement should be the DELETE; its RETURNING row should "
                                        f"include JDB301. It returned {returned[1]}.")


def test_task3_roster(db):
    registrar_data(db)
    actual = report(db, "Task 3", "roster.sql", ["course_code", "name", "city", "grade", "result"])
    expected = [
        ["SQL101", "Ada Lovelace", "London", "92", "pass"],
        ["SQL101", "Grace Hopper", "New York", "75", "pass"],
        ["SQL101", "Alan Turing", "London", "64", "pass"],
        ["SQL101", "Barbara Liskov", "New York", "48", "fail"],
        ["SQL101", "Katherine Johnson", "not given", "NULL", "pending"],
        ["SQL201", "Edsger Dijkstra", "Rotterdam", "95", "pass"],
        ["SQL201", "Ada Lovelace", "London", "88.5", "pass"],
        ["SQL201", "Barbara Liskov", "New York", "70", "pass"],
        ["SQL201", "Grace Hopper", "New York", "NULL", "pending"],
    ]
    hint = None
    if any(r[0] in ("JDB301", "PGA110") for r in actual):
        hint = "Keep only the SQL-track courses, whose codes start with SQL: use LIKE with a % wildcard."
    elif any(r[2] == "NULL" for r in actual):
        hint = "A city is NULL: replace a missing city with 'not given' using COALESCE."
    elif any(r[4] == "NULL" for r in actual):
        hint = "A result is NULL: give the CASE expression a branch for a missing grade (IS NULL) that says 'pending'."
    elif actual and actual[0][3] == "NULL":
        hint = "An ungraded student comes first: descending order puts NULL first unless you add NULLS LAST."
    elif len(actual) == len(expected) and actual[0][0] != "SQL101":
        hint = "Sort by course_code first, then grade, then name."
    same_rows("Task 3", "roster.sql", expected, actual, hint)


def test_task4_course_load(db):
    registrar_data(db)
    actual = report(db, "Task 4", "course_load.sql", ["code", "title", "enrolled", "graded", "seats_left"])
    expected = [
        ["JDB301", "JDBC Data Access", "3", "2", "1"],
        ["PGA110", "PostgreSQL Admin Basics", "0", "0", "2"],
        ["SQL101", "SQL Foundations", "5", "4", "1"],
        ["SQL201", "Reporting with SQL", "4", "3", "0"],
    ]
    hint = None
    if not any(r[0] == "PGA110" for r in actual):
        hint = "PGA110 is missing: it has no enrollments, so only a LEFT JOIN from courses keeps it."
    elif any(r[0] == "PGA110" and r[2] == "1" for r in actual):
        hint = "PGA110 shows 1 enrolled: COUNT(*) counts the LEFT JOIN's row of NULLs; count a column from enrollments instead."
    elif any(r[2] == r[3] and r[2] != "0" for r in actual):
        hint = "graded should count only enrollments that have a grade: COUNT(column) skips NULLs."
    same_rows("Task 4", "course_load.sql", expected, actual, hint)


def test_task5_city_averages(db):
    registrar_data(db)
    actual = report(db, "Task 5", "city_averages.sql", ["city", "graded", "average_grade", "best_grade"])
    expected = [
        ["London", "4", "81.375", "92"],
        ["New York", "4", "70.75", "90"],
    ]
    hint = None
    if any(r[0] in ("Rotterdam", "not given", "NULL") for r in actual):
        hint = "Keep only cities with at least 2 graded enrollments: filter the groups with HAVING on COUNT(grade)."
    same_rows("Task 5", "city_averages.sql", expected, actual, hint)


def test_task6_prerequisite_gaps(db):
    registrar_data(db)
    actual = report(db, "Task 6", "prerequisite_gaps.sql", ["name", "course_code", "prerequisite_code", "prerequisite_title"])
    expected = [
        ["Alan Turing", "JDB301", "SQL201", "Reporting with SQL"],
        ["Donald Knuth", "JDB301", "SQL201", "Reporting with SQL"],
        ["Edsger Dijkstra", "SQL201", "SQL101", "SQL Foundations"],
    ]
    hint = None
    if any(r[0] in ("Barbara Liskov", "Ada Lovelace") for r in actual):
        hint = ("Barbara and Ada took their prerequisites: keep a row only when NOT EXISTS finds the student's enrollment "
                "in the prerequisite course.")
    elif any(r[1] == "SQL101" for r in actual):
        hint = "SQL101 has no prerequisite: join courses to itself on prerequisite_code so only courses with one remain."
    same_rows("Task 6", "prerequisite_gaps.sql", expected, actual, hint)
