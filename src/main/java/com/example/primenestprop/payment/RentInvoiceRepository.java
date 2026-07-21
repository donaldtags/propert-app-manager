package com.example.primenestprop.payment;

import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.user.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentInvoiceRepository extends JpaRepository<RentInvoice, Long> {
    Optional<RentInvoice> findByLeaseAndPeriodStart(Lease lease, LocalDate periodStart);

    List<RentInvoice> findByLandlordAndStatusIn(AppUser landlord, List<RentInvoiceStatus> statuses);

    List<RentInvoice> findByTenantOrderByPeriodStartDesc(AppUser tenant);

    List<RentInvoice> findByLandlordOrderByPeriodStartDesc(AppUser landlord);

    long countByTenantAndStatus(AppUser tenant, RentInvoiceStatus status);

    long countByTenant(AppUser tenant);

    List<RentInvoice> findByLeaseAndStatusInOrderByPeriodStartAsc(Lease lease, List<RentInvoiceStatus> statuses);

    @Query("select coalesce(sum(i.amount), 0) from RentInvoice i where i.landlord = :landlord and i.status in :statuses")
    BigDecimal sumAmountByLandlordAndStatusIn(@Param("landlord") AppUser landlord, @Param("statuses") List<RentInvoiceStatus> statuses);

    long countByLandlordAndStatus(AppUser landlord, RentInvoiceStatus status);
}
