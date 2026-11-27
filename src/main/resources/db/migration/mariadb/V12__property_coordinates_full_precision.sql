-- properties.latitude/longitude were created as DECIMAL(38,2) (Hibernate's default for BigDecimal),
-- which rounded every map pin to ~1 km. Keep 7 decimal places like locations, property_events and
-- listing_daily_snapshots. Values already rounded can't be recovered.
ALTER TABLE properties MODIFY COLUMN latitude DECIMAL(10,7), MODIFY COLUMN longitude DECIMAL(10,7);
