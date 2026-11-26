package com.example.estatex.analytics.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {
    @Query("select v from SavedView v where v.page = :page and (v.ownerUserId = :user or v.shared = true) order by v.name")
    List<SavedView> visible(@Param("page") String page, @Param("user") Long user);

    long countByOwnerUserId(Long ownerUserId);
}
