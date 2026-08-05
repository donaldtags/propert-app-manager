package com.example.primenestprop.investment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReitRepository extends JpaRepository<Reit, Long> {
    List<Reit> findByActiveTrue();

    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reit r where r.id = :id")
    Optional<Reit> findByIdForUpdate(@Param("id") Long id);
}
