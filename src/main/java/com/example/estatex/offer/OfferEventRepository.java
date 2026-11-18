package com.example.estatex.offer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferEventRepository extends JpaRepository<OfferEvent, Long> {
    List<OfferEvent> findByOfferIdOrderByAtAscIdAsc(Long offerId);
}
