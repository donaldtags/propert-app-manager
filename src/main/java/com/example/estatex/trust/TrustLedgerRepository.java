package com.example.estatex.trust;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrustLedgerRepository extends JpaRepository<TrustLedgerEntry, Long> {
    Optional<TrustLedgerEntry> findByClientId(String clientId);

    List<TrustLedgerEntry> findByOrganisationIdOrderByOccurredOnDescIdDesc(Long organisationId);

    @Query("""
            select coalesce(sum(e.signedAmountCents), 0) from TrustLedgerEntry e
            where e.organisationId = :org and e.currency = :currency
              and ((:propertyId is null and e.propertyId is null) or e.propertyId = :propertyId)
            """)
    long bucketBalance(@Param("org") Long organisationId, @Param("currency") String currency,
                       @Param("propertyId") Long propertyId);

    @Query("""
            select e.currency, coalesce(sum(e.signedAmountCents), 0) from TrustLedgerEntry e
            where e.organisationId = :org group by e.currency
            """)
    List<Object[]> balancesByCurrency(@Param("org") Long organisationId);
}
