package com.example.primenestprop.payment;

import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.primenestprop.payment.PaymentStatus.SUCCESSFUL
              and p.currency = :currency
              and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal sumSuccessfulBetween(@Param("from") Instant from, @Param("to") Instant to, @Param("currency") String currency);

    List<Payment> findTop10ByStatusOrderByPaidAtDesc(PaymentStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.primenestprop.payment.PaymentStatus.SUCCESSFUL
              and p.payee.id = :payeeId
              and p.currency = :currency
            """)
    BigDecimal sumSuccessfulByPayeeAndCurrency(@Param("payeeId") Long payeeId, @Param("currency") String currency);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.example.primenestprop.payment.PaymentStatus.SUCCESSFUL
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
            where p.status = com.example.primenestprop.payment.PaymentStatus.SUCCESSFUL
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
