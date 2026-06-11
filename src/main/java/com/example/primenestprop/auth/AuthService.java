package com.example.primenestprop.auth;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserDtos.UserResponse;
import com.example.primenestprop.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String RESET_MESSAGE = "If this email exists, a reset link has been sent.";

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService users;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserService users,
            AuthSessionRepository sessions,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
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
        AppUser user = users.requireByEmail(request.email());
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
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
            log.info("Password reset URL for {}: http://localhost:3000/forgot-password?token={}", user.getEmail(), rawToken);
        });
        return new AuthDtos.MessageResponse(RESET_MESSAGE);
    }

    @Transactional
    public AuthDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        validatePasswordPolicy(request.password());
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
        return users.require(sessionFromAuthorization(authorization).getUser().getId());
    }

    @Transactional
    public void logout(String authorization) {
        String token = tokenFromAuthorization(authorization);
        sessions.deleteByToken(token);
    }

    private AuthDtos.AuthResponse issue(AppUser user) {
        String token = randomToken();
        AuthSession session = new AuthSession();
        session.setToken(token);
        session.setUser(user);
        sessions.save(session);
        return new AuthDtos.AuthResponse(token, UserResponse.from(user));
    }

    private AuthSession sessionFromAuthorization(String authorization) {
        String token = tokenFromAuthorization(authorization);
        return sessions.findByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again."));
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

    private void validatePasswordPolicy(String password) {
        if (password.length() < 10
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at least 10 characters and include uppercase, number, and symbol");
        }
    }
}
