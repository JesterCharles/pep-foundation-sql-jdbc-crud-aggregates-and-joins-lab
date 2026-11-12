# Queries lab: run the registrar's term desk

You look after the registrar database from the Relational Design lab: students, courses and enrollments. This term you load it with data, make two corrections, and write the four reports the course coordinator asks for. The tests run every file you write against a fresh database on the PostgreSQL server the tests connect to.

```
OPEN the project -> EDIT a TODO -> RUN the tests -> READ the feedback -> STOP when all pass
                        ^                                  |
                        +------------ next task -----------+
```

In words: open the lab project, edit the TODO for one task, run the tests, read what the failing test tells you, and repeat with the next task. Stop when every test passes.

**Time:** about 35 minutes, at most 40. If you pass 40 minutes, note the next failing task and finish it later.

## Quick start

1. **Open** the lab project in VS Code, then open its terminal with **Terminal > New Terminal**. Go into the Maven project folder:

   ```
   cd starter
   ```

   Run every command in this README from this `starter` folder.
2. **Run** the tests:

   ```
   mvn -q -o test
   ```

   **All 6 tests fail at first. That is expected:** each failure is a task you have not done yet.

   If Maven instead says it cannot resolve a dependency, run `mvn -q dependency:go-offline` once (it needs the internet), then run the tests again.
3. **Edit** the file for one task in [Your tasks](#your-tasks).
4. **Read** the feedback: each failing test starts with its task number and names the next thing to fix. Run that task's test command again after each change.
5. **Stop** when the full run `mvn -q -o test` prints no `[ERROR]` lines.

The tests need a PostgreSQL server they can reach, JDK 25 and Maven 3.9 or later ([Prerequisites](#prerequisites)). If a command fails, see [Troubleshooting](#troubleshooting).

## Your tasks

You edit only the files below, all in `starter/`. Each one has a `TODO` line that marks where your work goes. After each task, run its test; it is done when the run prints no `[ERROR]` lines. You can do Tasks 2 to 6 in any order.

- [ ] **Practice database** (about 3 minutes, not tested): make it now, so you can look at the data and preview Task 2. See [Practice database](#practice-database).
- [ ] **1. Seed the database:** `seed.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task1Seed'`
- [ ] **2. Make two corrections:** `changes.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task2Changes'`
- [ ] **3. SQL-track roster:** `reports/roster.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task3Roster'`
- [ ] **4. Course load:** `reports/course_load.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task4CourseLoad'`
- [ ] **5. Averages by city:** `reports/city_averages.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task5CityAverages'`
- [ ] **6. Prerequisite gaps:** `reports/prerequisite_gaps.sql`
  `mvn -q -o test -Dtest='QueriesLabTest#task6PrerequisiteGaps'`

**Finish:** run all the tests with `mvn -q -o test`. You are done when it prints no `[ERROR]` lines and `target/surefire-reports/registrar.QueriesLabTest.txt` contains:

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

`WARNING:` lines from Java or Maven, such as `WARNING: A restricted method ...`, are not test failures.

## The data

`../fixtures/schema.sql` creates the tables: your Relational Design schema plus `students.city` (may be empty) and `courses.prerequisite_code` (the course to take first, or empty). `../fixtures/students.csv` holds 8 students and `../fixtures/enrollments.csv` holds 12 enrollments; an empty field means NULL.

The four courses (Task 1 inserts them):

| code | title | capacity | prerequisite_code |
| --- | --- | --- | --- |
| `SQL101` | SQL Foundations | 6 | none (NULL) |
| `PGA110` | PostgreSQL Admin Basics | 2 | none (NULL) |
| `SQL201` | Reporting with SQL | 4 | `SQL101` |
| `JDB301` | JDBC Data Access | 4 | `SQL201` |

Every test starts from fresh tables. Task 1 runs your `seed.sql`; Tasks 2 to 6 load the same data themselves.

## Practice database

Make your own database to look at the data and preview Task 2's changes. The tests never use or check it. From `starter`:

```
psql -h localhost -d postgres -c "CREATE DATABASE queries_scratch;"
psql -h localhost -d queries_scratch -f ../fixtures/schema.sql
psql -h localhost -d queries_scratch
```

At the `queries_scratch=>` prompt, run `\l` (your new database is in the server's list), `\dt` (its three tables), `\d enrollments` (its columns, keys and indexes), then `\q` to leave.

After Task 1, load your seed into it once with `psql -h localhost -d queries_scratch -f seed.sql`. To load it again, drop the database and make it again. When you finish the lab, drop it:

```
psql -h localhost -d postgres -c "DROP DATABASE queries_scratch;"
```

## Task details

1. **Seed the database (`seed.sql`).**
   1. Insert the four courses above with one `INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES ...`, naming the columns. A course must exist before a course that names it as a prerequisite, and before any enrollment in it.
   2. The students' line is supplied: `\copy students (id, name, email, city) FROM '../fixtures/students.csv' WITH (FORMAT csv, HEADER)`.
   3. Add five guest students with `INSERT ... SELECT` over `generate_series(901, 905)`: ids 901 to 905, names `Guest 901` to `Guest 905`, emails `guest901@registrar.test` to `guest905@registrar.test`, and no city.
   4. Load the enrollments from `../fixtures/enrollments.csv` (columns `student_id, course_code, enrolled_at, grade`) with a second `\copy`, after the students and courses they refer to.

   The test also checks that `seed.sql` uses `\copy` and `generate_series` on lines that are not comments.

   <details><summary>Hint</summary>

   `\copy` makes `psql` read the file on your machine and send the rows to the server. Use the supplied students' line as the pattern for step 4.

   </details>

2. **Make two corrections (`changes.sql`).** First preview each `WHERE` with a `SELECT` in your practice database, then keep only the two changes in the file:
   1. Student 2's SQL201 grade has arrived: set it to 79 with an `UPDATE` whose `WHERE` matches only that enrollment.
   2. Student 7 withdraws from JDB301: remove that one enrollment with a `DELETE`.
   3. End both statements with `RETURNING`, so each shows the row it changed.

   The test compares the whole table afterwards, so a `WHERE` that matches extra rows fails.
3. **SQL-track roster (`reports/roster.sql`).** One row per enrollment in a course whose code starts with `SQL`, joined to the student. Columns `course_code`, `name`, `city`, `grade`, `result`:
   - `city` shows `not given` when it is NULL.
   - `result` is a `CASE`: `pending` when the grade is NULL, `pass` when it is 50 or more, otherwise `fail`.
   - Sort by `course_code`, then grade from highest to lowest with ungraded students last, then `name`.

   <details><summary>Hint</summary>

   Use `LIKE` for the course-code prefix, `COALESCE` for the missing city, and `NULLS LAST` to put ungraded students last.

   </details>

4. **Course load (`reports/course_load.sql`).** The draft in the file fails with a GROUP BY error. Fix it, then finish it:
   - Columns `code`, `title`, `enrolled` (enrollments in the course), `graded` (enrollments that have a grade), `seats_left` (`capacity` minus `enrolled`).
   - One row per course, all four courses, sorted by `code`. `PGA110` has no enrollments yet and must show 0 enrolled.

   <details><summary>Hint</summary>

   To fix the GROUP BY error, make every selected column grouped or aggregated.

   </details>

5. **Averages by city (`reports/city_averages.sql`).** The file supplies the first column, `COALESCE(s.city, 'not given') AS city`, and the `FROM` and `JOIN` lines. You add:
   - the columns `graded` (enrollments with a grade), `average_grade` (`AVG` of grade, unrounded) and `best_grade` (`MAX` of grade);
   - a `GROUP BY` on the same `COALESCE` expression;
   - a filter that keeps only cities with at least 2 graded enrollments;
   - sorting by `average_grade` from highest to lowest.

   <details><summary>Hint</summary>

   The filter is a condition on an aggregate, so it belongs in `HAVING`, not `WHERE`.

   </details>

6. **Prerequisite gaps (`reports/prerequisite_gaps.sql`).** List each enrollment whose course has a prerequisite that the same student has no enrollment for, with the prerequisite's title. Columns `name`, `course_code`, `prerequisite_code`, `prerequisite_title`, sorted by `name`, then `course_code`.

   <details><summary>Hint</summary>

   Join `courses` to itself (for example `courses AS c` and `courses AS p`) to show the prerequisite's title, and use `NOT EXISTS` to keep only the missing ones.

   </details>

**Starting over (optional):** the shipped version of every file is in the lab's original repository, in the same folder. Copy your attempt somewhere else first if you want to keep it, then paste the shipped text back into the file.

## What the tests do

Each run creates its own database with a unique name such as `queries_lab_3f9c0a1b7d2e`, runs the tests in it, and drops it. It never touches a database it did not create. While a run is in progress, the database's name is in `starter/target/lab-database.txt`. After setup, the tests run offline and talk only to that PostgreSQL server.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| PostgreSQL server and `psql` | 18 (16 or later works) | the connection check below |
| JDK | 25 | `java -version` |
| Apache Maven | 3.9 or later | `mvn -v` |

### Connection check

```
psql -h localhost -p 5432 -d postgres -c "SELECT version();"
```

It should print one row starting `PostgreSQL 18` (or 16, 17) without asking for a password.

## Setup

The quick start is all the setup most learners need. Only if the connection check needs other values: the tests read the same environment variables as `psql`. Set the ones you need in the terminal where you run the tests; they last until you close it.

| Variable | Meaning | Default |
| --- | --- | --- |
| `PGHOST` | server host name | `localhost` |
| `PGPORT` | server port | `5432` |
| `PGUSER` | a PostgreSQL user allowed to create databases | your login name |
| `PGDATABASE` | an existing database to connect to first | `postgres` |

For example `export PGUSER=postgres` (macOS or Linux) or `$env:PGUSER = "postgres"` (Windows PowerShell).

**If your server asks for a password**, put one line, `localhost:5432:*:postgres:your-password`, in `~/.pgpass` and run `chmod 600 ~/.pgpass` (macOS or Linux), or in `%APPDATA%\postgresql\pgpass.conf` (Windows). Never write a password into a lab file.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| `cd: no such file or directory: starter` (or `Cannot find path`) | The terminal is not in the lab's top folder, the one that holds this `README.md` and the `starter` folder. Change to that folder, then run `cd starter`. |
| `Could not reach PostgreSQL to create the lab database` | Run the [connection check](#connection-check) until it prints the version. |
| `password authentication failed` or `no password was provided` | Set up a [password file](#setup), or set `PGUSER`. |
| `permission denied to create database` | Use a PostgreSQL user that may create databases, such as `postgres`. |
| `Could not start psql` | `psql --version` must work in this terminal. On Windows, add PostgreSQL's `bin` folder to `PATH`, or set `PSQL` to the full path of `psql.exe`. |
| `psql stopped on an error in seed.sql` with a foreign key | Insert the parent rows (courses, then students) before the rows that refer to them. |
| `must appear in the GROUP BY clause` | Every selected column must be in `GROUP BY` or inside an aggregate. |
| `NOT IN` returns no rows | A NULL in the subquery makes `NOT IN` unknown for every row; use `NOT EXISTS`. |
| Maven cannot resolve a dependency offline | Run `mvn -q dependency:go-offline` once with the internet on. |
| A run was interrupted | Drop the database named in `starter/target/lab-database.txt`, for example `psql -h localhost -d postgres -c "DROP DATABASE queries_lab_3f9c0a1b7d2e;"` with the name from the file. |

## Tested with

PostgreSQL 18.3, psql 18.3, JDK 25 and Maven 3.9.9 on macOS (arm64), with no-password local connections. Linux and Windows runs have not been recorded yet; the steps follow PostgreSQL's documented behaviour there.
