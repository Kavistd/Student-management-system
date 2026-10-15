-- Rollback for V1__create_refresh_tokens.sql.
-- Destructive: drops every stored refresh token, which signs out all users.
-- Run only deliberately.

BEGIN;
DROP TABLE IF EXISTS refresh_tokens;
COMMIT;
