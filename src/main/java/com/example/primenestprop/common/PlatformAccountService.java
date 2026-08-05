package com.example.primenestprop.common;

import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserDtos;
import com.example.primenestprop.user.UserRepository;
import com.example.primenestprop.user.UserRole;
import com.example.primenestprop.user.UserService;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The platform's own account - the payee for any in-app charge PrimeNest itself collects
 * (featured listings, subscriptions, and future fee types), so revenue is always a real,
 * queryable Payment row rather than a side-channel ledger entry. */
@Service
public class PlatformAccountService {
    private static final String PLATFORM_ACCOUNT_EMAIL = "billing@primenest.africa";

    private final UserRepository userRepository;
    private final UserService userService;

    public PlatformAccountService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public AppUser billingAccount() {
        return userRepository.findByEmailIgnoreCase(PLATFORM_ACCOUNT_EMAIL).orElseGet(() -> {
            AppUser created = userService.create(new UserDtos.CreateUserRequest(
                    "PrimeNest Billing",
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