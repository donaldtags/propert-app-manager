-- UserRole gained the staff roles (FINANCE_ADMIN, MARKET_ANALYST, OPERATIONS_ADMIN,
-- VERIFICATION_OFFICER). RoleCheckConstraintFixer drops roles_name_check at startup; this drops the
-- matching CHECK on the users' role collection table, which ddl-auto=update never widens.
SET lock_timeout = '5s';
ALTER TABLE app_user_roles DROP CONSTRAINT IF EXISTS app_user_roles_roles_check;
