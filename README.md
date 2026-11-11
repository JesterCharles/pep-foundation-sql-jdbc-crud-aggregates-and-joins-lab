# Queries lab: run the registrar's term desk

You look after the registrar database from the Relational Design lab: students, courses and enrollments. This term you load it with data, make two corrections, and write the four reports the course coordinator asks for. Every file you write runs for real against a database on your own PostgreSQL server: the seed script through the `psql` client, the other files through the PostgreSQL JDBC driver.

This lab practises all eight CRUD, Aggregates and Joins topics. It needs no cloud account, container or shared server: only your local PostgreSQL server with `psql`, a JDK and Maven.

**Time.** At most 40 minutes of your scheduled exercise block, a guided lab with named files and supplied starting points. The estimate is about 35 minutes: about 5 minutes of setup, about 3 minutes for your practice database (Setup step 4), then about 27 minutes for the six tasks (about 4, 4, 5, 5, 3 and 6 minutes), which leaves about 5 minutes of buffer. Two parts are supplied so the lab fits: the students' `\copy` line in Task 1 and the `FROM`, `JOIN` and city lines of Task 5. If you pass 40 minutes, pause and finish in self-study.

## What you edit

| File (in `starter/`) | What you do | Why the registrar needs it |
| --- | --- | --- |
| `seed.sql` | Task 1: insert the courses, generate guest students, `\copy` the enrollments (the students' `\copy` is supplied) | an empty database is no use to anyone |
| `changes.sql` | Task 2: one `UPDATE` and one `DELETE`, each with `RETURNING` | a late grade and a withdrawal |
| `reports/roster.sql` | Task 3: one `SELECT` | the SQL-track roster |
| `reports/course_load.sql` | Task 4: fix and finish the draft `SELECT` | seats left in every course |
| `reports/city_averages.sql` | Task 5: finish one `SELECT` | grade averages by city |
| `reports/prerequisite_gaps.sql` | Task 6: one `SELECT` | students missing a prerequisite |

Leave `tests/`, `fixtures/`, `expected/`, `starter/pom.xml` and `starter/.gitignore` alone: the tests and your copy's version control read them. The single test command, run in `starter/`, is:

```
mvn -q -o test
```

Each failing check starts with its task number and names the next thing to fix.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| PostgreSQL server and `psql` | 18 (16 or later works) | the connection check below |
| JDK | 25 | `java -version` |
| Apache Maven | 3.9 or later | `mvn -v` |

**Connection check.** Run this in a new terminal. It must print one row starting `PostgreSQL 18` (or 16, 17) without asking for a password:

```
psql -h localhost -p 5432 -d postgres -c "SELECT version();"
```

## Setup

1. Open the lab. This lab is a self-contained project: the folder that holds this `README.md` is its root, and in your own copy or clone it is the repository root. Open that root folder in VS Code or any other editor; no extension is needed. Run every command in a terminal whose working directory is `starter/`, the Maven project folder inside the root.
2. Only if the connection check needed other settings: set the same values for the tests in this terminal. They read the variables `psql` reads: `PGHOST` (a host name such as `localhost`, default `localhost`), `PGPORT` (default `5432`), `PGUSER` (a user allowed to create databases, default your login name) and `PGDATABASE` (an existing database, default `postgres`). For example `export PGUSER=postgres` (macOS or Linux) or `$env:PGUSER = "postgres"` (Windows PowerShell). If your server asks for a password, put it in a password file as in the Relational Design lab: one line `localhost:5432:*:postgres:your-password` in `~/.pgpass` (then `chmod 600 ~/.pgpass`) on macOS or Linux, or in `%APPDATA%\postgresql\pgpass.conf` on Windows. Never write a password into a lab file.
3. Download the test libraries once (needs the internet; if you did the Relational Design lab, Maven already has most of them):

   ```
   mvn -q dependency:go-offline
   ```

   After this step the test command works offline.
4. Make your practice database, where you look at the data and preview Task 2's changes (required, about 3 minutes; the tests never use or check it). From `starter/`:

   ```
   psql -h localhost -d postgres -c "CREATE DATABASE queries_scratch;"
   psql -h localhost -d queries_scratch -f ../fixtures/schema.sql
   psql -h localhost -d queries_scratch
   ```

   At the `queries_scratch=>` prompt, run `\l` (your new database is in the server's list), `\dt` (its three tables), `\d enrollments` (its columns, keys and indexes), then `\q` to leave. After Task 1, load your seed into it once with `psql -h localhost -d queries_scratch -f seed.sql`; to load it again, drop and remake the database (see Cleanup).

## The data

`fixtures/schema.sql` creates the tables. It is your Relational Design schema plus two columns: `students.city` (may be empty) and `courses.prerequisite_code` (the course to take first, or empty). `fixtures/students.csv` holds 8 students and `fixtures/enrollments.csv` holds 12 enrollments; in both files an empty field means NULL.

The four courses (Task 1 inserts them):

| code | title | capacity | prerequisite_code |
| --- | --- | --- | --- |
| `SQL101` | SQL Foundations | 6 | none (NULL) |
| `PGA110` | PostgreSQL Admin Basics | 2 | none (NULL) |
| `SQL201` | Reporting with SQL | 4 | `SQL101` |
| `JDB301` | JDBC Data Access | 4 | `SQL201` |

## Tasks

Every test starts from fresh tables. Task 1 runs your `seed.sql`; Tasks 2 to 6 load the same data themselves, so you can do the tasks in any order. Run the test command after each change.

1. **Seed the database (`seed.sql`).**
   1. Insert the four courses above with one `INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES ...`, naming the columns. A course must exist before a course that names it as a prerequisite, and before any enrollment in it.
   2. The file already loads the students with `\copy students (id, name, email, city) FROM '../fixtures/students.csv' WITH (FORMAT csv, HEADER)`; students refer to no other table, so this line can come first. The path is relative to `starter/`, where the tests run `psql`. Unlike server-side `COPY`, `\copy` makes `psql` read the file on your machine and send the rows to the server. Use it as the pattern for step 4.
   3. Add five guest students with `INSERT ... SELECT` over `generate_series(901, 905)`: ids 901 to 905, names `Guest 901` to `Guest 905`, emails `guest901@registrar.test` to `guest905@registrar.test`, and no city.
   4. Load the enrollments from `../fixtures/enrollments.csv` (columns `student_id, course_code, enrolled_at, grade`) with a second `\copy`, after the students and courses they refer to.
   5. Done: `task1Seed` passes. If `psql` stops on a foreign key error, a child row came before its parent.
2. **Make two corrections (`changes.sql`).** First preview each `WHERE` with a `SELECT` in your practice database (Setup step 4), then keep only the two changes in the file:
   1. Student 2's SQL201 grade has arrived: set it to 79 with an `UPDATE` whose `WHERE` matches only that enrollment.
   2. Student 7 withdraws from JDB301: remove that one enrollment with a `DELETE`.
   3. End both statements with `RETURNING` so each shows the row it changed.
   4. Done: `task2Changes` passes. The test compares the whole table afterwards, so a `WHERE` that matches extra rows fails.
3. **SQL-track roster (`reports/roster.sql`).** One row per enrollment in a course whose code starts with `SQL` (use `LIKE`), joined to the student. Columns `course_code`, `name`, `city`, `grade`, `result`: `city` shows `not given` when it is NULL (`COALESCE`); `result` is a `CASE`: `pending` when the grade is NULL, `pass` when it is 50 or more, otherwise `fail`. Sort by `course_code`, then grade from highest to lowest with ungraded students last (`NULLS LAST`), then `name` so ties always come out in the same order. Done: `task3Roster` passes.
4. **Course load (`reports/course_load.sql`).** The draft in the file fails with a GROUP BY error. Fix it so every selected column is grouped or aggregated, then finish the coordinator's request: columns `code`, `title`, `enrolled` (enrollments in the course), `graded` (enrollments that have a grade), `seats_left` (`capacity` minus `enrolled`); one row per course, all four courses, sorted by `code`. `PGA110` has no enrollments yet and must show 0 enrolled. Done: `task4CourseLoad` passes; if it fails, its message says what to change next.
5. **Averages by city (`reports/city_averages.sql`).** Group graded work by student city, writing a missing city as `not given`. The file supplies the `SELECT`'s first column, `COALESCE(s.city, 'not given') AS city`, and the `FROM` and `JOIN` lines; you add the other three columns and the `GROUP BY`, `HAVING` and `ORDER BY`, grouping by the same `COALESCE` expression. Columns `city`, `graded` (enrollments with a grade), `average_grade` (`AVG` of grade, unrounded), `best_grade` (`MAX` of grade). Keep only cities with at least 2 graded enrollments: that is a condition on an aggregate, so it belongs in `HAVING`, not `WHERE`. Sort by `average_grade` from highest to lowest. Done: `task5CityAverages` passes.
6. **Prerequisite gaps (`reports/prerequisite_gaps.sql`).** List each enrollment whose course has a prerequisite that the same student has no enrollment for. Join `courses` to itself (for example `courses AS c` and `courses AS p`) to show the prerequisite's title, and use `NOT EXISTS` to keep only the missing ones. Columns `name`, `course_code`, `prerequisite_code`, `prerequisite_title`, sorted by `name`, then `course_code`. Done: `task6PrerequisiteGaps` passes, and the whole run matches Expected output.

**Starting over.** Save your attempt elsewhere if you want to keep it, then delete your lines below the TODO line of the file you want to restart. For `reports/course_load.sql` and `reports/city_averages.sql`, restore the supplied lines from a fresh copy of the lab.

## Test

In `starter/`:

```
mvn -q -o test
```

Each run creates its own database with a unique name such as `queries_lab_3f9c0a1b7d2e`, runs the tests in it, and drops it again. It never removes a database it did not create. While a run is in progress, the database's name is in `starter/target/lab-database.txt`.

As shipped, all six tests fail; that is expected. Task 1 also checks that `seed.sql` uses `\copy` and `generate_series` on lines that are not comments; that one part is a static check of the file's text, while every other check runs your SQL on the database. A test cannot see whether you previewed your changes; do it anyway.

## Expected output

When every task is done, `mvn -q -o test` exits with status 0 (`echo $?` on macOS or Linux, `$LASTEXITCODE` in PowerShell, prints `0`) and `starter/target/surefire-reports/registrar.QueriesLabTest.txt` contains the line `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. Warnings such as `WARNING: A restricted method ...` are not test failures. The full summary is in `expected/test-summary.txt`.

## Offline note

Only the setup step downloads anything. The test command runs offline and talks only to your local PostgreSQL server.

## Cleanup

- The tests drop their own database when they finish.
- After an interrupted run, drop the database named in `starter/target/lab-database.txt`, for example `psql -h localhost -d postgres -c "DROP DATABASE queries_lab_3f9c0a1b7d2e;"`, before you delete `starter/target`.
- Drop your practice database: `psql -h localhost -d postgres -c "DROP DATABASE queries_scratch;"`.
- Delete `starter/target` to remove compiled classes and test reports. The libraries in your local Maven repository (`~/.m2/repository`) are kept for later labs.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Could not reach PostgreSQL to create the lab database` | Run the connection check with the same settings until it prints the version. |
| `password authentication failed` or `no password was provided` | Check the password file line (host, port, user, password) and its `chmod 600`, or `PGUSER`. |
| `permission denied to create database` | Use a user that may create databases, such as `postgres`. |
| `Could not start psql` | `psql --version` must work in this terminal. On Windows, add PostgreSQL's `bin` folder to `PATH`, or set `PSQL` to the full path of `psql.exe`. |
| `psql stopped on an error in seed.sql` with a foreign key | Insert the parent rows (courses, then students) before the rows that refer to them. |
| `must appear in the GROUP BY clause` | Every selected column must be listed in `GROUP BY` or be inside an aggregate. |
| `NOT IN` returns no rows | A NULL in the subquery makes `NOT IN` unknown for every row; use `NOT EXISTS`. |
| Maven cannot resolve a dependency offline | Run the setup step once with the internet on. |
| You pass 40 minutes | Pause, note the next failing task, and finish it in self-study. |

## Tested on

The shipped starter and a completed set of files were run from a fresh clone of this lab on macOS (arm64) with PostgreSQL 18.3, psql 18.3, JDK 25 and Maven 3.9.9 against a local server with trust authentication; the course authors keep that run's record with the course's own evidence. Linux and Windows runs have not yet been recorded; the steps follow PostgreSQL's documented behaviour on those systems.
