package com.example.primenestprop.organisation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findBySlugIgnoreCase(String slug);
}
