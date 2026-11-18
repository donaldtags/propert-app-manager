-- PaymentPurpose gained STAY_BOOKING. On MariaDB Hibernate created payments.purpose_type as a
-- native ENUM(...) of the values at the time; make it a plain VARCHAR so new values insert.
ALTER TABLE payments MODIFY COLUMN purpose_type VARCHAR(40);
