package com.example.estatex.wallet;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserIdAndCurrency(Long userId, String currency);

    Optional<Wallet> findBySystemCodeAndCurrency(String systemCode, String currency);
}
