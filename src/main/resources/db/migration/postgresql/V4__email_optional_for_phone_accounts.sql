-- Accounts can sign up with only a phone number. Hibernate's ddl-auto=update never relaxes an
-- existing NOT NULL, so do it here. The unique index stays: Postgres allows many NULLs in it.
SET lock_timeout = '5s';
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
