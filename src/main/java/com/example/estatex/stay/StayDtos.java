package com.example.estatex.stay;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class StayDtos {
    private StayDtos() {}

    public record SettingsResponse(Long propertyId, BigDecimal nightlyRate, String currency, int maxGuests, int minNights,
                                   int maxNights, BigDecimal cleaningFee, String checkInTime, String checkOutTime,
                                   boolean instantBook, String houseRules) {}

    public record UpdateSettingsRequest(
            @NotNull @Min(1) @Max(50) Integer maxGuests,
            @NotNull @Min(1) @Max(365) Integer minNights,
            @NotNull @Min(1) @Max(365) Integer maxNights,
            @NotNull @PositiveOrZero BigDecimal cleaningFee,
            @NotNull @Size(min = 5, max = 5) String checkInTime,
            @NotNull @Size(min = 5, max = 5) String checkOutTime,
            boolean instantBook,
            @Size(max = 2000) String houseRules) {}

    /** A busy range on the public calendar: no guest details. End is exclusive. */
    public record BusyRange(LocalDate start, LocalDate end) {}

    public record Quote(boolean available, String unavailableReason, int nights, BigDecimal nightlyRate,
                        BigDecimal accommodation, BigDecimal cleaningFee, BigDecimal serviceFee, BigDecimal total,
                        String currency, boolean instantBook) {}

    public record BookRequest(@NotNull LocalDate checkIn, @NotNull LocalDate checkOut, @NotNull @Min(1) @Max(50) Integer guests,
                              @Size(max = 1000) String message) {}

    public record DecisionRequest(@Size(max = 1000) String note) {}

    public record MarkPaidRequest(@NotNull @Size(min = 2, max = 30) String method) {}

    public record PayRequest(@NotNull String provider) {}

    public record BlockRequest(@NotNull LocalDate start, @NotNull LocalDate end, @Size(max = 200) String note) {}

    public record BlockResponse(Long id, LocalDate start, LocalDate end, String note) {
        static BlockResponse from(StayBlock b) {
            return new BlockResponse(b.getId(), b.getStartDate(), b.getEndDate(), b.getNote());
        }
    }

    public record Party(Long id, String fullName, String phone, String email) {}

    public record BookingResponse(Long id, String reference, StayBookingStatus status, Long propertyId, String propertyTitle,
                                  String propertyCity, String propertySuburb, String coverPhotoUrl, LocalDate checkIn,
                                  LocalDate checkOut, int nights, int guests, BigDecimal nightlyRate, BigDecimal cleaningFee,
                                  BigDecimal serviceFee, BigDecimal total, String currency, String guestMessage,
                                  String hostNote, Instant holdExpiresAt, String paidVia, Instant createdAt,
                                  String checkInTime, String checkOutTime, Party guest, Party host) {}

    public record HostingOverview(List<BookingResponse> bookings, List<HostedStay> stays) {}

    public record HostedStay(Long propertyId, String title, String city, BigDecimal nightlyRate, String currency,
                             SettingsResponse settings, List<BlockResponse> blocks) {}
}
