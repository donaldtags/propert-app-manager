package com.example.primenestprop.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleLookupRepository extends JpaRepository<RoleLookup, Long> {
    Optional<RoleLookup> findByName(UserRole name);
}
