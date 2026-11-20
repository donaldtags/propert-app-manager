package com.example.estatex.kyc.check;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityCheckRepository extends JpaRepository<IdentityCheck, Long> {
    List<IdentityCheck> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<IdentityCheck> findFirstByProviderAndProviderReference(String provider, String providerReference);
}
