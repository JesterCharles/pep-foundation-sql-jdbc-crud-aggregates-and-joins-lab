-- Raw import tables for Task 1. Supplied: do not edit.
-- The tests load students.csv and enrollments.csv into them unchanged: every column is text,
-- and an empty CSV field is an empty string ''.
CREATE TABLE import_students (
    id    text,
    name  text,
    email text,
    city  text
);

CREATE TABLE import_enrollments (
    student_id  text,
    course_code text,
    enrolled_at text,
    grade       text
);
