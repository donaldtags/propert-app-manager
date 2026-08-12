package com.example.primenestprop.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    List<Vendor> findByActiveTrueAndCategory(VendorCategory category);

    List<Vendor> findByActiveTrue();

    List<Vendor> findByActiveTrueAndCategoryAndCityIgnoreCase(VendorCategory category, String city);

    List<Vendor> findByActiveTrueAndCityIgnoreCase(String city);

    Optional<Vendor> findByOwnerId(Long ownerId);
}
