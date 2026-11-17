# Queries lab: run the registrar's term desk

You look after a registrar database: students, courses and enrollments.
This term you load it with data, make two corrections, and write the four reports the course coordinator asks for.
The tests run every file you write against a fresh database on the PostgreSQL server the tests connect to.

You practise INSERT, `\copy`, UPDATE and DELETE with RETURNING, joins, CASE, GROUP BY with HAVING, and NOT EXISTS.

**Time:** about 35 minutes, at most 40. If you pass 40 minutes, note the next failing task and finish it later.

## Before you start: PostgreSQL and psql

The tests need a PostgreSQL server they can reach (16 or later), and Task 1's test runs your `seed.sql` with the real `psql` client. See [Prerequisites](#prerequisites) for the connection check, and [Setup](#setup) if your server needs other connection settings or a password.
If no server is reachable, every test fails with `Could not reach PostgreSQL to create the lab database`. That is a missing prerequisite, not a mistake in your SQL.

## Quick start

1. Open the lab project. The files you edit are in `src/main/sql/`; the checklist below names them.
2. Select **Run Tests** before you change anything.

   **All 6 tests fail at first. That is expected: each failure is a task you have not done yet.**
   Every failure message starts with its task number, such as `Task 1:`.
3. Work through the checklist below. After each task, select **Run Tests** again and read the remaining `Task N:` messages.
4. **You are finished when all 6 tests pass.** Then select **Submit**.

```
OPEN project -> RUN TESTS: 6 fail (expected)
  -> READ the "Task N:" messages -> EDIT the next TODO -> RUN TESTS again
  -> repeat for Tasks 1-6 -> SUBMIT when all 6 tests pass
```

In words: open the project, run the tests, read which tasks fail, complete one task's TODO, run the tests again, and submit when they all pass.

## Checklist

You edit only the files below, all in `src/main/sql/`. Each one has a `TODO` line that marks where your work goes. You can do Tasks 2 to 6 in any order.

- [ ] Practice database (about 3 minutes, not tested): make it now, so you can look at the data and preview Task 2. See [Practice database](#practice-database).
- [ ] Task 1: seed the database, `seed.sql` (test `task1Seed`)
- [ ] Task 2: make two corrections, `changes.sql` (test `task2Changes`)
- [ ] Task 3: SQL-track roster, `reports/roster.sql` (test `task3Roster`)
- [ ] Task 4: course load, `reports/course_load.sql` (test `task4CourseLoad`)
- [ ] Task 5: averages by city, `reports/city_averages.sql` (test `task5CityAverages`)
- [ ] Task 6: prerequisite gaps, `reports/prerequisite_gaps.sql` (test `task6PrerequisiteGaps`)

A task is done when no failure message starts with its number.
The tests in `src/test/java/registrar/` are restored to their original state before your work is graded, so changes to them do not count. Leave the build file `pom.xml`, the `fixtures/` folder and `expected/test-summary.txt` as they are.

From a terminal in the project folder, you can run one task's test on its own, for example `mvn -q test -Dtest='QueriesLabTest#task1Seed'`, or all of them with `mvn -q test`.
`WARNING:` lines from Java or Maven, such as `WARNING: A restricted method ...`, are not test failures.

## The data

`fixtures/schema.sql` creates the tables `students`, `courses` and `enrollments`, including `students.city` (may be empty) and `courses.prerequisite_code` (the course to take first, or empty).
`fixtures/students.csv` holds 8 students and `fixtures/enrollments.csv` holds 12 enrollments; an empty field means NULL.

The four courses (Task 1 inserts them):

| code | title | capacity | prerequisite_code |
| --- | --- | --- | --- |
| `SQL101` | SQL Foundations | 6 | none (NULL) |
| `PGA110` | PostgreSQL Admin Basics | 2 | none (NULL) |
| `SQL201` | Reporting with SQL | 4 | `SQL101` |
| `JDB301` | JDBC Data Access | 4 | `SQL201` |

Every test starts from fresh tables. Task 1 runs your `seed.sql`; Tasks 2 to 6 load the same data themselves.

## Practice database

Make your own database to look at the data and preview Task 2's changes. The tests never use or check it. In a terminal, from the project folder:

```
psql -h localhost -d postgres -c "CREATE DATABASE queries_scratch;"
psql -h localhost -d queries_scratch -f fixtures/schema.sql
psql -h localhost -d queries_scratch
```

At the `queries_scratch=>` prompt, run `\l` (your new database is in the server's list), `\dt` (its three tables), `\d enrollments` (its columns, keys and indexes), then `\q` to leave.

After Task 1, load your seed into it once with `psql -h localhost -d queries_scratch -f src/main/sql/seed.sql`, still from the project folder, because the `\copy` paths are relative to it. To load it again, drop the database and make it again. When you finish the lab, drop it:

```
psql -h localhost -d postgres -c "DROP DATABASE queries_scratch;"
```

## Task steps

### Task 1: seed the database (`seed.sql`)

1. Insert the four courses above with one `INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES ...`, naming the columns. A course must exist before a course that names it as a prerequisite, and before any enrollment in it.
2. The students' line is supplied: `\copy students (id, name, email, city) FROM 'fixtures/students.csv' WITH (FORMAT csv, HEADER)`.
3. Add five guest students with `INSERT ... SELECT` over `generate_series(901, 905)`: ids 901 to 905, names `Guest 901` to `Guest 905`, emails `guest901@registrar.test` to `guest905@registrar.test`, and no city.
4. Load the enrollments from `fixtures/enrollments.csv` (columns `student_id, course_code, enrolled_at, grade`) with a second `\copy`, after the students and courses they refer to.

The tests run `seed.sql` with `psql` from the project folder, so the `\copy` paths start at `fixtures/`.
The test also checks that `seed.sql` uses `\copy` and `generate_series` on lines that are not comments.

<details><summary>Hint</summary>

`\copy` makes `psql` read the file on your machine and send the rows to the server. Use the supplied students' line as the pattern for step 4.

</details>

### Task 2: make two corrections (`changes.sql`)

First preview each `WHERE` with a `SELECT` in your practice database, then keep only the two changes in the file:
1. Student 2's SQL201 grade has arrived: set it to 79 with an `UPDATE` whose `WHERE` matches only that enrollment.
2. Student 7 withdraws from JDB301: remove that one enrollment with a `DELETE`.
3. End both statements with `RETURNING`, so each shows the row it changed.

The test compares the whole table afterwards, so a `WHERE` that matches extra rows fails.

### Task 3: SQL-track roster (`reports/roster.sql`)

One row per enrollment in a course whose code starts with `SQL`, joined to the student. Columns `course_code`, `name`, `city`, `grade`, `result`:
- `city` shows `not given` when it is NULL.
- `result` is a `CASE`: `pending` when the grade is NULL, `pass` when it is 50 or more, otherwise `fail`.
- Sort by `course_code`, then grade from highest to lowest with ungraded students last, then `name`.

<details><summary>Hint</summary>

Use `LIKE` for the course-code prefix, `COALESCE` for the missing city, and `NULLS LAST` to put ungraded students last.

</details>

### Task 4: course load (`reports/course_load.sql`)

The draft in the file fails with a GROUP BY error. Fix it, then finish it:
- Columns `code`, `title`, `enrolled` (enrollments in the course), `graded` (enrollments that have a grade), `seats_left` (`capacity` minus `enrolled`).
- One row per course, all four courses, sorted by `code`. `PGA110` has no enrollments yet and must show 0 enrolled.

<details><summary>Hint</summary>

To fix the GROUP BY error, make every selected column grouped or aggregated.

</details>

### Task 5: averages by city (`reports/city_averages.sql`)

The file supplies the first column, `COALESCE(s.city, 'not given') AS city`, and the `FROM` and `JOIN` lines. You add:
- the columns `graded` (enrollments with a grade), `average_grade` (`AVG` of grade, unrounded) and `best_grade` (`MAX` of grade);
- a `GROUP BY` on the same `COALESCE` expression;
- a filter that keeps only cities with at least 2 graded enrollments;
- sorting by `average_grade` from highest to lowest.

<details><summary>Hint</summary>

The filter is a condition on an aggregate, so it belongs in `HAVING`, not `WHERE`.

</details>

### Task 6: prerequisite gaps (`reports/prerequisite_gaps.sql`)

List each enrollment whose course has a prerequisite that the same student has no enrollment for, with the prerequisite's title. Columns `name`, `course_code`, `prerequisite_code`, `prerequisite_title`, sorted by `name`, then `course_code`.

<details><summary>Hint</summary>

Join `courses` to itself (for example `courses AS c` and `courses AS p`) to show the prerequisite's title, and use `NOT EXISTS` to keep only the missing ones.

</details>

**Starting over (optional):** before you change a file, keep a copy of it outside the project folder. To restart that file, paste the copy back.

## What the tests do

Each run creates its own database with a unique name such as `queries_lab_3f9c0a1b7d2e`, runs the tests in it, and drops it.
It never touches a database it did not create. While a run is in progress, the database's name is in `target/lab-database.txt`.
The tests talk only to that PostgreSQL server.

## Using an AI assistant

If you use an AI assistant, ask it to explain, not to answer.
"Explain why this column must appear in GROUP BY" teaches you SQL; "write my report query" does not.
Check any explanation with a test run before you trust it.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| PostgreSQL server and `psql` | 16 or later | the connection check below |
| JDK (only for terminal runs) | 17 or later | `java -version` |
| Apache Maven (only for terminal runs) | 3.9 or later | `mvn -v` |

### Connection check

```
psql -h localhost -p 5432 -d postgres -c "SELECT version();"
```

It should print one row starting `PostgreSQL 16` (or later) without asking for a password.

## Setup

Only if the connection check needs other values: the tests read the same environment variables as `psql`.

| Variable | Meaning | Default |
| --- | --- | --- |
| `PGHOST` | server host name | `localhost` |
| `PGPORT` | server port | `5432` |
| `PGUSER` | a PostgreSQL user allowed to create databases | your login name |
| `PGDATABASE` | an existing database to connect to first | `postgres` |
| `PSQL` | full path of `psql`, if it is not on `PATH` | `psql` |

For a terminal run, set the ones you need in that terminal, for example `export PGUSER=postgres` (macOS or Linux) or `$env:PGUSER = "postgres"` (Windows PowerShell).

**If your server asks for a password**, put one line, `localhost:5432:*:postgres:your-password`, in `~/.pgpass` and run `chmod 600 ~/.pgpass` (macOS or Linux), or in `%APPDATA%\postgresql\pgpass.conf` (Windows). Never write a password into a lab file.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| `Could not reach PostgreSQL to create the lab database` | Run the [connection check](#connection-check) until it prints the version. |
| `password authentication failed` or `no password was provided` | Set up a [password file](#setup), or set `PGUSER`. |
| `permission denied to create database` | Use a PostgreSQL user that may create databases, such as `postgres`. |
| `Could not start psql` | `psql --version` must work in this terminal. On Windows, add PostgreSQL's `bin` folder to `PATH`, or set `PSQL` to the full path of `psql.exe`. |
| `psql stopped on an error in seed.sql` with a foreign key | Insert the parent rows (courses, then students) before the rows that refer to them. |
| `psql stopped on an error in seed.sql` with `No such file or directory` | A `\copy` path is wrong. Paths start at the project folder: `fixtures/students.csv`. |
| `must appear in the GROUP BY clause` | Every selected column must be in `GROUP BY` or inside an aggregate. |
| `NOT IN` returns no rows | A NULL in the subquery makes `NOT IN` unknown for every row; use `NOT EXISTS`. |
| A run was interrupted | Drop the database named in `target/lab-database.txt`, for example `psql -h localhost -d postgres -c "DROP DATABASE queries_lab_3f9c0a1b7d2e;"` with the name from the file. |
