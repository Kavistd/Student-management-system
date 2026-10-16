-- Rollback for V3__student_courses.sql.
--
-- WHAT THIS DESTROYS: public.student_courses is dropped, so EVERY current
-- course assignment is removed from the live schema, including assignments
-- administrators made after V3. The pre-V3 single legacy value is restored only
-- for students whose value V3 mapped (and only where students.course is still
-- empty, so no value is ever overwritten).
--
-- WHAT THIS KEEPS: before dropping anything, both V3 tables are copied into
-- inert archive tables (no foreign keys, RLS on, no Data API access):
--   public.student_courses_v3_rollback_archive         (student_id, course_id, course_code, course_name, archived_at)
--   public.student_course_legacy_v3_rollback_archive   (the full V3 backup)
-- They are NOT dropped by this script. Delete them deliberately once recovery
-- is no longer needed.
--
-- Order:
--   1. Stop the backend.
--   2. Restore the pre-V3 application code (code using student_courses fails
--      Hibernate validation without the table).
--   3. For a read-only look at what will be lost, run
--      verify_v3_student_courses.sql checks 7, 8 and 11 first.
--   4. Uncomment the confirmation line below and run this script as postgres.
--   5. Start the backend.
--
-- Recovery (re-applying V3 later): with the backend stopped and the V3 code
-- deployed, run V3 again (it re-maps the restored legacy values with the same
-- rule), then re-insert the archived assignments that still reference existing
-- students and courses:
--
--   INSERT INTO public.student_courses (student_id, course_id)
--   SELECT a.student_id, a.course_id
--     FROM public.student_courses_v3_rollback_archive a
--    WHERE EXISTS (SELECT 1 FROM public.students s WHERE s.id = a.student_id)
--      AND EXISTS (SELECT 1 FROM public.courses  c WHERE c.id = a.course_id)
--   ON CONFLICT (student_id, course_id) DO NOTHING;
--
-- then run verify_v3_student_courses.sql. Checks 8, 11 and 12 then reflect the
-- recovered state, not a fresh V3 run, so differences there are expected.

BEGIN;

-- Deliberate confirmation. Uncomment ONLY after reading the header above.
-- SET LOCAL sms.confirm_v3_rollback = 'DESTROY_ALL_ASSIGNMENTS';

SET LOCAL lock_timeout = '10s';

DO $$
BEGIN
    IF coalesce(current_setting('sms.confirm_v3_rollback', true), '') <> 'DESTROY_ALL_ASSIGNMENTS' THEN
        RAISE EXCEPTION 'Refusing to roll back V3 without confirmation: uncomment the SET LOCAL sms.confirm_v3_rollback line. Nothing was changed.';
    END IF;
    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Aborted: run the rollback as postgres (current role: %). Nothing was changed.', current_user;
    END IF;
    IF to_regclass('public.student_courses') IS NULL OR to_regclass('public.student_course_legacy_v3') IS NULL THEN
        RAISE EXCEPTION 'Aborted: V3 tables are missing (V3 not applied, or already rolled back). Nothing was changed.';
    END IF;
    IF to_regclass('public.student_courses_v3_rollback_archive') IS NOT NULL
       OR to_regclass('public.student_course_legacy_v3_rollback_archive') IS NOT NULL THEN
        RAISE EXCEPTION 'Aborted: rollback archive tables already exist from an earlier rollback. Review and remove them first. Nothing was changed.';
    END IF;
END
$$;

-- Same lock order as V3, then exclusive locks on the V3 tables.
LOCK TABLE public.students, public.courses IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.student_courses, public.student_course_legacy_v3 IN ACCESS EXCLUSIVE MODE;

-- 1. Report what is about to be lost from the live schema.
DO $$
BEGIN
    RAISE NOTICE 'Assignments being archived and dropped: %', (SELECT count(*) FROM public.student_courses);
    RAISE NOTICE 'Assignments added after V3 (not from a MAPPED backup): %',
        (SELECT count(*) FROM public.student_courses sc
          WHERE NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                             WHERE b.outcome = 'MAPPED' AND b.student_id = sc.student_id
                               AND b.mapped_course_id = sc.course_id));
    RAISE NOTICE 'V3 assignments removed after V3 (legacy text will still be restored): %',
        (SELECT count(*) FROM public.student_course_legacy_v3 b
          WHERE b.outcome = 'MAPPED'
            AND NOT EXISTS (SELECT 1 FROM public.student_courses sc
                             WHERE sc.student_id = b.student_id AND sc.course_id = b.mapped_course_id));
END
$$;

-- 2. Archive both tables as inert copies.
CREATE TABLE public.student_courses_v3_rollback_archive AS
SELECT sc.student_id, sc.course_id, c.course_code, c.course_name, now() AS archived_at
  FROM public.student_courses sc
  JOIN public.courses c ON c.id = sc.course_id;

CREATE TABLE public.student_course_legacy_v3_rollback_archive AS
SELECT b.*, now() AS archived_at
  FROM public.student_course_legacy_v3 b;

ALTER TABLE public.student_courses_v3_rollback_archive        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_course_legacy_v3_rollback_archive  ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.student_courses_v3_rollback_archive,
                    public.student_course_legacy_v3_rollback_archive FROM anon, authenticated;

-- 3. Restore legacy values V3 cleared; never overwrite a non-empty value.
UPDATE public.students s
   SET course = b.original_value
  FROM public.student_course_legacy_v3 b
 WHERE b.student_id = s.id
   AND b.outcome = 'MAPPED'
   AND s.course IS NULL;

-- 4. Archive completeness check, then remove the V3 tables.
DO $$
BEGIN
    IF (SELECT count(*) FROM public.student_courses_v3_rollback_archive)
       <> (SELECT count(*) FROM public.student_courses)
       OR (SELECT count(*) FROM public.student_course_legacy_v3_rollback_archive)
       <> (SELECT count(*) FROM public.student_course_legacy_v3) THEN
        RAISE EXCEPTION 'Aborted: archive copies are incomplete. Nothing was changed.';
    END IF;
END
$$;

DROP TABLE public.student_courses;
DROP TABLE public.student_course_legacy_v3;

COMMIT;
