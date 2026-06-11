package com.example.primenestprop.investment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReitRepository extends JpaRepository<Reit, Long> {
    List<Reit> findByActiveTrue();
}
