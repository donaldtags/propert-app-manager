package com.example.primenestprop.property;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyInquiryRepository extends JpaRepository<PropertyInquiry, Long> {
    @Query("""
            select i from PropertyInquiry i
            where i.property.landlord.id = :userId or i.property.agent.id = :userId
            order by i.createdAt desc
            """)
    List<PropertyInquiry> findForOwner(@Param("userId") Long userId);
}
