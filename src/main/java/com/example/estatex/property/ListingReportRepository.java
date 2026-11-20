package com.example.estatex.property;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingReportRepository extends JpaRepository<ListingReport, Long> {
    List<ListingReport> findByStatusOrderByCreatedAtAsc(String status);

    List<ListingReport> findByPropertyIdAndStatus(Long propertyId, String status);
}
