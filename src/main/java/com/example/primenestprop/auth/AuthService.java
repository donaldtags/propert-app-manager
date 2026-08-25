package com.example.primenestprop.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.common.PasswordPolicy;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserDtos.UserResponse;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String RESET_MESSAGE = "If this email exists, a reset link has been sent.";

    /**
     * Every authenticated request otherwise costs 2 DB round trips (session lookup + user
     * reload) in {@link #currentUser}. Caching the resolved user per bearer token bounds that
     * to one DB hit per token per TTL window, independent of how many requests arrive - the
     * lever that matters for surviving high concurrent request volume, not the permission
     * check itself (which is already O(1) in-memory, see {@link com.example.primenestprop.user.RolePermissions}).
     * A short TTL keeps role/permission changes and session revocation visible quickly;
     * logout evicts immediately.
     */
    private final Cache<String, AppUser> currentUserCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService users;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final long sessionTtlHours;
    private final String frontendBaseUrl;

    public AuthService(
            UserService users,
            AuthSessionRepository sessions,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.auth.session-ttl-hours:168}") long sessionTtlHours,
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.users = users;
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.sessionTtlHours = sessionTtlHours;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (request.roles().contains(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin role requires approval and cannot be self-registered");
        }
        PasswordPolicy.validate(request.password());
        AppUser user = users.create(new UserDtos.CreateUserRequest(
                request.fullName(),
                request.email(),
                request.phone(),
                request.password(),
                request.country(),
                request.roles()
        ));
        return issue(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        AppUser user = users.requireByIdentifier(request.identifier());
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issue(user);
    }

    @Transactional
    public AuthDtos.MessageResponse forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        users.findByEmail(request.email()).ifPresent(user -> {
            String rawToken = randomToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setTokenHash(hash(rawToken));
            token.setUser(user);
            token.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
            resetTokens.save(token);
            String resetLink = frontendBaseUrl + "/forgot-password?token=" + rawToken;
            emailService.sendPasswordReset(user.getEmail(), resetLink);
        });
        return new AuthDtos.MessageResponse(RESET_MESSAGE);
    }

    @Transactional
    public AuthDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordPolicy.validate(request.password());
        PasswordResetToken token = resetTokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }
        users.updatePassword(token.getUser().getId(), request.password());
        token.setUsed(true);
        token.setUsedAt(Instant.now());
        return new AuthDtos.MessageResponse("Password has been reset.");
    }

    @Transactional(readOnly = true)
    public UserResponse me(String authorization) {
        return UserResponse.from(sessionFromAuthorization(authorization).getUser());
    }

    @Transactional(readOnly = true)
    public AppUser currentUser(String authorization) {
        String token = tokenFromAuthorization(authorization);
        AppUser cached = currentUserCache.getIfPresent(token);
        if (cached != null) {
            return cached;
        }
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        currentUserCache.put(token, user);
        return user;
    }

    @Transactional
    public void logout(String authorization) {
        String token = tokenFromAuthorization(authorization);
        currentUserCache.invalidate(token);
        sessions.deleteByToken(token);
    }

    private AuthDtos.AuthResponse issue(AppUser user) {
        String token = randomToken();
        AuthSession session = new AuthSession();
        session.setToken(token);
        session.setUser(user);
        session.setExpiresAt(Instant.now().plus(sessionTtlHours, ChronoUnit.HOURS));
        sessions.save(session);
        return new AuthDtos.AuthResponse(token, UserResponse.from(user));
    }

    private AuthSession sessionFromAuthorization(String authorization) {
        String token = tokenFromAuthorization(authorization);
        AuthSession session = sessions.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again."));
        if (session.getExpiresAt().isBefore(Instant.now())) {
            sessions.delete(session);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        return session;
    }

    private String tokenFromAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return authorization.substring("Bearer ".length());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
