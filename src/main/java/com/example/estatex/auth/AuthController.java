package com.example.estatex.auth;

import com.example.estatex.auth.twofactor.TwoFactorService;
import com.example.estatex.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                   @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return service.register(request, userAgent);
    }

    @PostMapping("/login")
    AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return service.login(request, userAgent);
    }

    /** Second sign-in step: { challengeToken, code, method: TOTP | SMS | BACKUP }. */
    @PostMapping("/2fa/verify")
    AuthDtos.AuthResponse verifyTwoFactor(@Valid @RequestBody AuthDtos.TwoFactorVerifyRequest request,
                                          @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return service.verifyTwoFactor(request, userAgent);
    }

    /** "Text me a code instead" during sign-in. */
    @PostMapping("/2fa/sms/send")
    AuthDtos.MessageResponse sendLoginCode(@Valid @RequestBody AuthDtos.ChallengeRequest request) {
        return service.sendLoginCode(request);
    }

    @PostMapping("/forgot-password")
    AuthDtos.MessageResponse forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        return service.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    AuthDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        return service.resetPassword(request);
    }

    @PostMapping("/change-password")
    AuthDtos.MessageResponse changePassword(@RequestHeader(name = "Authorization", required = false) String authorization,
                                            @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        return service.changePassword(authorization, request);
    }

    @PostMapping("/logout")
    void logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        service.logout(authorization);
    }

    /** Signs out every device, including this one. */
    @PostMapping("/logout-all")
    AuthDtos.MessageResponse logoutAll(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.logoutEverywhere(authorization);
    }

    @GetMapping("/me")
    UserResponse me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.me(authorization);
    }

    // ---- Two-step sign-in settings (signed in) ----

    @GetMapping("/2fa")
    TwoFactorService.Status twoFactorStatus(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.twoFactorStatus(authorization);
    }

    @PostMapping("/2fa/totp/start")
    TwoFactorService.TotpSetup startTotp(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.startTotp(authorization);
    }

    @PostMapping("/2fa/totp/confirm")
    AuthDtos.BackupCodesResponse confirmTotp(@RequestHeader(name = "Authorization", required = false) String authorization,
                                             @Valid @RequestBody AuthDtos.CodeRequest request) {
        return service.confirmTotp(authorization, request.code());
    }

    @PostMapping("/2fa/sms/start")
    AuthDtos.MessageResponse startSms(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.startSms(authorization);
    }

    @PostMapping("/2fa/sms/confirm")
    AuthDtos.BackupCodesResponse confirmSms(@RequestHeader(name = "Authorization", required = false) String authorization,
                                            @Valid @RequestBody AuthDtos.CodeRequest request) {
        return service.confirmSms(authorization, request.code());
    }

    /** Texts a code for turning two-step sign-in off. */
    @PostMapping("/2fa/disable/code")
    AuthDtos.MessageResponse sendDisableCode(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return service.sendDisableCode(authorization);
    }

    @PostMapping("/2fa/disable")
    AuthDtos.MessageResponse disable(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @Valid @RequestBody AuthDtos.DisableTwoFactorRequest request) {
        return service.disableTwoFactor(authorization, request);
    }
}
