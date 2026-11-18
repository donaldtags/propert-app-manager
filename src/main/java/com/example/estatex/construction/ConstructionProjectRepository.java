package com.example.estatex.construction;

import com.example.estatex.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionProjectRepository extends JpaRepository<ConstructionProject, Long> {
    List<ConstructionProject> findByOwnerOrderByCreatedAtDesc(AppUser owner);
}
