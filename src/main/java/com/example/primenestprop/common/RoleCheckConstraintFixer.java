package com.example.primenestprop.common;

import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hibernate's schema generator fixes the shape of every column backing the UserRole enum - a
 * CHECK constraint on Postgres, a native {@code ENUM(...)} type listing out the role names on
 * MariaDB/MySQL - to whichever UserRole values existed when the table was first created.
 * {@code ddl-auto=update} never widens any of these when a new role enum constant is added later
 * (e.g. SERVICE_PROVIDER), so on any database that already had the tables, inserting the newer
 * role fails - a Postgres check-violation, or a MariaDB/MySQL "Data truncated for column" - even
 * though the application code has no idea any of this schema exists. Two tables carry a
 * UserRole-shaped column: the roles lookup table itself, and {@code AppUser.roles}'s element
 * collection table. Every fix below is safe to run on every boot and a no-op on a database where
 * it doesn't apply or is already applied. */
@Component
@Order(1)
class RoleCheckConstraintFixer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(RoleCheckConstraintFixer.class);
    private final JdbcTemplate jdbc;

    RoleCheckConstraintFixer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        dropPostgresCheckConstraint();
        widenMariaDbColumn("roles", "name", "NOT NULL");
        widenMariaDbColumn("app_user_roles", "roles", "DEFAULT NULL");
    }

    private void dropPostgresCheckConstraint() {
        try {
            // Postgres needs an ACCESS EXCLUSIVE lock to drop a constraint; without a timeout,
            // any lingering connection from a prior instance (e.g. mid-shutdown) can block this
            // indefinitely and hang the whole app startup. Fail fast instead - next boot retries.
            // Both statements must run on the same connection for the lock_timeout to apply.
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
                try (Statement st = con.createStatement()) {
                    st.execute("SET lock_timeout = '5s'");
                    st.execute("ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_check");
                }
                return null;
            });
        } catch (Exception e) {
            log.debug("Skipping roles_name_check drop (not applicable, or lock unavailable, on this database): {}", e.getMessage());
        }
    }

    /** Converts a MariaDB/MySQL native {@code ENUM(...)} role column to a plain VARCHAR, so it
     * accepts any current or future UserRole value instead of only the ones baked in when the
     * table was first created. {@code table}/{@code column}/{@code nullability} are always
     * literal constants from the call sites above, never external input. */
    private void widenMariaDbColumn(String table, String column, String nullability) {
        try {
            // MariaDB/MySQL's equivalent of Postgres's lock_timeout: bound how long the ALTER
            // waits for a metadata lock from another connection, instead of hanging forever.
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
                try (Statement st = con.createStatement()) {
                    st.execute("SET SESSION lock_wait_timeout = 5");
                    st.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column + " VARCHAR(64) " + nullability);
                }
                return null;
            });
        } catch (Exception e) {
            log.debug("Skipping {}.{} column widening (not applicable, or lock unavailable, on this database): {}",
                    table, column, e.getMessage());
        }
    }
}