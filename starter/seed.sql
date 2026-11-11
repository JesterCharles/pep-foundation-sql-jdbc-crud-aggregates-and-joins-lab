-- Task 1: seed script. The tests run this file with psql from the starter/ folder,
-- into a new database that already has the tables from ../fixtures/schema.sql.
-- Write the statements in the order the foreign keys need (README.md, Task 1):
--   1. INSERT the four courses, naming the columns.
--   2. \copy the students from ../fixtures/students.csv.
--   3. INSERT ... SELECT the five guest students with generate_series.
--   4. \copy the enrollments from ../fixtures/enrollments.csv.
-- Step 2 is supplied: psql's \copy reads the students from the CSV file on your machine.
\copy students (id, name, email, city) FROM '../fixtures/students.csv' WITH (FORMAT csv, HEADER)
-- TODO: write steps 1, 3 and 4 below this line, in that order.
