package com.example.primenestprop.construction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionUpdateRepository extends JpaRepository<ConstructionUpdate, Long> {
    @EntityGraph(attributePaths = {"postedBy"})
    List<ConstructionUpdate> findByProjectOrderByCreatedAtDesc(ConstructionProject project);

    @EntityGraph(attributePaths = {"postedBy"})
    Optional<ConstructionUpdate> findFirstByProjectOrderByCreatedAtDesc(ConstructionProject project);
}
