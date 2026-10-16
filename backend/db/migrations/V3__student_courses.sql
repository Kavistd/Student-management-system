-- V3: multiple courses per student, with a backfill of legacy course text.
--
-- Creates:
--   * public.student_courses           one row per (student, course) assignment
--   * public.student_course_legacy_v3  backup of every legacy students.course value
--                                      and the outcome of mapping it
--
-- MATCH RULE v1 (must stay identical in ../verify/preview_v3_student_courses.sql
-- and ../verify/verify_v3_student_courses.sql check 12):
--   candidate values:  students.course IS NOT NULL AND trim(students.course) <> ''
--   match key:         lower(trim(value))
--   a course matches:  lower(trim(course_code)) = key OR lower(trim(course_name)) = key
--   outcome:           0 distinct courses = UNMATCHED, 1 = MAPPED, 2+ = AMBIGUOUS
-- trim() removes spaces only; lower() follows the database's LC_CTYPE.
--
-- Data changes, all backed up first, all in one transaction:
--   * every candidate legacy value is copied to student_course_legacy_v3;
--   * MAPPED values get a student_courses row;
--   * students.course is cleared ONLY for MAPPED rows (backed up, unchanged, assigned).
-- UNMATCHED and AMBIGUOUS values stay in students.course for administrators.
-- The students.course column is NOT dropped. No other row is changed or deleted.
--
-- Run ONCE, as the postgres role, with the backend STOPPED. A second run is
-- refused (see step 1): re-applying a backfill after the application has used
-- student_courses could re-add assignments an administrator removed.
-- Before committing, the migration re-checks its own result and rolls back
-- everything if anything is inconsistent.
--
-- Rollback: V3__student_courses.rollback.sql (read its header first)
-- Verify:   ../verify/verify_v3_student_courses.sql

BEGIN;

-- 0. Fail fast instead of queueing behind other sessions (which would also
--    make later writers queue behind this one).
SET LOCAL lock_timeout = '10s';

-- No writes to students or courses until COMMIT; reads are still allowed.
-- Taken before the checks, so nothing can change between checking and acting.
LOCK TABLE public.students, public.courses IN SHARE ROW EXCLUSIVE MODE;

-- 1. Preconditions. The repository has no migration that created students or
--    courses (Hibernate did), so every property V3 depends on is verified here.
--    Any failure aborts the whole migration with no changes.
DO $$
DECLARE
    col_type     TEXT;
    col_nullable TEXT;
BEGIN
    -- The new tables must be owned by the application role.
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Aborted: run V3 as postgres (current role: %). Nothing was changed.', current_user;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres' AND rolbypassrls) THEN
        RAISE EXCEPTION 'Aborted: role postgres lacks BYPASSRLS. Nothing was changed.';
    END IF;

    -- Refuse a second run or a name collision; never adopt existing objects.
    IF to_regclass('public.student_courses') IS NOT NULL
       OR to_regclass('public.student_course_legacy_v3') IS NOT NULL
       OR to_regclass('public.idx_student_courses_course_id') IS NOT NULL THEN
        RAISE EXCEPTION 'Aborted: V3 objects already exist (V3 was applied before, or names collide). Nothing was changed. Run ../verify/verify_v3_student_courses.sql instead.';
    END IF;

    -- students and courses: ordinary tables owned by postgres.
    IF (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public' AND c.relkind = 'r'
           AND c.relname IN ('students', 'courses')
           AND pg_get_userbyid(c.relowner) = 'postgres') <> 2 THEN
        RAISE EXCEPTION 'Aborted: public.students and public.courses must be ordinary tables owned by postgres. Nothing was changed.';
    END IF;

    -- Referenced keys: bigint id columns that are each the whole primary key.
    SELECT data_type INTO col_type FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'students' AND column_name = 'id';
    IF col_type IS DISTINCT FROM 'bigint' THEN
        RAISE EXCEPTION 'Aborted: public.students.id is % (expected bigint). Nothing was changed.', coalesce(col_type, 'missing');
    END IF;
    SELECT data_type INTO col_type FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'courses' AND column_name = 'id';
    IF col_type IS DISTINCT FROM 'bigint' THEN
        RAISE EXCEPTION 'Aborted: public.courses.id is % (expected bigint). Nothing was changed.', coalesce(col_type, 'missing');
    END IF;
    IF (SELECT count(*) FROM pg_constraint con
         WHERE con.contype = 'p'
           AND con.conrelid IN ('public.students'::regclass, 'public.courses'::regclass)
           AND con.conkey = ARRAY[(SELECT a.attnum FROM pg_attribute a
                                    WHERE a.attrelid = con.conrelid AND a.attname = 'id')]) <> 2 THEN
        RAISE EXCEPTION 'Aborted: public.students and public.courses must each have PRIMARY KEY (id). Nothing was changed.';
    END IF;

    -- Legacy column: text, and nullable (V3 sets it to NULL for mapped rows).
    SELECT data_type, is_nullable INTO col_type, col_nullable FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'students' AND column_name = 'course';
    IF col_type IS NULL OR col_type NOT IN ('character varying', 'text') THEN
        RAISE EXCEPTION 'Aborted: public.students.course is % (expected character varying or text). Nothing was changed.', coalesce(col_type, 'missing');
    END IF;
    IF col_nullable <> 'YES' THEN
        RAISE EXCEPTION 'Aborted: public.students.course is NOT NULL; V3 must be able to clear it. Nothing was changed.';
    END IF;

    -- Catalog columns used by the match rule: text.
    IF (SELECT count(*) FROM information_schema.columns
         WHERE table_schema = 'public' AND table_name = 'courses'
           AND column_name IN ('course_code', 'course_name')
           AND data_type IN ('character varying', 'text')) <> 2 THEN
        RAISE EXCEPTION 'Aborted: public.courses.course_code and course_name must exist as character varying or text. Nothing was changed.';
    END IF;
END
$$;

-- 2. Assignments. The composite key prevents assigning a course twice.
--    Deleting a student removes its assignments; deleting an assigned course
--    is refused by the database (the application also refuses it first).
CREATE TABLE public.student_courses (
    student_id  BIGINT NOT NULL REFERENCES public.students (id) ON DELETE CASCADE,
    course_id   BIGINT NOT NULL REFERENCES public.courses (id) ON DELETE RESTRICT,
    PRIMARY KEY (student_id, course_id)
);

CREATE INDEX idx_student_courses_course_id ON public.student_courses (course_id);

-- 3. Backup of the original legacy values and their mapping outcome.
--    mapped_course_id deliberately has no foreign key: it is an audit value and
--    must not block deleting that course later. Rows of deleted students are
--    removed with them (nothing left to restore).
CREATE TABLE public.student_course_legacy_v3 (
    student_id        BIGINT                      NOT NULL PRIMARY KEY
                      REFERENCES public.students (id) ON DELETE CASCADE,
    original_value    TEXT                        NOT NULL,
    outcome           VARCHAR(9)                  NOT NULL
                      CHECK (outcome IN ('MAPPED', 'UNMATCHED', 'AMBIGUOUS')),
    match_count       INTEGER                     NOT NULL,
    mapped_course_id  BIGINT,
    backed_up_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    CHECK ((outcome = 'MAPPED') = (mapped_course_id IS NOT NULL)),
    CHECK ((outcome = 'UNMATCHED' AND match_count = 0)
        OR (outcome = 'MAPPED'    AND match_count = 1)
        OR (outcome = 'AMBIGUOUS' AND match_count >= 2))
);

-- 4. Back up and classify every candidate legacy value (MATCH RULE v1).
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
INSERT INTO public.student_course_legacy_v3 (student_id, original_value, outcome, match_count, mapped_course_id)
SELECT l.student_id,
       l.original_value,
       CASE WHEN m.match_count = 0 THEN 'UNMATCHED'
            WHEN m.match_count = 1 THEN 'MAPPED'
            ELSE 'AMBIGUOUS' END,
       m.match_count,
       CASE WHEN m.match_count = 1 THEN m.course_id END
  FROM legacy l
  JOIN matches m ON m.student_id = l.student_id;

-- 5. Assign the mapped courses.
INSERT INTO public.student_courses (student_id, course_id)
SELECT b.student_id, b.mapped_course_id
  FROM public.student_course_legacy_v3 b
 WHERE b.outcome = 'MAPPED';

-- 6. Clear legacy text only where it is backed up, unchanged, and assigned.
UPDATE public.students s
   SET course = NULL
  FROM public.student_course_legacy_v3 b
 WHERE b.student_id = s.id
   AND b.outcome = 'MAPPED'
   AND s.course = b.original_value
   AND EXISTS (SELECT 1 FROM public.student_courses sc
                WHERE sc.student_id = b.student_id AND sc.course_id = b.mapped_course_id);

-- 7. Same Data API protection as every other application table (V1, V2).
--    (Uncommitted until COMMIT, so the default grants are never visible.)
ALTER TABLE public.student_courses          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_course_legacy_v3 ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.student_courses, public.student_course_legacy_v3 FROM anon, authenticated;

-- 8. Self-check before commit. Any failure rolls back the whole migration.
DO $$
DECLARE
    problems BIGINT;
    r        RECORD;
BEGIN
    -- Exactly one assignment per MAPPED value, and nothing else.
    IF (SELECT count(*) FROM public.student_courses)
       <> (SELECT count(*) FROM public.student_course_legacy_v3 WHERE outcome = 'MAPPED')
       OR EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                   WHERE b.outcome = 'MAPPED'
                     AND NOT EXISTS (SELECT 1 FROM public.student_courses sc
                                      WHERE sc.student_id = b.student_id
                                        AND sc.course_id = b.mapped_course_id)) THEN
        RAISE EXCEPTION 'Aborted: assignments do not match the MAPPED backups. Nothing was changed.';
    END IF;

    -- MAPPED legacy text cleared; UNMATCHED/AMBIGUOUS preserved exactly.
    SELECT count(*) INTO problems
      FROM public.student_course_legacy_v3 b
      JOIN public.students s ON s.id = b.student_id
     WHERE (b.outcome = 'MAPPED' AND s.course IS NOT NULL)
        OR (b.outcome <> 'MAPPED' AND s.course IS DISTINCT FROM b.original_value);
    IF problems > 0 THEN
        RAISE EXCEPTION 'Aborted: % legacy values were not cleared/preserved as expected. Nothing was changed.', problems;
    END IF;

    -- Every candidate legacy value that remains is backed up.
    IF EXISTS (SELECT 1 FROM public.students s
                WHERE s.course IS NOT NULL AND trim(s.course) <> ''
                  AND NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                                   WHERE b.student_id = s.id)) THEN
        RAISE EXCEPTION 'Aborted: a legacy value has no backup. Nothing was changed.';
    END IF;

    FOR r IN SELECT outcome, count(*) AS n FROM public.student_course_legacy_v3 GROUP BY outcome ORDER BY outcome LOOP
        RAISE NOTICE 'V3 legacy values %: %', r.outcome, r.n;
    END LOOP;
    RAISE NOTICE 'V3 assignments created: %', (SELECT count(*) FROM public.student_courses);
END
$$;

COMMIT;
