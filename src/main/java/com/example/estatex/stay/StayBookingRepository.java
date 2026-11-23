package com.example.estatex.stay;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StayBookingRepository extends JpaRepository<StayBooking, Long> {
    /** Bookings that keep any of the given listings busy somewhere in [from, to). */
    @Query("""
            select b from StayBooking b
            where b.property.id in :propertyIds and b.statusCode in :statuses
              and b.checkIn < :to and b.checkOut > :from
            order by b.checkIn
            """)
    List<StayBooking> overlapping(@Param("propertyIds") Collection<Long> propertyIds, @Param("from") LocalDate from,
                                  @Param("to") LocalDate to, @Param("statuses") Collection<String> statuses);

    boolean existsByPropertyIdAndGuestIdAndStatusCodeIn(Long propertyId, Long guestId, Collection<String> statuses);

    @EntityGraph(attributePaths = {"property", "host"})
    List<StayBooking> findByGuestIdOrderByCheckInDesc(Long guestId);

    @EntityGraph(attributePaths = {"property", "guest"})
    List<StayBooking> findByHostIdOrderByCheckInDesc(Long hostId);

    @EntityGraph(attributePaths = {"property", "guest", "host"})
    Optional<StayBooking> findWithPartiesById(Long id);

    @Query("select b from StayBooking b where b.statusCode in :statuses and b.holdExpiresAt < :now")
    List<StayBooking> holdsExpiredBefore(@Param("now") Instant now, @Param("statuses") Collection<String> statuses);

    @Query("select b from StayBooking b where b.statusCode = 'CONFIRMED' and b.checkOut <= :today")
    List<StayBooking> finishedBy(@Param("today") LocalDate today);
}
