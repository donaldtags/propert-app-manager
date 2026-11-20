package com.example.estatex.property;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, Long> {
    List<PropertyPhoto> findByPropertyOrderByPrimaryPhotoDescSortOrderAscIdAsc(Property property);

    /** Ids of listings owned by someone else that already use a photo with exactly these bytes. */
    @org.springframework.data.jpa.repository.Query("select distinct ph.property.id from PropertyPhoto ph "
            + "where ph.contentSha256 = :sha256 and ph.property.landlord.id <> :ownerId")
    List<Long> listingsOfOtherOwnersWithPhoto(@org.springframework.data.repository.query.Param("sha256") String sha256,
                                              @org.springframework.data.repository.query.Param("ownerId") Long ownerId);
}
