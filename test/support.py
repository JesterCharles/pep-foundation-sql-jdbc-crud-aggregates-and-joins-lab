"""Test plumbing shared by the visible tests and the held-out tests.

You need no PostgreSQL server of your own. The pgserver package (see requirements.txt) contains a
real PostgreSQL 16. The tests start a private copy of it in a temporary folder, give every test a
brand-new empty database, and delete the server and its data when the run ends. They never touch
any other database.
"""

from __future__ import annotations

import contextlib
import re
import secrets
import tempfile
from decimal import Decimal
from pathlib import Path

import psycopg
import pytest

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
FIXTURES = Path(__file__).resolve().parent / "fixtures"

COURSES = "SELECT code, title, capacity, prerequisite_code FROM courses ORDER BY code"
STUDENTS = "SELECT id, name, email, city FROM students ORDER BY id"
ENROLLMENTS = "SELECT student_id, course_code, enrolled_at, grade FROM enrollments ORDER BY student_id, course_code"


# ---------- the private PostgreSQL ----------

def start_server():
    """Starts a private PostgreSQL in a new temporary folder and returns its pgserver handle."""
    try:
        import pgserver
    except ImportError as error:
        raise RuntimeError(
            "The tests start their own PostgreSQL with the pgserver package, which is not installed. "
            "Run: python -m pip install -r requirements.txt (pgserver needs Python 3.9 to 3.12)."
        ) from error
    return pgserver.get_server(tempfile.mkdtemp(prefix="pglab-"), cleanup_mode="delete")


@contextlib.contextmanager
def fresh_database(server):
    """Creates a new, empty database, yields an autocommit connection to it, then drops it."""
    name = "lab_" + secrets.token_hex(6)
    with psycopg.connect(server.get_uri(), autocommit=True) as admin:
        admin.execute(f"CREATE DATABASE {name}")
        try:
            with psycopg.connect(server.get_uri(database=name), autocommit=True) as conn:
                yield conn
        finally:
            admin.execute(f"DROP DATABASE IF EXISTS {name} WITH (FORCE)")


# ---------- the registrar data ----------

def _copy_csv(db, table: str, columns: str, csv_name: str, keep_empty_strings: bool) -> None:
    options = "FORMAT csv, HEADER"
    if keep_empty_strings:
        options += f", FORCE_NOT_NULL ({columns})"
    with db.cursor().copy(f"COPY {table} ({columns}) FROM STDIN WITH ({options})") as copy:
        copy.write((FIXTURES / csv_name).read_text(encoding="utf-8"))


def empty_tables(db) -> None:
    """Fresh, empty registrar tables from test/fixtures/schema.sql."""
    db.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
    db.execute((FIXTURES / "schema.sql").read_text(encoding="utf-8"))


def import_tables(db) -> None:
    """The raw import tables for Task 1, holding the two CSV files unchanged (an empty field is '')."""
    db.execute((FIXTURES / "import.sql").read_text(encoding="utf-8"))
    _copy_csv(db, "import_students", "id, name, email, city", "students.csv", keep_empty_strings=True)
    _copy_csv(db, "import_enrollments", "student_id, course_code, enrolled_at, grade", "enrollments.csv",
              keep_empty_strings=True)


def registrar_data(db) -> None:
    """Empty tables plus the registrar data that Task 1 seeds, loaded by the tests themselves."""
    empty_tables(db)
    db.execute("INSERT INTO courses (code, title, capacity, prerequisite_code) VALUES "
               "('SQL101', 'SQL Foundations', 6, NULL), ('PGA110', 'PostgreSQL Admin Basics', 2, NULL), "
               "('SQL201', 'Reporting with SQL', 4, 'SQL101'), ('JDB301', 'JDBC Data Access', 4, 'SQL201')")
    db.execute("INSERT INTO students (id, name, email) SELECT g, 'Guest ' || g, 'guest' || g || '@registrar.test' "
               "FROM generate_series(901, 905) AS g")
    _copy_csv(db, "students", "id, name, email, city", "students.csv", keep_empty_strings=False)
    _copy_csv(db, "enrollments", "student_id, course_code, enrolled_at, grade", "enrollments.csv", keep_empty_strings=False)


# ---------- the learner's files ----------

def learner_sql(task: str, name: str) -> str:
    """The learner's file src/<name>; fails the task while it holds only comments and blank lines."""
    content = (SRC / name).read_text(encoding="utf-8")
    if not any(line.strip() and not line.strip().startswith("--") for line in content.splitlines()):
        pytest.fail(f"{task}: {name} has no SQL yet. Write it below the TODO line (lab instructions, {task}).", pytrace=False)
    return content


def code_only(name: str) -> str:
    """The file's SQL with block comments, -- comments and blank lines removed, for the few checks that read the text."""
    content = re.sub(r"/\*.*?\*/", " ", (SRC / name).read_text(encoding="utf-8"), flags=re.S)
    kept = [line.split("--", 1)[0].strip() for line in content.splitlines()]
    return "\n".join(line for line in kept if line) + "\n"


# ---------- results as text ----------

def text(value) -> str:
    """A value as the tests compare it: NULL for a missing value, numbers without trailing zeros."""
    if value is None:
        return "NULL"
    if isinstance(value, Decimal):
        return "0" if value == 0 else format(value.normalize(), "f")
    return str(value)


def _rows(cursor) -> list[list[str]]:
    return [[text(value) for value in row] for row in cursor.fetchall()]


def table(db, sql: str) -> list[list[str]]:
    """Runs one SELECT and returns its rows as text, with NULL written as "NULL"."""
    return _rows(db.execute(sql))


def run_script(db, sql: str) -> list[list[list[str]] | None]:
    """Runs every statement of a file; one entry per statement: its rows, or None when it returned only a row count."""
    cursor = db.cursor()
    cursor.execute(sql)
    results = []
    while True:
        results.append(_rows(cursor) if cursor.description is not None else None)
        if not cursor.nextset():
            return results


def report(db, task: str, name: str, columns: list[str]) -> list[list[str]]:
    """Runs a report file and checks its column names; returns its rows."""
    sql = learner_sql(task, name)
    problem = None
    try:
        cursor = db.execute(sql)
    except psycopg.Error as error:
        problem = str(error).strip()
    if problem is not None:
        advice = (" Every selected column must either be listed in GROUP BY or be inside an aggregate function."
                  if "must appear in the GROUP BY clause" in problem else "")
        pytest.fail(f"{task}: {name} did not run. PostgreSQL said: {problem}{advice}", pytrace=False)
    actual = [column.name for column in cursor.description] if cursor.description else []
    assert actual == columns, (f"{task}: {name} must return the columns {columns} in that order "
                               f"(name them with AS); it returned {actual}.")
    return _rows(cursor)


def run_changes(db) -> list[list[list[str]] | None]:
    """Task 2: checks changes.sql holds no transaction statements, runs it, and returns its per-statement results."""
    sql = learner_sql("Task 2", "changes.sql")
    code = code_only("changes.sql").upper()
    if re.search(r"\b(BEGIN|START\s+TRANSACTION|COMMIT|ROLLBACK)\b", code) or re.search(r"(?:^|\n)\s*END\s*;", code):
        pytest.fail("Task 2: leave BEGIN, COMMIT and other transaction statements out of changes.sql; the test runs the file "
                    "as it is and checks the table afterwards. Keep only the UPDATE and the DELETE, each with RETURNING.",
                    pytrace=False)
    problem = None
    try:
        return run_script(db, sql)
    except psycopg.Error as error:
        problem = str(error).strip()
    pytest.fail(f"Task 2: changes.sql did not run. PostgreSQL said: {problem}", pytrace=False)


def check_changed_enrollments(db, before: list[list[str]]) -> None:
    """Task 2: only student 2's SQL201 grade became 79 and only student 7's JDB301 row is gone."""
    expected = []
    for row in before:
        if row[0] == "7" and row[1] == "JDB301":
            continue
        expected.append([row[0], row[1], row[2], "79"] if row[0] == "2" and row[1] == "SQL201" else row)
    after = table(db, ENROLLMENTS)
    problems = []
    if len(after) > len(expected):
        problems.append("student 7's JDB301 enrollment should be deleted and nothing else")
    elif len(after) < len(expected):
        problems.append("only student 7's JDB301 enrollment should be deleted: the DELETE's WHERE matched more rows")
    assert after == expected, ("Task 2: after changes.sql the enrollments are not as asked. "
                               + "; ".join(problems) + (". " if problems else "")
                               + "Only student 2's SQL201 grade should become 79 and only student 7's JDB301 row should go; "
                               + f"preview each WHERE with a SELECT first. Expected {expected} but found {after}.")


def same_rows(task: str, name: str, expected, actual, hint: str | None) -> None:
    assert actual == expected, (f"{task}: {name} returned the wrong rows or order. "
                                f"{hint + ' ' if hint else ''}Expected {expected} but got {actual}.")
