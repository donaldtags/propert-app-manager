package com.example.estatex.application;

import com.example.estatex.property.Property;
import com.example.estatex.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalApplicationRepository extends JpaRepository<RentalApplication, Long> {

    @EntityGraph(attributePaths = {"property", "applicant"})
    Optional<RentalApplication> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"property", "applicant"})
    List<RentalApplication> findByApplicantOrderByCreatedAtDesc(AppUser applicant);

    @EntityGraph(attributePaths = {"property", "applicant"})
    Optional<RentalApplication> findFirstByPropertyAndApplicantAndStatusNotOrderByCreatedAtDesc(
            Property property, AppUser applicant, ApplicationStatus excludedStatus);

    @Query("""
            select a from RentalApplication a
            where a.property.landlord.id = :userId or a.property.agent.id = :userId
            order by a.createdAt desc
            """)
    @EntityGraph(attributePaths = {"property", "applicant"})
    List<RentalApplication> findForOwner(@Param("userId") Long userId);
}
