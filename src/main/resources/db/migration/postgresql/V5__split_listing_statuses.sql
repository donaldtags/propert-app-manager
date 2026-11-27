-- PropertyStatus: SOLD becomes REPORTED_SOLD (no sale was ever independently verified), RESERVED
-- becomes UNDER_OFFER, and WITHDRAWN and EXPIRED are new. Hibernate created properties.status
-- with a CHECK constraint listing the old values, which ddl-auto=update never widens.
SET lock_timeout = '5s';
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;
UPDATE properties SET status = 'REPORTED_SOLD' WHERE status = 'SOLD';
UPDATE properties SET status = 'UNDER_OFFER' WHERE status = 'RESERVED';
