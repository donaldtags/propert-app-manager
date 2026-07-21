package com.example.primenestprop.neighbourhood;

import com.example.primenestprop.user.AppUser;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NeighbourhoodService {
    private final NeighbourhoodProfileRepository repository;

    public NeighbourhoodService(NeighbourhoodProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<NeighbourhoodProfile> find(String city, String suburb) {
        return repository.findByCityIgnoreCaseAndSuburbIgnoreCase(city, suburb);
    }

    @Transactional
    public NeighbourhoodProfile upsert(NeighbourhoodDtos.UpsertNeighbourhoodRequest request, AppUser admin) {
        NeighbourhoodProfile profile = repository
                .findByCityIgnoreCaseAndSuburbIgnoreCase(request.city(), request.suburb())
                .orElseGet(NeighbourhoodProfile::new);
        profile.setCity(request.city());
        profile.setSuburb(request.suburb());
        profile.setSchoolsNote(request.schoolsNote());
        profile.setHospitalsNote(request.hospitalsNote());
        profile.setTransportNote(request.transportNote());
        profile.setShoppingNote(request.shoppingNote());
        profile.setGeneralNote(request.generalNote());
        profile.setUpdatedByUserId(admin.getId());
        profile.setUpdatedAt(Instant.now());
        return repository.save(profile);
    }
}
