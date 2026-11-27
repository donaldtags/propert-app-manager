-- H2 is only used by tests with create-drop: properties.status is created as an ENUM of the
-- current values, so no old SOLD/RESERVED rows can exist (and H2 rejects comparing with them).
-- Kept so version numbers line up across vendors.
SELECT 1;
