-- PaymentPurpose gained STAY_BOOKING. Hibernate created payments.purpose_type with a CHECK
-- constraint listing the enum values at the time, which ddl-auto=update never widens.
SET lock_timeout = '5s';
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_purpose_type_check;
