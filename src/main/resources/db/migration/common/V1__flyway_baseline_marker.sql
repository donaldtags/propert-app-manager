-- Marks the point where Flyway starts tracking this database.
-- Everything before this (the legacy schema) was created by Hibernate ddl-auto=update and is
-- still maintained that way; see common/FlywayAfterHibernateConfig.java. Later migrations hold
-- versioned data fixes, constraint repairs and indexes. Statements in db/migration/common must
-- run unchanged on PostgreSQL, MariaDB and H2 (PostgreSQL mode); anything vendor-specific goes in
-- db/migration/{postgresql,mariadb,h2} with a version number unique across all folders.
SELECT 1;
