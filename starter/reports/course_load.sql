-- Task 4: the course load report (README.md, Task 4). One SELECT statement.
-- Columns, in order: code, title, enrolled, graded, seats_left.
-- The previous coordinator left this draft. It fails with a GROUP BY error and it drops
-- courses that have no enrollments. TODO: fix and finish it.
SELECT c.code, c.title, COUNT(e.student_id) AS enrolled
FROM courses AS c
JOIN enrollments AS e ON e.course_code = c.code
GROUP BY e.course_code
ORDER BY c.code;
