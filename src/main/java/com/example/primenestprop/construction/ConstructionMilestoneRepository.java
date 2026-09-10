package com.example.primenestprop.construction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionMilestoneRepository extends JpaRepository<ConstructionMilestone, Long> {
    List<ConstructionMilestone> findByProjectOrderByTargetDateAscCreatedAtAsc(ConstructionProject project);
}
