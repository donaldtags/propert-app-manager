package com.example.estatex.common;

import com.example.estatex.user.AppUser;
import com.example.estatex.user.UserDtos;
import com.example.estatex.user.UserRepository;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The platform's own account - the payee for any in-app charge EstateX itself collects
 * (featured listings, subscriptions, and future fee types), so revenue is always a real,
 * queryable Payment row rather than a side-channel ledger entry. */
@Service
public class PlatformAccountService {
    private static final String PLATFORM_ACCOUNT_EMAIL = "billing@estatex.africa";

    private final UserRepository userRepository;
    private final UserService userService;

    public PlatformAccountService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public AppUser billingAccount() {
        return LegacyBrand.adopt(userRepository, PLATFORM_ACCOUNT_EMAIL, "billing@primenest.africa", "EstateX Billing").orElseGet(() -> {
            AppUser created = userService.create(new UserDtos.CreateUserRequest(
                    "EstateX Billing",
                    PLATFORM_ACCOUNT_EMAIL,
                    "+263771000098",
                    "Bl9#" + UUID.randomUUID(),
                    "Zimbabwe",
                    Set.of(UserRole.ADMIN)
            ));
            userService.verify(created.getId());
            return created;
        });
    }
}