-- properties.latitude/longitude were created as numeric(38,2) (Hibernate's default for BigDecimal),
-- which rounded every map pin to ~1 km. Keep 7 decimal places like locations, property_events and
-- listing_daily_snapshots. Values already rounded can't be recovered.
SET lock_timeout = '5s';
ALTER TABLE properties ALTER COLUMN latitude TYPE numeric(10,7), ALTER COLUMN longitude TYPE numeric(10,7);
