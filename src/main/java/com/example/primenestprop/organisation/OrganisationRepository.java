package com.example.primenestprop.organisation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findBySlugIgnoreCase(String slug);

    /** Row lock used to serialise trust-ledger writes per organisation (no concurrent overdraw). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organisation o where o.id = :id")
    Optional<Organisation> lockById(@Param("id") Long id);
}
