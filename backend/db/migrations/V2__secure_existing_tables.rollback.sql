-- Rollback for V2__secure_existing_tables.sql.
-- WARNING: restores the previous, insecure state. The anon and authenticated
-- roles regain full access to these tables through Supabase's Data API,
-- including password hashes in public.users. Run only deliberately.

BEGIN;

ALTER TABLE public.users    DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.students DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.courses  DISABLE ROW LEVEL SECURITY;

GRANT ALL ON TABLE public.users, public.students, public.courses TO anon, authenticated;

COMMIT;
