# Queries lab: run the registrar's term desk

You look after a registrar database: students, courses and enrollments.
This term you load it with data, make two corrections, and write the four reports the course coordinator asks for, in six SQL files.
The tests run every file against a fresh database on a PostgreSQL server.

You practise INSERT, `\copy`, UPDATE and DELETE with RETURNING, joins, CASE, GROUP BY with HAVING, and NOT EXISTS.

The full learner instructions are in [`.evolv/instructions.md`](.evolv/instructions.md), which the lab platform shows as the lab's instructions.

## Layout

This repository follows the Exa coding-lab scaffold for Java.

| Path | What it is |
|---|---|
| `src/main/sql/seed.sql` | Lab source: Task 1, run with `psql` |
| `src/main/sql/changes.sql` | Lab source: Task 2 |
| `src/main/sql/reports/*.sql` | Lab source: Tasks 3 to 6, one query each |
| `src/test/java/registrar/QueriesLabTest.java` | Visible tests |
| `src/test/java/registrar/LabDatabase.java` | Test support: creates and drops the run's own lab database, runs `psql` |
| `fixtures/schema.sql`, `fixtures/*.csv` | The registrar tables and the data the lab loads |
| `.evolv/instructions.md` | Learner instructions |
| `.evolv/grade.yml` | Grading manifest, used by Run Tests and by the grader |
| `pom.xml` | Maven build: Java 17 and JUnit 5.11.3, matching the scaffold, plus the PostgreSQL JDBC driver |
| `Dockerfile` | Image for local Run Tests; warms the Maven cache and installs `psql` |
| `expected/test-summary.txt` | The result of a finished lab |

There are no held-out tests in this lab: every test is visible.

## PostgreSQL prerequisite

The tests need a running PostgreSQL server (16 or later) that they reach over TCP, and Task 1 runs `seed.sql` with the `psql` client (on `PATH`, or its full path in `PSQL`).
They read `PGHOST`, `PGPORT`, `PGUSER` and `PGDATABASE` (defaults `localhost`, `5432`, the login user, `postgres`) and a password file or `PGPASSWORD`.
The user must be allowed to create databases: each run creates its own uniquely named database and drops it afterwards.
Without a reachable server every test fails with `Could not reach PostgreSQL to create the lab database`.

## Run the tests

On the platform, select **Run Tests**. From a terminal with JDK 17 or later, Apache Maven 3.9, `psql` and a reachable PostgreSQL server:

```
mvn -q test
```

All 6 tests fail until the `TODO`s are done. That is expected.
