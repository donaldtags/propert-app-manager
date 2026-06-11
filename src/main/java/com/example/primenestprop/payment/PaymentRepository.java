package com.example.primenestprop.payment;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByPayer(AppUser payer);

    List<Payment> findByPayee(AppUser payee);
}
