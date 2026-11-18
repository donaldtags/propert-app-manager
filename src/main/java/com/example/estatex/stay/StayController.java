package com.example.estatex.stay;

import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyDtos.PropertyResponse;
import com.example.estatex.property.PropertyService;
import com.example.estatex.user.AppUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stays")
class StayController {
    private static final int SEARCH_CANDIDATES = 500;

    private final StayService service;
    private final PropertyService properties;

    StayController(StayService service, PropertyService properties) {
        this.service = service;
        this.properties = properties;
    }

    /** Short stays free on the given dates for the given party (public). */
    @GetMapping("/search")
    ResponseEntity<List<PropertyResponse>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String suburb,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Property> candidates = properties.searchPage(com.example.estatex.property.ListingType.SHORT_STAY, city, suburb,
                minPrice, maxPrice, bedrooms, null, null, null, null, null, null, null, null, null, null, null, null,
                0, SEARCH_CANDIDATES).getContent();
        List<Property> free = service.availableAmong(candidates, checkIn, checkOut, guests);
        int pageSize = Math.min(Math.max(size, 1), 50);
        int from = Math.min(Math.max(page, 0) * pageSize, free.size());
        List<PropertyResponse> body = free.subList(from, Math.min(from + pageSize, free.size())).stream()
                .map(PropertyResponse::from).toList();
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(free.size())).body(body);
    }

    @GetMapping("/{propertyId}")
    StayDtos.SettingsResponse settings(@PathVariable Long propertyId) {
        return service.settings(propertyId);
    }

    @PutMapping("/{propertyId}/settings")
    StayDtos.SettingsResponse updateSettings(@PathVariable Long propertyId, @Valid @RequestBody StayDtos.UpdateSettingsRequest r,
                                             @AuthenticationPrincipal AppUser user) {
        return service.updateSettings(propertyId, r, user);
    }

    @GetMapping("/{propertyId}/availability")
    List<StayDtos.BusyRange> availability(@PathVariable Long propertyId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.busy(propertyId, from, to);
    }

    @GetMapping("/{propertyId}/quote")
    StayDtos.Quote quote(@PathVariable Long propertyId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                         @RequestParam(defaultValue = "1") int guests) {
        return service.quote(propertyId, checkIn, checkOut, guests);
    }

    @PostMapping("/{propertyId}/bookings")
    StayDtos.BookingResponse book(@PathVariable Long propertyId, @Valid @RequestBody StayDtos.BookRequest r,
                                  @AuthenticationPrincipal AppUser user) {
        return service.book(propertyId, r, user);
    }

    @PostMapping("/{propertyId}/blocks")
    StayDtos.BlockResponse block(@PathVariable Long propertyId, @Valid @RequestBody StayDtos.BlockRequest r,
                                 @AuthenticationPrincipal AppUser user) {
        return service.block(propertyId, r, user);
    }

    @DeleteMapping("/{propertyId}/blocks/{blockId}")
    ResponseEntity<Void> unblock(@PathVariable Long propertyId, @PathVariable Long blockId, @AuthenticationPrincipal AppUser user) {
        service.unblock(propertyId, blockId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookings/mine")
    List<StayDtos.BookingResponse> mine(@AuthenticationPrincipal AppUser user) {
        return service.mine(user);
    }

    @GetMapping("/hosting")
    StayDtos.HostingOverview hosting(@AuthenticationPrincipal AppUser user) {
        return service.hosting(user);
    }

    @GetMapping("/bookings/{id}")
    StayDtos.BookingResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUser user) {
        return service.get(id, user);
    }

    @PostMapping("/bookings/{id}/accept")
    StayDtos.BookingResponse accept(@PathVariable Long id, @Valid @RequestBody(required = false) StayDtos.DecisionRequest r,
                                    @AuthenticationPrincipal AppUser user) {
        return service.accept(id, r, user);
    }

    @PostMapping("/bookings/{id}/decline")
    StayDtos.BookingResponse decline(@PathVariable Long id, @Valid @RequestBody(required = false) StayDtos.DecisionRequest r,
                                     @AuthenticationPrincipal AppUser user) {
        return service.decline(id, r, user);
    }

    @PostMapping("/bookings/{id}/cancel")
    StayDtos.BookingResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) StayDtos.DecisionRequest r,
                                    @AuthenticationPrincipal AppUser user) {
        return service.cancel(id, r, user);
    }

    @PostMapping("/bookings/{id}/mark-paid")
    StayDtos.BookingResponse markPaid(@PathVariable Long id, @Valid @RequestBody StayDtos.MarkPaidRequest r,
                                      @AuthenticationPrincipal AppUser user) {
        return service.markPaid(id, r, user);
    }

    @PostMapping("/bookings/{id}/pay")
    PaymentDtos.CheckoutResponse pay(@PathVariable Long id, @Valid @RequestBody StayDtos.PayRequest r,
                                     @AuthenticationPrincipal AppUser user) {
        return service.pay(id, r.provider(), user);
    }
}
