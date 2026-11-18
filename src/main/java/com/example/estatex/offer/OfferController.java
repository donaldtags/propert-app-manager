package com.example.estatex.offer;

import com.example.estatex.offer.OfferDtos.OfferResponse;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{slug}")
public class OfferController {
    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @GetMapping("/listings/{propertyId}/offers")
    List<OfferResponse> list(@PathVariable String slug, @PathVariable Long propertyId, @AuthenticationPrincipal AppUser user) {
        return service.forListing(slug, propertyId, user);
    }

    @PostMapping("/listings/{propertyId}/offers")
    OfferResponse record(@PathVariable String slug, @PathVariable Long propertyId,
                         @Valid @RequestBody OfferDtos.RecordOfferRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.record(slug, propertyId, request, user));
    }

    @PostMapping("/offers/{offerId}/present")
    OfferResponse present(@PathVariable String slug, @PathVariable Long offerId,
                          @Valid @RequestBody OfferDtos.PresentRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.present(slug, offerId, request.via(), user));
    }

    @PostMapping("/offers/{offerId}/owner-response")
    OfferResponse respond(@PathVariable String slug, @PathVariable Long offerId,
                          @Valid @RequestBody OfferDtos.OwnerResponseRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.respond(slug, offerId, request, user));
    }

    @PostMapping("/offers/{offerId}/withdraw")
    OfferResponse withdraw(@PathVariable String slug, @PathVariable Long offerId,
                           @RequestBody(required = false) OfferDtos.WithdrawRequest request, @AuthenticationPrincipal AppUser user) {
        return service.response(service.withdraw(slug, offerId, request == null ? null : request.note(), user));
    }
}
