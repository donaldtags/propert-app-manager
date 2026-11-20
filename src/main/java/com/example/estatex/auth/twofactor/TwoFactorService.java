package com.example.estatex.auth.twofactor;

import com.example.estatex.auth.sms.SmsGateway;
import com.example.estatex.common.ApiException;
import com.example.estatex.common.PhoneNumbers;
import com.example.estatex.user.AppUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step sign-in: a code from an authenticator app (TOTP) or a text message, plus ten
 * single-use backup codes for a lost phone. Turning it on always proves the app or phone works
 * first, so nobody locks themselves out by accident.
 */
@Service
public class TwoFactorService {
    public static final String TOTP = "TOTP";
    public static final String SMS = "SMS";
    public static final String BACKUP = "BACKUP";

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final int MAX_CODE_ATTEMPTS = 5;
    private static final String ISSUER = "EstateX";
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int BACKUP_CODE_COUNT = 10;

    private final OneTimeCodeRepository codes;
    private final BackupCodeRepository backupCodes;
    private final SmsGateway sms;
    private final SecretCipher cipher;
    private final boolean allowLogGateway;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(OneTimeCodeRepository codes, BackupCodeRepository backupCodes, SmsGateway sms,
                            SecretCipher cipher, @Value("${app.sms.allow-log-gateway:false}") boolean allowLogGateway) {
        this.codes = codes;
        this.backupCodes = backupCodes;
        this.sms = sms;
        this.cipher = cipher;
        this.allowLogGateway = allowLogGateway;
    }

    /** Text messages can actually reach people (a provider is configured, or logging is allowed in dev/test). */
    public boolean smsAvailable() {
        return sms.deliversRealMessages() || allowLogGateway;
    }

    public List<String> methodsFor(AppUser user) {
        List<String> methods = new ArrayList<>();
        if (user.getTotpSecretEncrypted() != null) methods.add(TOTP);
        if (user.getPhone() != null && smsAvailable()) methods.add(SMS);
        return methods;
    }

    public record Status(boolean enabled, boolean authenticatorApp, boolean sms, boolean smsAvailable,
                         String maskedPhone, long backupCodesRemaining) {}

    @Transactional(readOnly = true)
    public Status status(AppUser user) {
        return new Status(user.isTwoFactorEnabled(), user.getTotpSecretEncrypted() != null,
                user.isTwoFactorEnabled() && user.getPhone() != null && smsAvailable(), smsAvailable(),
                PhoneNumbers.mask(user.getPhone()), backupCodes.countByUserIdAndUsedAtIsNull(user.getId()));
    }

    // ---- Text-message codes -------------------------------------------------------------------

    /** Sends a fresh code (any earlier unused one for the same purpose stops working). */
    @Transactional
    public void sendCode(AppUser user, String purpose) {
        if (user.getPhone() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add a phone number to your profile first");
        }
        if (!smsAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Text messages aren't available right now. Use your authenticator app or a backup code.");
        }
        Instant now = Instant.now();
        if (codes.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now.minusSeconds(60)) >= 1) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "We just sent you a code. Please wait a minute before asking for another.");
        }
        if (codes.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, now.minus(Duration.ofHours(1))) >= 5) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Please try again in an hour.");
        }
        codes.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .ifPresent(old -> old.setConsumedAt(now));
        String code = String.format("%06d", random.nextInt(1_000_000));
        OneTimeCode row = new OneTimeCode();
        row.setUserId(user.getId());
        row.setPurpose(purpose);
        row.setCodeHash(hash(user.getId() + ":" + code));
        row.setExpiresAt(now.plus(CODE_TTL));
        codes.save(row);
        sms.send(user.getPhone(), "Your EstateX code is " + code + ". It expires in 5 minutes. Never share it with anyone - EstateX staff will never ask for it.");
    }

    /** Checks and uses up a texted code. Wrong, expired or already-used codes return false. */
    @Transactional
    public boolean checkSmsCode(AppUser user, String purpose, String code) {
        if (code == null) return false;
        String digits = code.replaceAll("\\s", "");
        return codes.findFirstByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .map(c -> {
                    c.setAttempts(c.getAttempts() + 1);
                    if (c.getAttempts() > MAX_CODE_ATTEMPTS) {
                        c.setConsumedAt(Instant.now());
                        return false;
                    }
                    if (!MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.UTF_8),
                            hash(user.getId() + ":" + digits).getBytes(StandardCharsets.UTF_8))) {
                        return false;
                    }
                    c.setConsumedAt(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    // ---- Authenticator app --------------------------------------------------------------------

    public record TotpSetup(String secret, String otpauthUri, String qrDataUrl) {}

    @Transactional
    public TotpSetup startTotp(AppUser user) {
        String secret = Totp.newSecret();
        user.setTotpPendingSecretEncrypted(cipher.encrypt(secret));
        String account = user.getEmail() != null ? user.getEmail() : user.getPhone() != null ? user.getPhone() : "user-" + user.getId();
        String uri = Totp.otpauthUri(ISSUER, account, secret);
        return new TotpSetup(secret, uri, qrDataUrl(uri));
    }

    /** Confirms the app works; returns new backup codes when this switches two-step sign-in on. */
    @Transactional
    public List<String> confirmTotp(AppUser user, String code) {
        if (user.getTotpPendingSecretEncrypted() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start the authenticator setup again");
        }
        String secret = cipher.decrypt(user.getTotpPendingSecretEncrypted());
        OptionalLong step = Totp.matchingStep(secret, code, Instant.now().getEpochSecond());
        if (step.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code didn't match. Check your phone's time is set automatically and try the newest code.");
        }
        user.setTotpSecretEncrypted(user.getTotpPendingSecretEncrypted());
        user.setTotpPendingSecretEncrypted(null);
        user.setTotpLastStep(step.getAsLong());
        return enable(user);
    }

    @Transactional
    public void startSms(AppUser user) {
        sendCode(user, OneTimeCode.ENABLE_SMS);
    }

    @Transactional
    public List<String> confirmSms(AppUser user, String code) {
        if (!checkSmsCode(user, OneTimeCode.ENABLE_SMS, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That code is wrong or has expired");
        }
        return enable(user);
    }

    private List<String> enable(AppUser user) {
        boolean wasOn = user.isTwoFactorEnabled();
        user.setTwoFactorEnabled(true);
        return wasOn && backupCodes.countByUserIdAndUsedAtIsNull(user.getId()) > 0 ? List.of() : newBackupCodes(user);
    }

    @Transactional
    public void disable(AppUser user) {
        user.setTwoFactorEnabled(false);
        user.setTotpSecretEncrypted(null);
        user.setTotpPendingSecretEncrypted(null);
        user.setTotpLastStep(null);
        backupCodes.deleteAllForUser(user.getId());
    }

    // ---- Checking a code at sign-in -----------------------------------------------------------

    /** True when the code is right for that method. Authenticator codes can't be reused. */
    @Transactional
    public boolean verify(AppUser user, String method, String code, String smsPurpose) {
        if (code == null || method == null) return false;
        return switch (method) {
            case TOTP -> {
                if (user.getTotpSecretEncrypted() == null) yield false;
                OptionalLong step = Totp.matchingStep(cipher.decrypt(user.getTotpSecretEncrypted()), code, Instant.now().getEpochSecond());
                if (step.isEmpty() || (user.getTotpLastStep() != null && step.getAsLong() <= user.getTotpLastStep())) yield false;
                user.setTotpLastStep(step.getAsLong());
                yield true;
            }
            case SMS -> user.getPhone() != null && checkSmsCode(user, smsPurpose, code);
            case BACKUP -> useBackupCode(user, code);
            default -> false;
        };
    }

    private boolean useBackupCode(AppUser user, String code) {
        String normalised = code.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String hashed = hash(user.getId() + ":" + normalised);
        for (BackupCode b : backupCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (MessageDigest.isEqual(b.getCodeHash().getBytes(StandardCharsets.UTF_8), hashed.getBytes(StandardCharsets.UTF_8))) {
                b.setUsedAt(Instant.now());
                return true;
            }
        }
        return false;
    }

    private List<String> newBackupCodes(AppUser user) {
        backupCodes.deleteAllForUser(user.getId());
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) sb.append(BACKUP_ALPHABET.charAt(random.nextInt(BACKUP_ALPHABET.length())));
            String code = sb.substring(0, 5) + "-" + sb.substring(5);
            BackupCode row = new BackupCode();
            row.setUserId(user.getId());
            row.setCodeHash(hash(user.getId() + ":" + code.replace("-", "")));
            backupCodes.save(row);
            plain.add(code);
        }
        return plain;
    }

    private static String qrDataUrl(String text) {
        try {
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Could not draw the QR code", e);
        }
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
