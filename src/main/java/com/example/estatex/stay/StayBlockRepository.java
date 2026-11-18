package com.example.estatex.stay;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StayBlockRepository extends JpaRepository<StayBlock, Long> {
    @Query("""
            select b from StayBlock b
            where b.propertyId in :propertyIds and b.startDate < :to and b.endDate > :from
            order by b.startDate
            """)
    List<StayBlock> overlapping(@Param("propertyIds") Collection<Long> propertyIds, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<StayBlock> findByPropertyIdAndEndDateAfterOrderByStartDate(Long propertyId, LocalDate after);
}
