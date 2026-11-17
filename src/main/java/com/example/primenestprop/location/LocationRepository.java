package com.example.primenestprop.location;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
    Optional<Location> findByParentIdIsNullAndNameIgnoreCase(String name);

    Optional<Location> findByParentIdAndNameIgnoreCase(Long parentId, String name);

    List<Location> findByVerifiedTrueOrderByNameAsc();

    List<Location> findByVerifiedFalseOrderByCreatedAtDesc();

    long countByTypeCode(String typeCode);
}
