-- Task 1: seed script. The tests run this whole file in a new database that already has
-- the tables from test/fixtures/schema.sql, plus two import tables holding the raw CSV files:
--   import_students    (id, name, email, city)                     from students.csv
--   import_enrollments (student_id, course_code, enrolled_at, grade) from enrollments.csv
-- Every import column is text, and an empty CSV field is an empty string ''.
-- Write the statements in the order the foreign keys need (lab instructions, Task 1):
--   1. INSERT the four courses, naming the columns.
--   2. INSERT ... SELECT the students from import_students.
--   3. INSERT ... SELECT the five guest students with generate_series.
--   4. INSERT ... SELECT the enrollments from import_enrollments.
-- Step 2 is supplied: it converts each text column to the table's type, and an empty city to NULL.
INSERT INTO students (id, name, email, city)
SELECT id::integer, name, email, NULLIF(city, '')
FROM import_students;
-- TODO: write steps 1, 3 and 4 below this line, in that order.
