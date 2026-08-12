package com.example.primenestprop.common;

import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Hibernate's schema generator adds a CHECK constraint on the user roles collection table's
 * `name` column, listing whichever UserRole values existed when the table was first created.
 * {@code ddl-auto=update} never widens that constraint when a new role enum constant is added
 * later, so on any database that already had the table, inserting a newer role (e.g.
 * SERVICE_PROVIDER) fails with a check-violation - even though the application code has no idea
 * the constraint exists. Drops it once so the column is governed by the Java enum going forward;
 * safe to run on every boot, and a no-op on databases where it's already gone or never existed. */
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
}