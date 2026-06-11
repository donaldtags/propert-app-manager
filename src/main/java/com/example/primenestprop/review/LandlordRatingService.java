package com.example.primenestprop.review;

import com.example.primenestprop.common.ApiException;
import com.example.primenestprop.lease.Lease;
import com.example.primenestprop.lease.LeaseService;
import com.example.primenestprop.property.PropertyService;
import com.example.primenestprop.user.AppUser;
import com.example.primenestprop.user.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LandlordRatingService {
    private final LandlordRatingRepository ratings;
    private final UserService users;
    private final LeaseService leases;
    private final PropertyService properties;

    public LandlordRatingService(
            LandlordRatingRepository ratings,
            UserService users,
            LeaseService leases,
            PropertyService properties
    ) {
        this.ratings = ratings;
        this.users = users;
        this.leases = leases;
        this.properties = properties;
    }

    @Transactional
    public LandlordRating create(RatingDtos.CreateLandlordRatingRequest request, AppUser currentUser) {
        if (!currentUser.getId().equals(request.tenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only submit ratings as yourself");
        }
        AppUser landlord = users.require(request.landlordId());
        AppUser tenant = users.require(request.tenantId());
        Lease lease = request.leaseId() == null ? null : leases.require(request.leaseId());

        if (lease != null) {
            if (!lease.getTenant().getId().equals(tenant.getId()) || !lease.getLandlord().getId().equals(landlord.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Ratings must match a lease between this tenant and landlord");
            }
            ratings.findByLeaseAndTenant(lease, tenant).ifPresent(existing -> {
                throw new ApiException(HttpStatus.CONFLICT, "You have already rated this landlord for this lease");
            });
        }

        LandlordRating rating = new LandlordRating();
        rating.setLandlord(landlord);
        rating.setTenant(tenant);
        rating.setLease(lease);
        if (request.propertyId() != null) {
            rating.setProperty(properties.require(request.propertyId()));
        } else if (lease != null) {
            rating.setProperty(lease.getProperty());
        }
        rating.setRating(request.rating());
        rating.setComment(request.comment());
        return ratings.save(rating);
    }

    public List<LandlordRating> forLandlord(Long landlordId) {
        return ratings.findByLandlordOrderByCreatedAtDesc(users.require(landlordId));
    }
}
