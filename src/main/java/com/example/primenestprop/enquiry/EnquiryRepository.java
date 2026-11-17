package com.example.primenestprop.enquiry;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    Optional<Enquiry> findByClientId(String clientId);

    List<Enquiry> findByOrganisationIdOrderByCapturedAtDescIdDesc(Long organisationId, Pageable pageable);
}
