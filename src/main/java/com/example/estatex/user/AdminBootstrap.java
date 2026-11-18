package com.example.estatex.user;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.AuditService;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants the platform ADMIN role to accounts listed in {@code APP_BOOTSTRAP_ADMIN_EMAILS}
 * (comma-separated), when they register, when they log in, and at startup for accounts that
 * already exist. This is how a fresh environment (CI, staging, a recreated database) gets its
 * first admin without hand-written SQL. Unset by default, so it does nothing unless the operator
 * opts in through the environment. Every grant is audited.
 */
@Component
@Order(20)
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final Set<String> emails;
    private final UserRepository users;
    private final UserService userService;
    private final AuditService audit;

    public AdminBootstrap(@Value("${app.bootstrap.admin-emails:}") String emails, UserRepository users,
                          UserService userService, AuditService audit) {
        this.emails = Arrays.stream(emails.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.users = users;
        this.userService = userService;
        this.audit = audit;
    }

    /** Adds ADMIN if this user's email is on the bootstrap list. Idempotent. */
    @Transactional
    public AppUser apply(AppUser user) {
        if (emails.isEmpty() || user.getEmail() == null
                || !emails.contains(user.getEmail().toLowerCase(Locale.ROOT))
                || user.getRoles().contains(UserRole.ADMIN)) {
            return user;
        }
        user.getRoles().add(UserRole.ADMIN);
        userService.syncRoleAssignments(user);
        users.save(user);
        audit.record(null, entry("ADMIN_BOOTSTRAPPED", "AppUser", user.getId())
                .metadata(Map.of("email", user.getEmail(), "source", "APP_BOOTSTRAP_ADMIN_EMAILS")));
        log.warn("Granted platform ADMIN to {} from APP_BOOTSTRAP_ADMIN_EMAILS", user.getEmail());
        return user;
    }

    @Override
    @Transactional
    public void run(String... args) {
        emails.forEach(email -> users.findByEmailIgnoreCase(email).ifPresent(this::apply));
    }
}
