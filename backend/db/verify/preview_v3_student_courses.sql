-- READ-ONLY preview of how V3__student_courses.sql will classify legacy values.
-- Run BEFORE V3 (and again right before running it, if data may have changed).
-- Changes nothing.
--
-- MATCH RULE v1, identical to V3 step 4 and verify check 12:
--   candidate values:  students.course IS NOT NULL AND trim(students.course) <> ''
--   match key:         lower(trim(value))
--   a course matches:  lower(trim(course_code)) = key OR lower(trim(course_name)) = key
--   outcome:           0 distinct courses = UNMATCHED, 1 = MAPPED, 2+ = AMBIGUOUS
-- trim() removes spaces only (tabs/newlines are kept, so such values stay
-- UNMATCHED unless they match exactly); lower() follows the database LC_CTYPE.

-- 1. Per-student outcome. MAPPED rows show the course V3 will assign.
WITH legacy AS (
    SELECT s.id AS student_id, s.course AS original_value, lower(trim(s.course)) AS match_key
      FROM public.students s
     WHERE s.course IS NOT NULL AND trim(s.course) <> ''
),
matches AS (
    SELECT l.student_id, count(DISTINCT c.id) AS match_count, min(c.id) AS course_id
      FROM legacy l
      LEFT JOIN public.courses c
        ON lower(trim(c.course_code)) = l.match_key
        OR lower(trim(c.course_name)) = l.match_key
     GROUP BY l.student_id
)
SELECT l.student_id,
       l.original_value,
       CASE WHEN m.match_count = 0 THEN 'UNMATCHED'
            WHEN m.match_count = 1 THEN 'MAPPED'
            ELSE 'AMBIGUOUS' END                    AS outcome,
       m.match_count,
       CASE WHEN m.match_count = 1 THEN m.course_id END AS mapped_course_id
  FROM legacy l
  JOIN matches m ON m.student_id = l.student_id
 ORDER BY outcome, l.student_id;

-- 2. Totals per outcome.
WITH legacy AS (
    SELECT s.id AS student_id, lower(trim(s.course)) AS match_key
      FROM public.students s
     WHERE s.course IS NOT NULL AND trim(s.course) <> ''
),
matches AS (
    SELECT l.student_id, count(DISTINCT c.id) AS match_count
      FROM legacy l
      LEFT JOIN public.courses c
        ON lower(trim(c.course_code)) = l.match_key
        OR lower(trim(c.course_name)) = l.match_key
     GROUP BY l.student_id
)
SELECT CASE WHEN match_count = 0 THEN 'UNMATCHED'
            WHEN match_count = 1 THEN 'MAPPED'
            ELSE 'AMBIGUOUS' END AS outcome,
       count(*)                  AS students
  FROM matches
 GROUP BY 1
 ORDER BY 1;
