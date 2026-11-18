package com.example.estatex.escrow;

import com.example.estatex.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowReleaseApprovalRepository extends JpaRepository<EscrowReleaseApproval, Long> {
    List<EscrowReleaseApproval> findByEscrow(EscrowTransaction escrow);

    boolean existsByEscrowAndApprover(EscrowTransaction escrow, AppUser approver);

    long countByEscrow(EscrowTransaction escrow);
}