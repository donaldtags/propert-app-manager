package com.example.estatex.referral;

import com.example.estatex.common.ApiException;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReferralController {
    private final ReferralService service;

    ReferralController(ReferralService service) {
        this.service = service;
    }

    /** Public: what a referral link offers. Says nothing about who owns the code. */
    @GetMapping("/api/v1/referrals/code/{code}")
    ReferralService.CodeInfo code(@PathVariable String code) {
        return service.describe(code);
    }

    /** The signed-in user's code, how their invitations are going, and any discount they're due. */
    @GetMapping("/api/v1/referrals/mine")
    ReferralService.Mine mine(@AuthenticationPrincipal AppUser user) {
        return service.mine(user);
    }

    @GetMapping("/api/v1/admin/referrals")
    ReferralService.AdminView admin(@AuthenticationPrincipal AppUser user) {
        if (user == null || !(user.hasPermission(Permission.ANALYTICS_FINANCE_VIEW) || user.hasPermission(Permission.USER_MANAGE))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You don't have access to the referral programme.");
        }
        return service.adminView();
    }

    @PutMapping("/api/v1/admin/referrals/settings")
    ReferralService.Settings settings(@RequestBody ReferralService.Settings request, @AuthenticationPrincipal AppUser user) {
        requireConfigure(user);
        return service.updateSettings(request, user);
    }

    record ReverseRequest(String reason) {
    }

    @PostMapping("/api/v1/admin/referrals/{id}/reverse")
    ReferralService.AdminView reverse(@PathVariable Long id, @RequestBody ReverseRequest request, @AuthenticationPrincipal AppUser user) {
        requireConfigure(user);
        service.reverse(id, request.reason(), user);
        return service.adminView();
    }

    private static void requireConfigure(AppUser user) {
        if (user == null || !user.hasPermission(Permission.ANALYTICS_CONFIGURE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only administrators can change the referral programme.");
        }
    }
}
