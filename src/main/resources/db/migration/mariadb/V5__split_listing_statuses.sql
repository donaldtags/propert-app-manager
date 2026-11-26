-- PropertyStatus: SOLD becomes REPORTED_SOLD (no sale was ever independently verified), RESERVED
-- becomes UNDER_OFFER, and WITHDRAWN and EXPIRED are new. Hibernate created properties.status as
-- a native ENUM of the old values; make it a plain VARCHAR so the new values insert.
ALTER TABLE properties MODIFY COLUMN status VARCHAR(20) NOT NULL;
UPDATE properties SET status = 'REPORTED_SOLD' WHERE status = 'SOLD';
UPDATE properties SET status = 'UNDER_OFFER' WHERE status = 'RESERVED';
