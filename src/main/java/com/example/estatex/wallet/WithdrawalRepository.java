package com.example.estatex.wallet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    List<Withdrawal> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<Withdrawal> findByStatusOrderByRequestedAtAsc(String status);

    List<Withdrawal> findAllByOrderByRequestedAtDesc();

    @Query("select coalesce(sum(w.amountCents), 0) from Withdrawal w where w.walletId = :walletId and w.status = 'REQUESTED'")
    long pendingForWallet(@Param("walletId") Long walletId);
}
