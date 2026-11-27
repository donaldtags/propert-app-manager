-- metric_values gained 'variant' (FULL / TO_DATE) in Phase C, and its unique key must include it.
-- ddl-auto=update never changes an existing index, so a database that ran Phase B kept the old
-- key and rejects the like-for-like rows. Rebuild it; on a fresh database this just recreates it.
DROP INDEX IF EXISTS uq_metric_value ON metric_values;
CREATE UNIQUE INDEX uq_metric_value ON metric_values (metric_key, grain, period_start, variant, dims_key);
