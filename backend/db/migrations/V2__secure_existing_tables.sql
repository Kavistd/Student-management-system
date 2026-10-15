-- V2: lock the existing application tables away from Supabase's Data API.
--
-- Before this migration, public.users, public.students and public.courses had
-- RLS disabled and the anon and authenticated roles held every table privilege
-- (SELECT, INSERT, UPDATE, DELETE, TRUNCATE, ...). Anyone with the project's
-- anon key could therefore read password hashes or change and delete records.
--
-- This migration:
--   * enables Row Level Security (with no policies) on the three tables, and
--   * revokes all table privileges from anon and authenticated.
--
-- It does NOT add, change, or delete any row, column, constraint, index, or
-- table. It only changes security settings.
--
-- Spring Boot connects as postgres, which owns these tables and has the
-- BYPASSRLS attribute, so its access is unaffected. The guard below verifies
-- that and aborts without changing anything if it is not true.
--
-- Safe to re-run. Rollback: V2__secure_existing_tables.rollback.sql
-- Verify:   ../verify/verify_security.sql

BEGIN;

DO $$
DECLARE
    owned_tables INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres' AND rolbypassrls) THEN
        RAISE EXCEPTION
            'Aborted: role postgres lacks BYPASSRLS, so enabling RLS could block the Spring Boot application. Nothing was changed.';
    END IF;

    SELECT count(*)
      INTO owned_tables
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'public'
       AND c.relkind = 'r'
       AND c.relname IN ('users', 'students', 'courses')
       AND pg_get_userbyid(c.relowner) = 'postgres';

    IF owned_tables <> 3 THEN
        RAISE EXCEPTION
            'Aborted: expected public.users, public.students and public.courses to exist and be owned by postgres (found %). Nothing was changed.',
            owned_tables;
    END IF;
END
$$;

ALTER TABLE public.users    ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses  ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.users, public.students, public.courses FROM anon, authenticated;

COMMIT;
