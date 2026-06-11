package com.example.primenestprop.lease;

import com.example.primenestprop.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseRepository extends JpaRepository<Lease, Long> {
    List<Lease> findByTenant(AppUser tenant);

    List<Lease> findByLandlord(AppUser landlord);
}
