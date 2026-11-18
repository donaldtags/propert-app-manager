package com.example.estatex.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Refuses to run migrations that look like an auto-generated schema diff. Hibernate still owns
 * table shapes (see {@link FlywayAfterHibernateConfig}), so a generated diff tends to re-add
 * columns Hibernate already created and DROP tables it didn't recognise, such as app_user_roles.
 * Checked at startup against what's actually on the classpath (which catches stale copies left
 * in target/classes after the source file was deleted) and in tests against src.
 *
 * <p>Rules: file names must be {@code V<n>__what_it_does.sql}; DROP TABLE / DROP COLUMN /
 * TRUNCATE / DELETE FROM need {@code -- reviewed-destructive: <reason>} on the line above.
 */
public final class MigrationGuard {
    private static final Pattern NAME = Pattern.compile("V\\d+__[a-z0-9_]+\\.sql");
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(DROP\\s+TABLE|DROP\\s+COLUMN|TRUNCATE|DELETE\\s+FROM)\\b", Pattern.CASE_INSENSITIVE);

    private MigrationGuard() {
    }

    public static List<String> problems(String name, String sql) {
        List<String> problems = new ArrayList<>();
        if (!NAME.matcher(name).matches()) {
            problems.add(name + ": name must look like V<n>__what_it_does.sql");
        }
        String[] lines = sql.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String code = lines[i].replaceAll("--.*$", "");
            boolean reviewed = i > 0 && lines[i - 1].toLowerCase(Locale.ROOT).contains("-- reviewed-destructive:");
            if (DESTRUCTIVE.matcher(code).find() && !reviewed) {
                problems.add(name + ":" + (i + 1) + ": unreviewed destructive statement: " + lines[i].trim());
            }
        }
        return problems;
    }

    /** Every problem across all migrations on the classpath. */
    public static List<String> classpathProblems() throws IOException {
        List<String> problems = new ArrayList<>();
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/**/*.sql");
        for (Resource script : scripts) {
            try (InputStream in = script.getInputStream()) {
                problems.addAll(problems(script.getFilename(), new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        }
        return problems;
    }
}
