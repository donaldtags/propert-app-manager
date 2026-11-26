package com.example.estatex.payment;

import com.example.estatex.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"payer", "payee", "property", "lease"})
    Optional<Payment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"payer", "payee", "property", "lease"})
    List<Payment> findByPayer(AppUser payer);

    @EntityGraph(attributePaths = {"payer", "payee", "property", "lease"})
    List<Payment> findByPayee(AppUser payee);

    Optional<Payment> findByProviderReference(String providerReference);

    /**
     * Row-locked read for every path that can complete a payment (return-page poll, gateway
     * callback, wallet, payee confirmation). Two of them can arrive at once - Paynow calls back
     * while the browser is still polling - and without the lock both would see INITIATED and
     * run the success side effects twice (e.g. REIT units issued twice for one payment).
     * Must be the first load of the payment in the transaction, so the state read is fresh.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("select p.id from Payment p where p.providerReference = :ref")
    Optional<Long> findIdByProviderReference(@Param("ref") String providerReference);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.estatex.payment.PaymentStatus.SUCCESSFUL
              and p.currency = :currency
              and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal sumSuccessfulBetween(@Param("from") Instant from, @Param("to") Instant to, @Param("currency") String currency);

    /** For the admin activity feed, which reads the payer's name after the session has closed. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "payer")
    List<Payment> findTop10ByStatusOrderByPaidAtDesc(PaymentStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.estatex.payment.PaymentStatus.SUCCESSFUL
              and p.payee.id = :payeeId
              and p.currency = :currency
            """)
    BigDecimal sumSuccessfulByPayeeAndCurrency(@Param("payeeId") Long payeeId, @Param("currency") String currency);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.estatex.payment.PaymentStatus.SUCCESSFUL
              and p.payee.id = :payeeId
              and p.currency = :currency
              and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal sumSuccessfulByPayeeBetween(
            @Param("payeeId") Long payeeId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("currency") String currency
    );

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.estatex.payment.PaymentStatus.SUCCESSFUL
              and p.payer.id = :payerId
              and p.currency = :currency
              and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal sumSuccessfulByPayerBetween(
            @Param("payerId") Long payerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("currency") String currency
    );
}
