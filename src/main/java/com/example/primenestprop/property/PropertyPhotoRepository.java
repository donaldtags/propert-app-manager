package com.example.primenestprop.property;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, Long> {
    List<PropertyPhoto> findByPropertyOrderByPrimaryPhotoDescSortOrderAscIdAsc(Property property);
}
