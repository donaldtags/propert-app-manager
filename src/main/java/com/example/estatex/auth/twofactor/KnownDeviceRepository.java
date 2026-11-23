package com.example.estatex.auth.twofactor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, Long> {
    Optional<KnownDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash);

    long countByUserId(Long userId);
}
