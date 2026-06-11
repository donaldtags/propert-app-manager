package com.example.primenestprop.escrow;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowRepository extends JpaRepository<EscrowTransaction, Long> {
    List<EscrowTransaction> findByPayer(AppUser payer);

    List<EscrowTransaction> findByBeneficiary(AppUser beneficiary);
}
