-- Task 5: grade averages by city (README.md, Task 5). One SELECT statement.
-- Columns, in order: city, graded, average_grade, best_grade.
-- Supplied: the rows to group, each enrollment with its student's city, a missing city written as 'not given'.
SELECT COALESCE(s.city, 'not given') AS city,
       -- TODO: add the columns graded, average_grade and best_grade here.
FROM students AS s
JOIN enrollments AS e ON e.student_id = s.id
-- TODO: add GROUP BY, HAVING and ORDER BY below this line.
