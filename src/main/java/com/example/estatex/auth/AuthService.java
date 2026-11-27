package com.example.estatex.auth;

import com.example.estatex.auth.sms.SmsGateway;
import com.example.estatex.auth.twofactor.KnownDevice;
import com.example.estatex.auth.twofactor.KnownDeviceRepository;
import com.example.estatex.auth.twofactor.LoginChallenge;
import com.example.estatex.auth.twofactor.LoginChallengeRepository;
import com.example.estatex.auth.twofactor.OneTimeCode;
import com.example.estatex.auth.twofactor.TwoFactorService;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.PasswordPolicy;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserDtos;
import com.example.estatex.user.UserDtos.UserResponse;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String RESET_MESSAGE = "If an account matches, we've sent instructions to reset your password.";
    private static final String BAD_CREDENTIALS = "Invalid credentials";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final DateTimeFormatter ALERT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Africa/Harare"));

    /**
     * Every authenticated request otherwise costs 2 DB round trips (session lookup + user
     * reload) in {@link #currentUser}. Caching the resolved user per bearer token bounds that
     * to one DB hit per token per TTL window. A short TTL keeps role changes visible quickly;
     * logout, "sign out everywhere" and password changes evict immediately.
     */
    private final Cache<String, AppUser> currentUserCache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService users;
    private final com.example.estatex.referral.ReferralService referrals;
    private final AuthSessionRepository sessions;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final com.example.estatex.user.AdminBootstrap adminBootstrap;
    private final LoginAttemptService attempts;
    private final TwoFactorService twoFactor;
    private final LoginChallengeRepository challenges;
    private final KnownDeviceRepository devices;
    private final NotificationService notifications;
    private final SmsGateway sms;
    private final long sessionTtlHours;
    private final String frontendBaseUrl;
    /** Checked against when the account doesn't exist, so a wrong email takes as long as a wrong password. */
    private final String timingDummyHash;

    public AuthService(
            UserService users,
            com.example.estatex.referral.ReferralService referrals,
            AuthSessionRepository sessions,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            com.example.estatex.user.AdminBootstrap adminBootstrap,
            LoginAttemptService attempts,
            TwoFactorService twoFactor,
            LoginChallengeRepository challenges,
            KnownDeviceRepository devices,
            @Lazy NotificationService notifications,
            SmsGateway sms,
            @Value("${app.auth.session-ttl-hours:168}") long sessionTtlHours,
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl
    ) {
        this.users = users;
        this.referrals = referrals;
        this.sessions = sessions;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.adminBootstrap = adminBootstrap;
        this.attempts = attempts;
        this.twoFactor = twoFactor;
        this.challenges = challenges;
        this.devices = devices;
        this.notifications = notifications;
        this.sms = sms;
        this.sessionTtlHours = sessionTtlHours;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
        this.timingDummyHash = passwordEncoder.encode(randomToken());
    }

    // ---- Sign-up and sign-in ------------------------------------------------------------------

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, String userAgent) {
        if (request.roles().stream().anyMatch(UserRole::isStaff)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EstateX staff roles are granted by an administrator and cannot be self-registered");
        }
        PasswordPolicy.validate(request.password());
        referrals.requireValidCode(request.referralCode());
        AppUser user = users.create(new UserDtos.CreateUserRequest(
                request.fullName(),
                request.email(),
                request.phone(),
                request.password(),
                request.country(),
                request.roles()
        ));
        rememberDevice(user, request.deviceId(), userAgent, false);
        referrals.onRegistered(user, request.referralCode(), request.deviceId());
        return issue(adminBootstrap.apply(user));
    }

    /**
     * Password step. Returns a session, or a two-step challenge when the account has it on.
     * Wrong passwords count towards a 15-minute lock on the account; the error is the same whether
     * or not the account exists.
     */
    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String userAgent) {
        Optional<AppUser> found = users.findByIdentifier(request.identifier());
        if (found.isEmpty()) {
            attempts.requireNotLocked(request.identifier());
            passwordEncoder.matches(request.password(), timingDummyHash);
            attempts.recordUnknownFailure(request.identifier());
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        AppUser user = found.get();
        attempts.requireNotLocked(user);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            attempts.recordFailure(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        List<String> methods = twoFactor.methodsFor(user);
        if (user.isTwoFactorEnabled()) {
            String raw = randomToken();
            LoginChallenge challenge = new LoginChallenge();
            challenge.setTokenHash(hash(raw));
            challenge.setUserId(user.getId());
            challenge.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));
            challenge.setDeviceId(truncate(request.deviceId(), 100));
            challenge.setUserAgent(truncate(userAgent, 300));
            challenges.save(challenge);
            boolean codeSent = false;
            if (!methods.contains(TwoFactorService.TOTP) && methods.contains(TwoFactorService.SMS)) {
                // Text-message only: send the code straight away so it's waiting on their phone.
                codeSent = trySendLoginCode(user);
            }
            return AuthDtos.AuthResponse.challenge(raw, methods,
                    com.example.estatex.common.PhoneNumbers.mask(user.getPhone()), codeSent);
        }
        return completeSignIn(user, request.deviceId(), userAgent);
    }

    /** Second step: the code from the app, a text message or a backup code. */
    @Transactional
    public AuthDtos.AuthResponse verifyTwoFactor(AuthDtos.TwoFactorVerifyRequest request, String userAgent) {
        LoginChallenge challenge = openChallenge(request.challengeToken());
        AppUser user = users.require(challenge.getUserId());
        attempts.requireNotLocked(user);
        String method = request.method().toUpperCase();
        if (!twoFactor.verify(user, method, request.code(), OneTimeCode.LOGIN)) {
            attempts.recordChallengeFailure(challenge.getId(), user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code didn't work. Check it and try again.");
        }
        challenge.setUsedAt(Instant.now());
        String deviceId = request.deviceId() != null ? request.deviceId() : challenge.getDeviceId();
        return completeSignIn(user, deviceId, userAgent != null ? userAgent : challenge.getUserAgent());
    }

    /** "Text me a code instead" during sign-in. */
    @Transactional
    public AuthDtos.MessageResponse sendLoginCode(AuthDtos.ChallengeRequest request) {
        LoginChallenge challenge = openChallenge(request.challengeToken());
        AppUser user = users.require(challenge.getUserId());
        attempts.requireNotLocked(user);
        twoFactor.sendCode(user, OneTimeCode.LOGIN);
        return new AuthDtos.MessageResponse("Code sent to " + com.example.estatex.common.PhoneNumbers.mask(user.getPhone()));
    }

    private boolean trySendLoginCode(AppUser user) {
        try {
            twoFactor.sendCode(user, OneTimeCode.LOGIN);
            return true;
        } catch (ApiException e) {
            // e.g. one was sent less than a minute ago: that code still works.
            return false;
        }
    }

    private LoginChallenge openChallenge(String rawToken) {
        LoginChallenge challenge = challenges.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your sign-in timed out. Please enter your password again."));
        if (challenge.getUsedAt() != null || challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your sign-in timed out. Please enter your password again.");
        }
        return challenge;
    }

    private AuthDtos.AuthResponse completeSignIn(AppUser user, String deviceId, String userAgent) {
        attempts.reset(user);
        rememberDevice(user, deviceId, userAgent, true);
        return issue(adminBootstrap.apply(user));
    }

    // ---- New-device alerts --------------------------------------------------------------------

    private void rememberDevice(AppUser user, String deviceId, String userAgent, boolean alertIfNew) {
        referrals.recordDevice(user.getId(), deviceId);
        String fingerprint = deviceId != null && !deviceId.isBlank() ? "id:" + deviceId : "ua:" + (userAgent == null ? "" : userAgent);
        String deviceHash = hash(user.getId() + ":" + fingerprint);
        Optional<KnownDevice> known = devices.findByUserIdAndDeviceHash(user.getId(), deviceHash);
        if (known.isPresent()) {
            known.get().setLastSeenAt(Instant.now());
            return;
        }
        boolean firstDevice = devices.countByUserId(user.getId()) == 0;
        KnownDevice device = new KnownDevice();
        device.setUserId(user.getId());
        device.setDeviceHash(deviceHash);
        device.setLabel(describe(userAgent));
        devices.save(device);
        if (alertIfNew && !firstDevice) {
            alertNewDevice(user, device);
        }
    }

    private void alertNewDevice(AppUser user, KnownDevice device) {
        String when = ALERT_TIME.format(device.getFirstSeenAt());
        String text = "Someone signed in to your EstateX account from a new device (" + device.getLabel() + ") on " + when
                + ". If this was you, there's nothing to do. If not, change your password now and turn on two-step sign-in in Settings > Security.";
        notifications.notify(user, NotificationType.SECURITY_ALERT, device.getId(), "New sign-in to your account", text);
        if (user.getEmail() != null) {
            emailService.send(user.getEmail(), "New sign-in to your EstateX account", text);
        } else if (user.getPhone() != null && twoFactor.smsAvailable()) {
            try {
                sms.send(user.getPhone(), "EstateX: new sign-in from " + device.getLabel() + " on " + when + ". Not you? Change your password now.");
            } catch (ApiException e) {
                // The in-app alert is already recorded; never fail the sign-in over the text.
            }
        }
    }

    /** "Chrome on Windows", "Safari on iPhone"... good enough for a person to recognise. */
    static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "an unknown device";
        String ua = userAgent;
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "a browser";
        String os = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad" : ua.contains("Android") ? "Android"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " on " + os;
    }

    // ---- Passwords ----------------------------------------------------------------------------

    /**
     * Email address -> emailed link. Phone number -> texted code. The answer is the same whether or
     * not an account exists.
     */
    @Transactional
    public AuthDtos.MessageResponse forgotPassword(AuthDtos.ForgotPasswordRequest request) {
        String identifier = request.value();
        if (identifier == null || identifier.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your email address or phone number");
        }
        Optional<AppUser> found = users.findByIdentifier(identifier);
        if (identifier.contains("@")) {
            found.filter(u -> u.getEmail() != null).ifPresent(user -> {
                String rawToken = randomToken();
                PasswordResetToken token = new PasswordResetToken();
                token.setTokenHash(hash(rawToken));
                token.setUser(user);
                token.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
                resetTokens.save(token);
                emailService.sendPasswordReset(user.getEmail(), frontendBaseUrl + "/forgot-password?token=" + rawToken);
            });
        } else {
            found.filter(u -> u.getPhone() != null).ifPresent(user -> {
                try {
                    twoFactor.sendCode(user, OneTimeCode.PASSWORD_RESET);
                } catch (ApiException e) {
                    // Throttled or SMS unavailable: stay silent so the response doesn't reveal the account.
                }
            });
        }
        return new AuthDtos.MessageResponse(RESET_MESSAGE);
    }

    @Transactional
    public AuthDtos.MessageResponse resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordPolicy.validate(request.password());
        AppUser user;
        if (request.token() != null && !request.token().isBlank()) {
            PasswordResetToken token = resetTokens.findByTokenHash(hash(request.token()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));
            if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
            }
            token.setUsed(true);
            token.setUsedAt(Instant.now());
            user = token.getUser();
        } else {
            if (request.identifier() == null || request.code() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the code we texted you");
            }
            user = users.findByIdentifier(request.identifier())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "That code is wrong or has expired"));
            attempts.requireNotLocked(user);
            if (!twoFactor.checkSmsCode(user, OneTimeCode.PASSWORD_RESET, request.code())) {
                attempts.recordFailure(user.getId());
                throw new ApiException(HttpStatus.BAD_REQUEST, "That code is wrong or has expired");
            }
        }
        users.updatePassword(user.getId(), request.password());
        attempts.reset(users.require(user.getId()));
        signOutEverywhere(user.getId(), null);
        return new AuthDtos.MessageResponse("Password has been reset. Please sign in with your new password.");
    }

    /** Changing the password signs out every other device, in case someone else knew the old one. */
    @Transactional
    public AuthDtos.MessageResponse changePassword(String authorization, AuthDtos.ChangePasswordRequest request) {
        AuthSession session = sessionFromAuthorization(authorization);
        AppUser user = users.require(session.getUser().getId());
        attempts.requireNotLocked(user);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            attempts.recordFailure(user.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is wrong");
        }
        PasswordPolicy.validate(request.newPassword());
        users.updatePassword(user.getId(), request.newPassword());
        signOutEverywhere(user.getId(), session.getToken());
        notifications.notify(user, NotificationType.SECURITY_ALERT, Instant.now().toEpochMilli(),
                "Your password was changed", "Your EstateX password was changed and other devices were signed out.");
        return new AuthDtos.MessageResponse("Password changed. Other devices have been signed out.");
    }

    // ---- Two-step sign-in settings ------------------------------------------------------------

    @Transactional
    public AuthDtos.MessageResponse disableTwoFactor(String authorization, AuthDtos.DisableTwoFactorRequest request) {
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        attempts.requireNotLocked(user);
        boolean passwordOk = user.getPasswordHash() != null && passwordEncoder.matches(request.password(), user.getPasswordHash());
        boolean codeOk = passwordOk && twoFactor.verify(user, request.method().toUpperCase(), request.code(), OneTimeCode.DISABLE);
        if (!codeOk) {
            attempts.recordFailure(user.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your password or code is wrong");
        }
        twoFactor.disable(user);
        notifications.notify(user, NotificationType.SECURITY_ALERT, Instant.now().toEpochMilli(),
                "Two-step sign-in turned off", "Two-step sign-in was turned off for your account. If this wasn't you, change your password now.");
        evictCachedUser(user.getId(), null);
        return new AuthDtos.MessageResponse("Two-step sign-in is off.");
    }

    // These load the user inside the transaction: the request's principal is a cached copy, and
    // changes to it would never be saved.

    @Transactional(readOnly = true)
    public TwoFactorService.Status twoFactorStatus(String authorization) {
        return twoFactor.status(users.require(sessionFromAuthorization(authorization).getUser().getId()));
    }

    @Transactional
    public TwoFactorService.TotpSetup startTotp(String authorization) {
        return twoFactor.startTotp(users.require(sessionFromAuthorization(authorization).getUser().getId()));
    }

    @Transactional
    public AuthDtos.BackupCodesResponse confirmTotp(String authorization, String code) {
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        List<String> codes = twoFactor.confirmTotp(user, code);
        twoFactorTurnedOn(user);
        return new AuthDtos.BackupCodesResponse(codes);
    }

    @Transactional
    public AuthDtos.MessageResponse startSms(String authorization) {
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        twoFactor.startSms(user);
        return new AuthDtos.MessageResponse("Code sent to " + com.example.estatex.common.PhoneNumbers.mask(user.getPhone()));
    }

    @Transactional
    public AuthDtos.BackupCodesResponse confirmSms(String authorization, String code) {
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        List<String> codes = twoFactor.confirmSms(user, code);
        twoFactorTurnedOn(user);
        return new AuthDtos.BackupCodesResponse(codes);
    }

    private void twoFactorTurnedOn(AppUser user) {
        notifications.notify(user, NotificationType.SECURITY_ALERT, Instant.now().toEpochMilli(),
                "Two-step sign-in is on", "Your account now asks for a code when you sign in. Keep your backup codes somewhere safe.");
        evictCachedUser(user.getId(), null);
    }

    /** Texts a code to confirm turning two-step sign-in off (for accounts without the app). */
    @Transactional
    public AuthDtos.MessageResponse sendDisableCode(String authorization) {
        AppUser user = users.require(sessionFromAuthorization(authorization).getUser().getId());
        twoFactor.sendCode(user, OneTimeCode.DISABLE);
        return new AuthDtos.MessageResponse("Code sent to " + com.example.estatex.common.PhoneNumbers.mask(user.getPhone()));
    }

    // ---- Sessions -----------------------------------------------------------------------------

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

    /** "Sign out of all devices", including this one. */
    @Transactional
    public AuthDtos.MessageResponse logoutEverywhere(String authorization) {
        AuthSession session = sessionFromAuthorization(authorization);
        signOutEverywhere(session.getUser().getId(), null);
        return new AuthDtos.MessageResponse("Signed out of all devices.");
    }

    private void signOutEverywhere(Long userId, String keepToken) {
        if (keepToken == null) {
            sessions.deleteAllForUser(userId);
        } else {
            sessions.deleteOthersForUser(userId, keepToken);
        }
        evictCachedUser(userId, keepToken);
    }

    private void evictCachedUser(Long userId, String keepToken) {
        currentUserCache.asMap().entrySet().removeIf(e -> e.getValue().getId().equals(userId)
                && (keepToken == null || !e.getKey().equals(keepToken)));
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

    AuthSession sessionFromAuthorization(String authorization) {
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

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
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
