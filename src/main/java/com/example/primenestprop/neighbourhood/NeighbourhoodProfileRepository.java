package com.example.primenestprop.neighbourhood;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeighbourhoodProfileRepository extends JpaRepository<NeighbourhoodProfile, Long> {
    Optional<NeighbourhoodProfile> findByCityIgnoreCaseAndSuburbIgnoreCase(String city, String suburb);
}
