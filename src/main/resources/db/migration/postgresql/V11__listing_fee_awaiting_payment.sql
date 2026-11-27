-- PropertyBillingStatus gained AWAITING_PAYMENT (listing fees are now paid through checkout, not
-- self-confirmed). Hibernate created property_billings.status with a CHECK constraint listing the
-- enum values at the time, which ddl-auto=update never widens.
SET lock_timeout = '5s';
ALTER TABLE property_billings DROP CONSTRAINT IF EXISTS property_billings_status_check;
