package com.example.primenestprop.escrow;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EscrowRepository extends JpaRepository<EscrowTransaction, Long> {

    @EntityGraph(attributePaths = {"property", "payer", "beneficiary", "lease", "releaseApprovals"})
    Optional<EscrowTransaction> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"property", "payer", "beneficiary", "lease", "releaseApprovals"})
    @Query("select e from EscrowTransaction e order by e.createdAt desc")
    List<EscrowTransaction> findAllForAdmin();

    @EntityGraph(attributePaths = {"property", "payer", "beneficiary", "lease", "releaseApprovals"})
    List<EscrowTransaction> findByPayer(AppUser payer);

    @EntityGraph(attributePaths = {"property", "payer", "beneficiary", "lease", "releaseApprovals"})
    List<EscrowTransaction> findByBeneficiary(AppUser beneficiary);

    @EntityGraph(attributePaths = {"property", "payer", "beneficiary", "lease", "releaseApprovals"})
    List<EscrowTransaction> findByPropertyOrderByCreatedAtDesc(com.example.primenestprop.property.Property property);

    boolean existsByPayerAndStatus(AppUser payer, EscrowStatus status);

    boolean existsByBeneficiaryAndStatus(AppUser beneficiary, EscrowStatus status);

    long countByStatus(EscrowStatus status);

    @Query("select coalesce(sum(e.amount), 0) from EscrowTransaction e where e.status = :status and e.currency = :currency")
    java.math.BigDecimal sumAmountByStatusAndCurrency(@Param("status") EscrowStatus status, @Param("currency") String currency);

    List<EscrowTransaction> findTop10ByStatusOrderByReleasedAtDesc(EscrowStatus status);

    @Query("select coalesce(sum(e.amount), 0) from EscrowTransaction e where e.beneficiary = :beneficiary and e.status = :status and e.currency = :currency")
    java.math.BigDecimal sumAmountByBeneficiaryAndStatusAndCurrency(
            @Param("beneficiary") AppUser beneficiary,
            @Param("status") EscrowStatus status,
            @Param("currency") String currency
    );

    long countByBeneficiaryAndStatus(AppUser beneficiary, EscrowStatus status);
}
