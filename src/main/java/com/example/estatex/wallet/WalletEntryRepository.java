package com.example.estatex.wallet;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {
    Optional<WalletEntry> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<WalletEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    @Query("select coalesce(sum(e.amountCents), 0) from WalletEntry e where e.walletId = :walletId")
    long sumForWallet(@Param("walletId") Long walletId);

    @Query("select coalesce(sum(e.amountCents), 0) from WalletEntry e where e.transferId = :transferId")
    long sumForTransfer(@Param("transferId") String transferId);
}
