package com.example.estatex.transaction;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Long> {
    List<SaleTransaction> findByPropertyIdOrderByReportedAtDesc(Long propertyId);

    boolean existsByPropertyIdAndVerificationIn(Long propertyId, Collection<SaleVerification> verifications);

    List<SaleTransaction> findTop200ByVerificationOrderByReportedAtAsc(SaleVerification verification);
}
