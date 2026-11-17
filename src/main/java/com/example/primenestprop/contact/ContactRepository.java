package com.example.primenestprop.contact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByClientId(String clientId);

    Optional<Contact> findByOrganisationIdAndPhoneNormalised(Long organisationId, String phoneNormalised);

    Optional<Contact> findFirstByOrganisationIdAndEmailIgnoreCase(Long organisationId, String email);

    @Query("""
            select c from Contact c where c.organisationId = :org
              and (:q is null or lower(c.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or c.phoneNormalised like concat('%', cast(:q as string), '%')
                   or lower(c.email) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.tags) like lower(concat('%', cast(:q as string), '%')))
            order by c.updatedAt desc, c.id desc
            """)
    List<Contact> search(@Param("org") Long organisationId, @Param("q") String q, Pageable pageable);
}
