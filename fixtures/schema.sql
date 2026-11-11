-- Registrar database for the queries lab. Supplied: do not edit.
-- It is the enrollment design from the Relational Design lab, plus two columns:
-- students.city (optional) and courses.prerequisite_code (the course you should take first).
CREATE TABLE students (
    id    integer PRIMARY KEY,
    name  varchar(100) NOT NULL,
    email text NOT NULL UNIQUE,
    city  text
);

CREATE TABLE courses (
    code              text PRIMARY KEY,
    title             text NOT NULL,
    capacity          integer NOT NULL CHECK (capacity > 0),
    prerequisite_code text REFERENCES courses (code)
);

CREATE TABLE enrollments (
    student_id  integer NOT NULL REFERENCES students (id),
    course_code text NOT NULL REFERENCES courses (code),
    enrolled_at timestamp NOT NULL DEFAULT now(),
    grade       numeric(5, 2) CHECK (grade BETWEEN 0 AND 100),
    PRIMARY KEY (student_id, course_code)
);
