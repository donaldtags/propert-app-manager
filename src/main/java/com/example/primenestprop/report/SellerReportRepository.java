package com.example.primenestprop.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerReportRepository extends JpaRepository<SellerReport, Long> {
    Optional<SellerReport> findByShareToken(String shareToken);

    List<SellerReport> findByPropertyIdOrderByGeneratedAtDesc(Long propertyId);
}
