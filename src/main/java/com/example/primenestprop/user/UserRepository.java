package com.example.primenestprop.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findFirstByPhone(String phone);

    List<AppUser> findByRolesContaining(UserRole role);
}
