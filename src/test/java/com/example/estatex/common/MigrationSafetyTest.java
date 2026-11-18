package com.example.estatex.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** See {@link MigrationGuard}. The same rules also run at startup against the classpath. */
class MigrationSafetyTest {
    private static final Path ROOT = Path.of("src/main/resources/db/migration");

    @Test
    void sourceMigrationsPassTheGuard() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                problems.addAll(MigrationGuard.problems(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void classpathMigrationsPassTheGuard() throws IOException {
        List<String> problems = MigrationGuard.classpathProblems();
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theGeneratedDiffThatWouldHaveDroppedUserRolesIsRejected() {
        String generated = """
                ALTER TABLE app_user_roles
                DROP
                FOREIGN KEY FKcbkg2c0sxfvw6vfl0fk17eo7c;

                ALTER TABLE properties
                    ADD organisation_id BIGINT NULL;

                DROP TABLE app_user_roles;""";
        List<String> problems = MigrationGuard.problems("V3__.sql", generated);
        assertEquals(2, problems.size(), String.join("\n", problems));
        assertTrue(problems.get(1).contains("DROP TABLE app_user_roles"));
    }

    @Test
    void aReviewedDestructiveStatementIsAllowed() {
        assertTrue(MigrationGuard.problems("V9__drop_legacy_table.sql", """
                -- reviewed-destructive: legacy_import was emptied and replaced by imports in V8
                DROP TABLE legacy_import;""").isEmpty());
    }
}
