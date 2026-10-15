-- Read-only verification for V1 (refresh_tokens) and V2 (secure existing tables).
-- Only reads system catalogs and row counts; changes nothing. Safe to run any
-- time, e.g. in the Supabase SQL editor. Every check has a "result" column:
-- PASS means the expected state was found.

-- 0. Row counts. Run before AND after the migrations: the numbers must match,
--    because V1 and V2 do not add, change, or delete any existing row.
SELECT 'users'    AS table_name, count(*) AS row_count FROM public.users
UNION ALL
SELECT 'students', count(*) FROM public.students
UNION ALL
SELECT 'courses',  count(*) FROM public.courses;

-- 1. RLS is enabled on all four application tables, and they are owned by postgres.
SELECT c.relname                        AS table_name,
       pg_get_userbyid(c.relowner)      AS owner,
       c.relrowsecurity                 AS rls_enabled,
       c.relforcerowsecurity            AS rls_forced,
       CASE WHEN c.relrowsecurity AND pg_get_userbyid(c.relowner) = 'postgres'
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public'
   AND c.relkind = 'r'
   AND c.relname IN ('users', 'students', 'courses', 'refresh_tokens')
 ORDER BY c.relname;
-- Expect 4 rows, all PASS (fewer rows means a table is missing).

-- 2. anon and authenticated hold no privileges on these tables.
--    has_table_privilege also accounts for privileges inherited via PUBLIC.
SELECT t.table_name,
       r.role_name,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'SELECT')     AS can_select,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'INSERT')     AS can_insert,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'UPDATE')     AS can_update,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'DELETE')     AS can_delete,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'TRUNCATE')   AS can_truncate,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'REFERENCES') AS can_reference,
       has_table_privilege(r.role_name, 'public.' || t.table_name, 'TRIGGER')    AS can_trigger,
       CASE WHEN has_table_privilege(r.role_name, 'public.' || t.table_name,
                     'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
            THEN 'FAIL' ELSE 'PASS' END AS result
  FROM (VALUES ('users'), ('students'), ('courses'), ('refresh_tokens')) AS t(table_name)
 CROSS JOIN (VALUES ('anon'), ('authenticated')) AS r(role_name)
 WHERE to_regclass('public.' || t.table_name) IS NOT NULL
 ORDER BY t.table_name, r.role_name;
-- Expect 8 rows, every privilege column false, all PASS.

-- 2b. Explicit grants remaining for those roles (should return no rows).
SELECT table_name, grantee, privilege_type
  FROM information_schema.role_table_grants
 WHERE table_schema = 'public'
   AND table_name IN ('users', 'students', 'courses', 'refresh_tokens')
   AND grantee IN ('anon', 'authenticated')
 ORDER BY table_name, grantee, privilege_type;

-- 3. Spring Boot access is preserved: postgres bypasses RLS (and owns the tables, see check 1).
SELECT rolname,
       rolbypassrls,
       CASE WHEN rolbypassrls THEN 'PASS' ELSE 'FAIL' END AS result
  FROM pg_roles
 WHERE rolname = 'postgres';

-- 4. refresh_tokens columns match the RefreshToken entity.
SELECT e.column_name,
       c.data_type,
       c.character_maximum_length,
       c.datetime_precision,
       c.is_nullable,
       CASE WHEN c.column_name IS NOT NULL
             AND c.data_type = e.data_type
             AND c.is_nullable = e.is_nullable
             AND (e.max_length IS NULL OR c.character_maximum_length = e.max_length)
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM (VALUES
          ('id',         'bigint',                   'NO',  NULL::int),
          ('user_id',    'bigint',                   'NO',  NULL),
          ('token_hash', 'character varying',        'NO',  64),
          ('family_id',  'character varying',        'NO',  36),
          ('persistent', 'boolean',                  'NO',  NULL),
          ('created_at', 'timestamp with time zone', 'NO',  NULL),
          ('expires_at', 'timestamp with time zone', 'NO',  NULL),
          ('revoked_at', 'timestamp with time zone', 'YES', NULL)
       ) AS e(column_name, data_type, is_nullable, max_length)
  LEFT JOIN information_schema.columns c
         ON c.table_schema = 'public'
        AND c.table_name = 'refresh_tokens'
        AND c.column_name = e.column_name
 ORDER BY e.column_name;
-- Expect 8 rows, all PASS.

-- 5. refresh_tokens constraints: primary key, unique token_hash, and a foreign
--    key to public.users that cascades on delete.
SELECT con.conname                       AS constraint_name,
       CASE con.contype WHEN 'p' THEN 'PRIMARY KEY'
                        WHEN 'u' THEN 'UNIQUE'
                        WHEN 'f' THEN 'FOREIGN KEY' END AS constraint_type,
       pg_get_constraintdef(con.oid)     AS definition,
       CASE
         WHEN con.contype = 'p' AND pg_get_constraintdef(con.oid) = 'PRIMARY KEY (id)' THEN 'PASS'
         WHEN con.contype = 'u' AND pg_get_constraintdef(con.oid) = 'UNIQUE (token_hash)' THEN 'PASS'
         WHEN con.contype = 'f' AND con.confrelid = 'public.users'::regclass
                                AND con.confdeltype = 'c' THEN 'PASS'
         ELSE 'CHECK'
       END                               AS result
  FROM pg_constraint con
 WHERE con.conrelid = to_regclass('public.refresh_tokens')
   AND con.contype IN ('p', 'u', 'f')
 ORDER BY con.contype;
-- Expect 3 rows (FOREIGN KEY, PRIMARY KEY, UNIQUE), all PASS.

-- 6. refresh_tokens indexes.
SELECT indexname, indexdef
  FROM pg_indexes
 WHERE schemaname = 'public'
   AND tablename = 'refresh_tokens'
 ORDER BY indexname;
-- Expect idx_refresh_tokens_family_id, idx_refresh_tokens_user_id, the primary
-- key index, and the unique index on token_hash.

-- 7. No RLS policies exist on these tables (none are needed; the app bypasses
--    RLS and the API roles should see nothing). Should return no rows.
SELECT schemaname, tablename, policyname, roles, cmd
  FROM pg_policies
 WHERE schemaname = 'public'
   AND tablename IN ('users', 'students', 'courses', 'refresh_tokens');
