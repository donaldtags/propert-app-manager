package com.example.primenestprop.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RoleTableSynchronizer implements CommandLineRunner {
    private final UserRepository users;
    private final RoleLookupRepository roles;
    private final UserRoleAssignmentRepository assignments;

    RoleTableSynchronizer(UserRepository users, RoleLookupRepository roles, UserRoleAssignmentRepository assignments) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (UserRole userRole : UserRole.values()) {
            roles.findByName(userRole).orElseGet(() -> {
                RoleLookup role = new RoleLookup();
                role.setName(userRole);
                return roles.save(role);
            });
        }
        for (AppUser user : users.findAll()) {
            for (UserRole userRole : user.getRoles()) {
                RoleLookup role = roles.findByName(userRole).orElseThrow();
                assignments.save(new UserRoleAssignment(user.getId(), role.getId()));
            }
        }
    }
}
