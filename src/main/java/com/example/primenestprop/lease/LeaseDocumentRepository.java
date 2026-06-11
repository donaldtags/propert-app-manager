package com.example.primenestprop.lease;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseDocumentRepository extends JpaRepository<LeaseDocument, Long> {
    List<LeaseDocument> findByLeaseOrderByUploadedAtDesc(Lease lease);

    Optional<LeaseDocument> findByIdAndLease(Long id, Lease lease);
}
