-- Read-only verification for V3__student_courses.sql. Changes nothing.
-- Every check has a "result" column: PASS means the expected state was found.
--
-- Checks 8, 11 and 12 describe the state IMMEDIATELY AFTER V3. Once
-- administrators edit assignments or the catalog, they can legitimately
-- differ; run them right after the migration.

-- 0. Row counts. Run before AND after V3: the numbers must match, because V3
--    adds no students or courses and deletes nothing.
SELECT 'students' AS table_name, count(*) AS row_count FROM public.students
UNION ALL
SELECT 'courses', count(*) FROM public.courses;

-- 1. New tables exist as ordinary tables, are owned by postgres, and have RLS enabled.
SELECT t.table_name,
       c.relkind,
       pg_get_userbyid(c.relowner)      AS owner,
       c.relrowsecurity                 AS rls_enabled,
       CASE WHEN c.relkind = 'r' AND c.relrowsecurity AND pg_get_userbyid(c.relowner) = 'postgres'
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM (VALUES ('student_courses'), ('student_course_legacy_v3')) AS t(table_name)
  LEFT JOIN pg_class c ON c.oid = to_regclass('public.' || t.table_name);
-- Expect 2 rows, both PASS.

-- 2. anon and authenticated hold no table-level or column-level privileges on
--    the new tables (including privileges inherited through PUBLIC).
SELECT t.table_name,
       r.role_name,
       has_table_privilege(r.role_name, 'public.' || t.table_name,
           'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')      AS any_table_privilege,
       has_any_column_privilege(r.role_name, 'public.' || t.table_name,
           'SELECT, INSERT, UPDATE, REFERENCES')                                 AS any_column_privilege,
       CASE WHEN has_table_privilege(r.role_name, 'public.' || t.table_name,
                     'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
              OR has_any_column_privilege(r.role_name, 'public.' || t.table_name,
                     'SELECT, INSERT, UPDATE, REFERENCES')
            THEN 'FAIL' ELSE 'PASS' END AS result
  FROM (VALUES ('student_courses'), ('student_course_legacy_v3')) AS t(table_name)
 CROSS JOIN (VALUES ('anon'), ('authenticated')) AS r(role_name)
 WHERE to_regclass('public.' || t.table_name) IS NOT NULL;
-- Expect 4 rows, all PASS.

-- 2b. Informational: other roles with access. service_role is expected here on
--     Supabase (server-side key, bypasses RLS); anything else deserves a look.
SELECT table_name, grantee, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privileges
  FROM information_schema.role_table_grants
 WHERE table_schema = 'public'
   AND table_name IN ('student_courses', 'student_course_legacy_v3')
   AND grantee NOT IN ('postgres', 'anon', 'authenticated')
 GROUP BY table_name, grantee
 ORDER BY table_name, grantee;

-- 3. Columns: exact names, types and nullability (and no unexpected columns).
WITH expected(table_name, column_name, data_type, is_nullable) AS (VALUES
    ('student_courses',          'student_id',       'bigint',                   'NO'),
    ('student_courses',          'course_id',        'bigint',                   'NO'),
    ('student_course_legacy_v3', 'student_id',       'bigint',                   'NO'),
    ('student_course_legacy_v3', 'original_value',   'text',                     'NO'),
    ('student_course_legacy_v3', 'outcome',          'character varying',        'NO'),
    ('student_course_legacy_v3', 'match_count',      'integer',                  'NO'),
    ('student_course_legacy_v3', 'mapped_course_id', 'bigint',                   'YES'),
    ('student_course_legacy_v3', 'backed_up_at',     'timestamp with time zone', 'NO')
),
actual AS (
    SELECT table_name, column_name, data_type, is_nullable
      FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name IN ('student_courses', 'student_course_legacy_v3')
)
SELECT coalesce(e.table_name, a.table_name)   AS table_name,
       coalesce(e.column_name, a.column_name) AS column_name,
       a.data_type,
       a.is_nullable,
       CASE WHEN e.column_name IS NULL THEN 'FAIL (unexpected column)'
            WHEN a.column_name IS NULL THEN 'FAIL (missing column)'
            WHEN a.data_type = e.data_type AND a.is_nullable = e.is_nullable THEN 'PASS'
            ELSE 'FAIL' END AS result
  FROM expected e
  FULL JOIN actual a ON a.table_name = e.table_name AND a.column_name = e.column_name
 ORDER BY 1, 2;
-- Expect 8 rows, all PASS.

-- 4. Foreign keys: exact source column, target table and column, delete
--    action, ON UPDATE NO ACTION, not deferrable; no unexpected foreign keys.
WITH expected(table_name, source_columns, target_table, target_columns, on_delete) AS (VALUES
    ('student_courses',          '{student_id}', 'students', '{id}', 'CASCADE'),
    ('student_courses',          '{course_id}',  'courses',  '{id}', 'RESTRICT'),
    ('student_course_legacy_v3', '{student_id}', 'students', '{id}', 'CASCADE')
),
actual AS (
    SELECT rel.relname AS table_name,
           (SELECT array_agg(a.attname::text ORDER BY k.ord)
              FROM unnest(con.conkey) WITH ORDINALITY AS k(attnum, ord)
              JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum)::text  AS source_columns,
           refns.nspname AS target_schema,
           ref.relname   AS target_table,
           (SELECT array_agg(a.attname::text ORDER BY k.ord)
              FROM unnest(con.confkey) WITH ORDINALITY AS k(attnum, ord)
              JOIN pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = k.attnum)::text AS target_columns,
           CASE con.confdeltype WHEN 'c' THEN 'CASCADE' WHEN 'r' THEN 'RESTRICT' WHEN 'a' THEN 'NO ACTION'
                                WHEN 'n' THEN 'SET NULL' WHEN 'd' THEN 'SET DEFAULT' END                 AS on_delete,
           con.confupdtype,
           con.condeferrable,
           con.conname
      FROM pg_constraint con
      JOIN pg_class rel       ON rel.oid = con.conrelid
      JOIN pg_namespace ns    ON ns.oid = rel.relnamespace
      JOIN pg_class ref       ON ref.oid = con.confrelid
      JOIN pg_namespace refns ON refns.oid = ref.relnamespace
     WHERE con.contype = 'f'
       AND ns.nspname = 'public'
       AND rel.relname IN ('student_courses', 'student_course_legacy_v3')
)
SELECT coalesce(e.table_name, a.table_name)         AS table_name,
       coalesce(e.source_columns, a.source_columns) AS source_columns,
       a.target_schema || '.' || a.target_table     AS references_table,
       a.target_columns,
       a.on_delete,
       a.conname,
       CASE WHEN e.table_name IS NULL THEN 'FAIL (unexpected foreign key)'
            WHEN a.table_name IS NULL THEN 'FAIL (missing foreign key)'
            WHEN a.target_schema = 'public' AND a.on_delete = e.on_delete
             AND a.confupdtype = 'a' AND NOT a.condeferrable THEN 'PASS'
            ELSE 'FAIL' END AS result
  FROM expected e
  FULL JOIN actual a
         ON a.table_name = e.table_name AND a.source_columns = e.source_columns
        AND a.target_table = e.target_table AND a.target_columns = e.target_columns
 ORDER BY 1, 2;
-- Expect 3 rows, all PASS.

-- 5. Other constraints: exact primary keys, three CHECKs on the backup table,
--    and no unique or exclusion constraints beyond the primary keys.
SELECT 'student_courses primary key' AS item,
       CASE WHEN (SELECT pg_get_constraintdef(oid) FROM pg_constraint
                   WHERE conrelid = to_regclass('public.student_courses') AND contype = 'p')
                 = 'PRIMARY KEY (student_id, course_id)' THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT 'student_course_legacy_v3 primary key',
       CASE WHEN (SELECT pg_get_constraintdef(oid) FROM pg_constraint
                   WHERE conrelid = to_regclass('public.student_course_legacy_v3') AND contype = 'p')
                 = 'PRIMARY KEY (student_id)' THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'student_course_legacy_v3 check constraints = 3',
       CASE WHEN (SELECT count(*) FROM pg_constraint
                   WHERE conrelid = to_regclass('public.student_course_legacy_v3') AND contype = 'c') = 3
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'no unique/exclusion constraints besides primary keys',
       CASE WHEN NOT EXISTS (SELECT 1 FROM pg_constraint
                              WHERE conrelid IN (to_regclass('public.student_courses'),
                                                 to_regclass('public.student_course_legacy_v3'))
                                AND contype IN ('u', 'x'))
            THEN 'PASS' ELSE 'FAIL' END;
-- Expect 4 rows, all PASS.

-- 6. Indexes on student_courses: the primary key index and exactly one plain
--    btree index on (course_id). Expression and partial indexes fail.
WITH expected(index_name, columns, is_unique) AS (VALUES
    ('student_courses_pkey',          '{student_id,course_id}', true),
    ('idx_student_courses_course_id', '{course_id}',            false)
),
actual AS (
    SELECT i.relname AS index_name,
           am.amname,
           (SELECT array_agg(a.attname::text ORDER BY k.ord)
              FROM unnest(ix.indkey::int2[]) WITH ORDINALITY AS k(attnum, ord)
              JOIN pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = k.attnum)::text AS columns,
           ix.indisunique,
           ix.indpred IS NULL AND ix.indexprs IS NULL AS plain
      FROM pg_index ix
      JOIN pg_class i ON i.oid = ix.indexrelid
      JOIN pg_am am   ON am.oid = i.relam
     WHERE ix.indrelid = to_regclass('public.student_courses')
)
SELECT coalesce(e.index_name, a.index_name) AS index_name,
       a.amname,
       a.columns,
       CASE WHEN e.index_name IS NULL THEN 'CHECK (extra index)'
            WHEN a.index_name IS NULL THEN 'FAIL (missing index)'
            WHEN a.amname = 'btree' AND a.columns = e.columns AND a.indisunique = e.is_unique AND a.plain
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM expected e
  FULL JOIN actual a ON a.index_name = e.index_name
 ORDER BY 1;
-- Expect 2 rows, both PASS. (If PostgreSQL named the primary key index
-- differently, the row shows FAIL/CHECK: compare the columns manually.)

-- 7. Backfill outcome summary (compare with the preview taken before V3) and
--    internal consistency of every backup row.
SELECT outcome,
       count(*)                                                     AS students,
       count(*) FILTER (WHERE NOT (
             (outcome = 'UNMATCHED' AND match_count = 0  AND mapped_course_id IS NULL)
          OR (outcome = 'MAPPED'    AND match_count = 1  AND mapped_course_id IS NOT NULL)
          OR (outcome = 'AMBIGUOUS' AND match_count >= 2 AND mapped_course_id IS NULL))) AS inconsistent,
       CASE WHEN count(*) FILTER (WHERE NOT (
             (outcome = 'UNMATCHED' AND match_count = 0  AND mapped_course_id IS NULL)
          OR (outcome = 'MAPPED'    AND match_count = 1  AND mapped_course_id IS NOT NULL)
          OR (outcome = 'AMBIGUOUS' AND match_count >= 2 AND mapped_course_id IS NULL))) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM public.student_course_legacy_v3
 GROUP BY outcome
 ORDER BY outcome;

-- 8. [Immediately after V3] Every MAPPED value has its assignment and its
--    legacy text was cleared.
SELECT count(*) AS mapped_problems,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
  FROM public.student_course_legacy_v3 b
  JOIN public.students s ON s.id = b.student_id
 WHERE b.outcome = 'MAPPED'
   AND (s.course IS NOT NULL
        OR NOT EXISTS (SELECT 1 FROM public.student_courses sc
                        WHERE sc.student_id = b.student_id AND sc.course_id = b.mapped_course_id));

-- 9. UNMATCHED and AMBIGUOUS values are preserved exactly in students.course.
--    (The application never writes this column, so this stays PASS.)
SELECT b.outcome,
       count(*) FILTER (WHERE s.course = b.original_value)                AS preserved,
       count(*) FILTER (WHERE s.course IS DISTINCT FROM b.original_value) AS changed,
       CASE WHEN count(*) FILTER (WHERE s.course IS DISTINCT FROM b.original_value) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM public.student_course_legacy_v3 b
  JOIN public.students s ON s.id = b.student_id
 WHERE b.outcome IN ('UNMATCHED', 'AMBIGUOUS')
 GROUP BY b.outcome
 ORDER BY b.outcome;
-- Expect PASS for each outcome present (no rows if there were none).

-- 10. No candidate legacy value was left without a backup.
SELECT count(*) AS unbacked_legacy_values,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
  FROM public.students s
 WHERE s.course IS NOT NULL AND trim(s.course) <> ''
   AND NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b WHERE b.student_id = s.id);

-- 11. [Immediately after V3] The assignment set equals the MAPPED backups:
--     nothing missing, nothing extra.
SELECT (SELECT count(*) FROM public.student_course_legacy_v3 b
         WHERE b.outcome = 'MAPPED'
           AND NOT EXISTS (SELECT 1 FROM public.student_courses sc
                            WHERE sc.student_id = b.student_id AND sc.course_id = b.mapped_course_id)) AS missing,
       (SELECT count(*) FROM public.student_courses sc
         WHERE NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                            WHERE b.outcome = 'MAPPED' AND b.student_id = sc.student_id
                              AND b.mapped_course_id = sc.course_id))                              AS extra,
       CASE WHEN NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                              WHERE b.outcome = 'MAPPED'
                                AND NOT EXISTS (SELECT 1 FROM public.student_courses sc
                                                 WHERE sc.student_id = b.student_id
                                                   AND sc.course_id = b.mapped_course_id))
             AND NOT EXISTS (SELECT 1 FROM public.student_courses sc
                              WHERE NOT EXISTS (SELECT 1 FROM public.student_course_legacy_v3 b
                                                 WHERE b.outcome = 'MAPPED' AND b.student_id = sc.student_id
                                                   AND b.mapped_course_id = sc.course_id))
            THEN 'PASS' ELSE 'FAIL' END AS result;

-- 12. [Immediately after V3] Re-applying MATCH RULE v1 (identical to V3 and
--     the preview) to the backed-up values gives the recorded outcomes.
WITH legacy AS (
    SELECT b.student_id, b.outcome, b.mapped_course_id, lower(trim(b.original_value)) AS match_key
      FROM public.student_course_legacy_v3 b
),
matches AS (
    SELECT l.student_id, count(DISTINCT c.id) AS match_count, min(c.id) AS course_id
      FROM legacy l
      LEFT JOIN public.courses c
        ON lower(trim(c.course_code)) = l.match_key
        OR lower(trim(c.course_name)) = l.match_key
     GROUP BY l.student_id
)
SELECT count(*) FILTER (WHERE
             l.outcome IS DISTINCT FROM CASE WHEN m.match_count = 0 THEN 'UNMATCHED'
                                             WHEN m.match_count = 1 THEN 'MAPPED'
                                             ELSE 'AMBIGUOUS' END
          OR l.mapped_course_id IS DISTINCT FROM CASE WHEN m.match_count = 1 THEN m.course_id END) AS differing,
       CASE WHEN count(*) FILTER (WHERE
             l.outcome IS DISTINCT FROM CASE WHEN m.match_count = 0 THEN 'UNMATCHED'
                                             WHEN m.match_count = 1 THEN 'MAPPED'
                                             ELSE 'AMBIGUOUS' END
          OR l.mapped_course_id IS DISTINCT FROM CASE WHEN m.match_count = 1 THEN m.course_id END) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
  FROM legacy l
  JOIN matches m ON m.student_id = l.student_id;

-- 13. No duplicate assignments (guaranteed by the primary key; checked anyway).
SELECT count(*) AS duplicate_pairs,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
  FROM (SELECT student_id, course_id FROM public.student_courses
         GROUP BY student_id, course_id HAVING count(*) > 1) d;

-- 14. No orphaned assignments (guaranteed by the foreign keys; checked anyway).
SELECT count(*) AS orphaned_assignments,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result
  FROM public.student_courses sc
 WHERE NOT EXISTS (SELECT 1 FROM public.students s WHERE s.id = sc.student_id)
    OR NOT EXISTS (SELECT 1 FROM public.courses  c WHERE c.id = sc.course_id);
