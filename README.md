# Queries lab: run the registrar's term desk

You look after a registrar database: students, courses and enrollments.
This term you load it with data, make two corrections, and write the four reports the course coordinator asks for.
The tests run every file you write against a fresh PostgreSQL database.

You practise INSERT and INSERT ... SELECT, UPDATE and DELETE with RETURNING, joins, CASE, GROUP BY with HAVING, and NOT EXISTS.

**Time:** about 35 minutes, at most 40. If you pass 40 minutes, note the next failing task and finish it later.

You need no database server of your own: the tests start a private PostgreSQL 16 for each run and delete it afterwards.

## Quick start

1. Open the lab project. The files you edit are in `src/`; the checklist below names them.
2. Select **Run Tests** before you change anything.

   **All 6 visible tests fail at first. That is expected: each failure is a task you have not done yet.**
   Every failure message starts with its task number, such as `Task 1:`.
3. Work through the checklist below. After each task, select **Run Tests** again and read the remaining `Task N:` messages.
4. **You are finished when all 6 visible tests pass.** Then select **Submit**.
   Submitting also runs held-out tests you can't see. They check that each file does what its task asks in full, for example that a `WHERE` matches only the rows it should, even when the data differs.

```
OPEN project -> RUN TESTS: 6 fail (expected)
  -> READ the "Task N:" messages -> EDIT the next TODO -> RUN TESTS again
  -> repeat for Tasks 1-6 -> SUBMIT when all 6 tests pass
```

In words: open the project, run the tests, read which tasks fail, complete one task's TODO, run the tests again, and submit when they all pass.

## Checklist

You edit only the files below, all in `src/`. Each one has a `TODO` line that marks where your work goes. You can do Tasks 2 to 6 in any order.

- [ ] Practice database (about 3 minutes, not tested): open it now, so you can look at the data and preview Task 2. See [Practice database](#practice-database).
- [ ] Task 1: seed the database, `seed.sql` (test `test_task1_seed`)
- [ ] Task 2: make two corrections, `changes.sql` (test `test_task2_changes`)
- [ ] Task 3: SQL-track roster, `roster.sql` (test `test_task3_roster`)
- [ ] Task 4: course load, `course_load.sql` (test `test_task4_course_load`)
- [ ] Task 5: averages by city, `city_averages.sql` (test `test_task5_city_averages`)
- [ ] Task 6: prerequisite gaps, `prerequisite_gaps.sql` (test `test_task6_prerequisite_gaps`)

A task is done when no failure message starts with its number.
The tests and their data in `test/` are restored to their original state before your work is graded, so changes to them do not count.

From a terminal in the project folder, you can run one task's test on its own, for example `python -m pytest -q -k task1`, or all of them with `python -m pytest -q`.

## The data

`test/fixtures/schema.sql` creates the tables `students`, `courses` and `enrollments`, including `students.city` (may be empty) and `courses.prerequisite_code` (the course to take first, or empty).
`test/fixtures/students.csv` holds 8 students and `test/fixtures/enrollments.csv` holds 12 enrollments; an empty field means NULL.

The four courses (Task 1 inserts them):

| code | title | capacity | prerequisite_code |
| --- | --- | --- | --- |
| `SQL101` | SQL Foundations | 6 | none (NULL) |
| `PGA110` | PostgreSQL Admin Basics | 2 | none (NULL) |
| `SQL201` | Reporting with SQL | 4 | `SQL101` |
| `JDB301` | JDBC Data Access | 4 | `SQL201` |

Every test starts from fresh tables. Task 1 runs your `seed.sql`; Tasks 2 to 6 load the same data themselves.

## Practice database

A practice database lets you look at the data and preview Task 2's changes. The tests never use or check it. In a terminal, from the project folder:

```
python -m test.practice
```

It starts a private PostgreSQL with the registrar tables and all their data, and opens a `psql` prompt.
At the prompt, run `\dt` (the three tables), `\d enrollments` (its columns, keys and indexes) and any `SELECT` you like.
Leave with `\q`: the practice database is deleted, so the next start is fresh and your changes never carry over.

Other ways to start it:

| Command | What you get |
| --- | --- |
| `python -m test.practice src/roster.sql` | the full data, your report run once, then the prompt |
| `python -m test.practice --empty` | empty tables plus the two import tables, for Task 1 |
| `python -m test.practice --empty src/seed.sql` | your seed script run on the empty tables, then the prompt |

## Task steps

### Task 1: seed the database (`seed.sql`)

A bulk import has already copied the two CSV files, unchanged, into two import tables: `import_students (id, name, email, city)` and `import_enrollments (student_id, course_code, enrolled_at, grade)`.
Every import column is `text`, and an empty CSV field is an empty string `''`. Your seed script moves the rows into the real tables, converting each value to the column's type:

1. Insert the four courses above with one `INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES ...`, naming the columns. A course must exist before a course that names it as a prerequisite, and before any enrollment in it.
2. The students' statement is supplied: `INSERT INTO students (id, name, email, city) SELECT id::integer, name, email, NULLIF(city, '') FROM import_students;`
3. Add five guest students with `INSERT ... SELECT` over `generate_series(901, 905)`: ids 901 to 905, names `Guest 901` to `Guest 905`, emails `guest901@registrar.test` to `guest905@registrar.test`, and no city.
4. Copy the enrollments from `import_enrollments` with a second `INSERT ... SELECT`, after the students and courses they refer to. An empty grade must become NULL.

The tests run the whole file at once. If any statement fails, none of the file's rows are kept, and the failure quotes PostgreSQL's message.

<details><summary>Hint</summary>

Use the supplied students' statement as the pattern for step 4: cast each text column with `::` to the type the `enrollments` column has, and turn `''` into NULL with `NULLIF` before casting the grade.

</details>

### Task 2: make two corrections (`changes.sql`)

First preview each `WHERE` with a `SELECT` in your practice database, then keep only the two changes in the file:
1. Student 2's SQL201 grade has arrived: set it to 79 with an `UPDATE` whose `WHERE` matches only that enrollment.
2. Student 7 withdraws from JDB301: remove that one enrollment with a `DELETE`.
3. End both statements with `RETURNING`, so each shows the row it changed.

The test compares the whole table afterwards, so a `WHERE` that matches extra rows fails.

### Task 3: SQL-track roster (`roster.sql`)

One row per enrollment in a course whose code starts with `SQL`, joined to the student. Columns `course_code`, `name`, `city`, `grade`, `result`:
- `city` shows `not given` when it is NULL.
- `result` is a `CASE`: `pending` when the grade is NULL, `pass` when it is 50 or more, otherwise `fail`.
- Sort by `course_code`, then grade from highest to lowest with ungraded students last, then `name`.

<details><summary>Hint</summary>

Use `LIKE` for the course-code prefix, `COALESCE` for the missing city, and `NULLS LAST` to put ungraded students last.

</details>

### Task 4: course load (`course_load.sql`)

The draft in the file fails with a GROUP BY error. Fix it, then finish it:
- Columns `code`, `title`, `enrolled` (enrollments in the course), `graded` (enrollments that have a grade), `seats_left` (`capacity` minus `enrolled`).
- One row per course, all four courses, sorted by `code`. `PGA110` has no enrollments yet and must show 0 enrolled.

<details><summary>Hint</summary>

To fix the GROUP BY error, make every selected column grouped or aggregated.

</details>

### Task 5: averages by city (`city_averages.sql`)

The file supplies the first column, `COALESCE(s.city, 'not given') AS city`, and the `FROM` and `JOIN` lines. You add:
- the columns `graded` (enrollments with a grade), `average_grade` (`AVG` of grade, unrounded) and `best_grade` (`MAX` of grade);
- a `GROUP BY` on the same `COALESCE` expression;
- a filter that keeps only cities with at least 2 graded enrollments;
- sorting by `average_grade` from highest to lowest.

<details><summary>Hint</summary>

The filter is a condition on an aggregate, so it belongs in `HAVING`, not `WHERE`.

</details>

### Task 6: prerequisite gaps (`prerequisite_gaps.sql`)

List each enrollment whose course has a prerequisite that the same student has no enrollment for, with the prerequisite's title. Columns `name`, `course_code`, `prerequisite_code`, `prerequisite_title`, sorted by `name`, then `course_code`.

<details><summary>Hint</summary>

Join `courses` to itself (for example `courses AS c` and `courses AS p`) to show the prerequisite's title, and use `NOT EXISTS` to keep only the missing ones.

</details>

**Starting over (optional):** before you change a file, keep a copy of it outside the project folder. To restart that file, paste the copy back.

## What the tests do

The first test starts a private PostgreSQL 16 from the `pgserver` package in a temporary folder.
Every test then gets its own new database with fresh tables, runs your file in it, compares the results, and drops that database.
At the end of the run the server and its folder are deleted. The tests never connect to any other database.

## Using an AI assistant

If you use an AI assistant, ask it to explain, not to answer.
"Explain why this column must appear in GROUP BY" teaches you SQL; "write my report query" does not.
Check any explanation with a test run before you trust it.

## Troubleshooting

| What you see | What to do |
| --- | --- |
| `pgserver package, which is not installed` | Run `python -m pip install -r requirements.txt`. `pgserver` needs Python 3.9 to 3.12; **Run Tests** uses a supported Python. |
| `seed.sql stopped on an error` with a foreign key | Insert the parent rows (courses, then students) before the rows that refer to them. |
| `seed.sql stopped on an error` with `invalid input syntax` | An empty string `''` cannot be cast to a number: wrap the column in `NULLIF(column, '')` before the cast. |
| `must appear in the GROUP BY clause` | Every selected column must be in `GROUP BY` or inside an aggregate. |
| `NOT IN` returns no rows | A NULL in the subquery makes `NOT IN` unknown for every row; use `NOT EXISTS`. |
