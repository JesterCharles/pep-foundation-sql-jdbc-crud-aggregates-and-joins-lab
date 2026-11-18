"""Held-out tests: graded on submit, never shown to the learner."""

from test.support import ENROLLMENTS, check_changed_enrollments, code_only, learner_sql, registrar_data, run_changes, table


def test_task1_seed_reads_the_imports():
    learner_sql("Task 1", "seed.sql")
    code = code_only("seed.sql").lower()
    missing = [name for name in ("import_students", "import_enrollments") if name not in code]
    assert not missing, ("Task 1 (static check of the file text): copy both import tables into the real tables with "
                         f"INSERT ... SELECT, as the task asks; seed.sql does not read {' or '.join(missing)}.")
    assert "generate_series" in code, ("Task 1 (static check of the file text): add the guest students with "
                                       "INSERT ... SELECT and generate_series, as the task asks.")


def test_task2_changes_touch_only_the_intended_rows(db):
    registrar_data(db)
    # Two extra enrollments: a WHERE that matches more than the one intended row (for example only
    # student_id = 7, or only the course and an empty grade) also changes one of these.
    db.execute("INSERT INTO enrollments (student_id, course_code, enrolled_at, grade) VALUES "
               "(7, 'PGA110', '2026-02-02 14:00', 65), (8, 'SQL201', '2026-03-04 10:00', NULL)")
    before = table(db, ENROLLMENTS)
    run_changes(db)
    check_changed_enrollments(db, before)
