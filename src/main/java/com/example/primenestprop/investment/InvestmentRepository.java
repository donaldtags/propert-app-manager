package com.example.primenestprop.investment;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    List<Investment> findByInvestor(AppUser investor);
}
