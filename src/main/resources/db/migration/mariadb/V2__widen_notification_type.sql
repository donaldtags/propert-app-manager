-- NotificationType gained LISTING_COMPLIANCE. On MariaDB Hibernate created notifications.type as a
-- native ENUM(...) of the values at the time; make it a plain VARCHAR so new values insert.
ALTER TABLE notifications MODIFY COLUMN type VARCHAR(40) NOT NULL;
