-- See the postgresql/mariadb versions. H2 test databases are created fresh, so this is a no-op rebuild.
DROP INDEX IF EXISTS uq_metric_value;
CREATE UNIQUE INDEX uq_metric_value ON metric_values (metric_key, grain, period_start, variant, dims_key);
