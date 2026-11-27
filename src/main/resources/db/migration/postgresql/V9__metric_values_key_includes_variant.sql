-- metric_values gained 'variant' (FULL / TO_DATE) in Phase C, and its unique key must include it.
-- ddl-auto=update never changes an existing key, so a database that ran Phase B kept the old key
-- and rejects the like-for-like rows. Rebuild it; on a fresh database this just recreates it.
-- On Postgres Hibernate creates the key as a UNIQUE CONSTRAINT (which owns an index of the same
-- name), so the constraint must be dropped; DROP INDEX alone fails. (Fixed 2026-11-27 before this
-- migration ever ran on a Postgres database.)
SET lock_timeout = '5s';
ALTER TABLE metric_values DROP CONSTRAINT IF EXISTS uq_metric_value;
DROP INDEX IF EXISTS uq_metric_value;
ALTER TABLE metric_values ADD CONSTRAINT uq_metric_value UNIQUE (metric_key, grain, period_start, variant, dims_key);
