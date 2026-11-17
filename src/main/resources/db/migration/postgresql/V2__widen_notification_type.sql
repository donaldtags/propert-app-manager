-- NotificationType gained LISTING_COMPLIANCE. Hibernate created notifications.type with a CHECK
-- constraint listing the enum values at the time, which ddl-auto=update never widens.
SET lock_timeout = '5s';
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
