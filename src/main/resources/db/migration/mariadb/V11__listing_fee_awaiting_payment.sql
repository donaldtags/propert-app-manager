-- PropertyBillingStatus gained AWAITING_PAYMENT (listing fees are now paid through checkout, not
-- self-confirmed). Hibernate created property_billings.status as a native ENUM(...) of the values
-- at the time; make it a plain VARCHAR so new values insert.
ALTER TABLE property_billings MODIFY COLUMN status VARCHAR(30);
