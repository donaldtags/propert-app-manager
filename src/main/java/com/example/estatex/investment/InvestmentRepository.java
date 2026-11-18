package com.example.estatex.investment;

import com.example.estatex.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    @EntityGraph(attributePaths = {"investor", "reit"})
    List<Investment> findByInvestor(AppUser investor);

    List<Investment> findByInvestorAndReitAndStatusOrderByCreatedAtAsc(AppUser investor, Reit reit, InvestmentStatus status);
}
