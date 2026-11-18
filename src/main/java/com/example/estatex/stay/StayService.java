package com.example.estatex.stay;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.notification.NotificationService;
import com.example.estatex.notification.NotificationType;
import com.example.estatex.payment.Payment;
import com.example.estatex.payment.PaymentDtos;
import com.example.estatex.payment.PaymentPurpose;
import com.example.estatex.payment.PaymentService;
import com.example.estatex.payment.PaymentSucceededEvent;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyPhoto;
import com.example.estatex.property.PropertyService;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short stays (BnB): calendar, quotes, booking requests, host decisions, payment and expiry.
 *
 * <p>Double bookings are prevented by locking the listing row while checking overlaps and saving,
 * so two guests asking for the same nights at the same moment can't both get them.
 */
@Service
public class StayService {
    private static final int MAX_CALENDAR_DAYS = 400;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaySettingsRepository settingsRepo;
    private final StayBookingRepository bookings;
    private final StayBlockRepository blocks;
    private final PropertyService properties;
    private final PaymentService payments;
    private final NotificationService notifications;
    private final BusinessTime time;
    private final EntityManager em;
    private final BigDecimal serviceFeePercent;
    private final Duration holdDuration;

    public StayService(StaySettingsRepository settingsRepo, StayBookingRepository bookings, StayBlockRepository blocks,
                       PropertyService properties, PaymentService payments, NotificationService notifications,
                       BusinessTime time, EntityManager em,
                       @Value("${app.stays.service-fee-percent:0}") BigDecimal serviceFeePercent,
                       @Value("${app.stays.hold-hours:24}") long holdHours) {
        this.settingsRepo = settingsRepo;
        this.bookings = bookings;
        this.blocks = blocks;
        this.properties = properties;
        this.payments = payments;
        this.notifications = notifications;
        this.time = time;
        this.em = em;
        this.serviceFeePercent = serviceFeePercent;
        this.holdDuration = Duration.ofHours(holdHours);
    }

    // ---------------------------------------------------------------- listing settings & calendar

    @Transactional(readOnly = true)
    public StayDtos.SettingsResponse settings(Long propertyId) {
        Property p = requireStay(propertyId, null);
        return toResponse(p, settingsFor(propertyId));
    }

    @Transactional
    public StayDtos.SettingsResponse updateSettings(Long propertyId, StayDtos.UpdateSettingsRequest r, AppUser actor) {
        Property p = requireStay(propertyId, actor);
        requireHost(p, actor);
        if (r.minNights() > r.maxNights()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Minimum nights can't be more than maximum nights");
        }
        requireTime(r.checkInTime(), "Check-in time");
        requireTime(r.checkOutTime(), "Check-out time");
        StaySettings s = settingsRepo.findById(propertyId).orElseGet(() -> StaySettings.defaultsFor(propertyId));
        s.setMaxGuests(r.maxGuests());
        s.setMinNights(r.minNights());
        s.setMaxNights(r.maxNights());
        s.setCleaningFee(r.cleaningFee().setScale(2, RoundingMode.HALF_UP));
        s.setCheckInTime(r.checkInTime());
        s.setCheckOutTime(r.checkOutTime());
        s.setInstantBook(r.instantBook());
        s.setHouseRules(blankToNull(r.houseRules()));
        s.setUpdatedAt(Instant.now());
        return toResponse(p, settingsRepo.save(s));
    }

    /** Busy nights between from and to (exclusive), merged; no guest details. */
    @Transactional(readOnly = true)
    public List<StayDtos.BusyRange> busy(Long propertyId, LocalDate from, LocalDate to) {
        requireStay(propertyId, null);
        LocalDate start = from == null ? time.today() : from;
        LocalDate end = to == null ? start.plusDays(180) : to;
        if (!end.isAfter(start) || ChronoUnit.DAYS.between(start, end) > MAX_CALENDAR_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a calendar range of up to " + MAX_CALENDAR_DAYS + " days");
        }
        List<StayDtos.BusyRange> ranges = new ArrayList<>();
        bookings.overlapping(List.of(propertyId), start, end, StayBookingStatus.HOLDING)
                .forEach(b -> ranges.add(new StayDtos.BusyRange(b.getCheckIn(), b.getCheckOut())));
        blocks.overlapping(List.of(propertyId), start, end)
                .forEach(b -> ranges.add(new StayDtos.BusyRange(b.getStartDate(), b.getEndDate())));
        return merge(ranges);
    }

    @Transactional(readOnly = true)
    public StayDtos.Quote quote(Long propertyId, LocalDate checkIn, LocalDate checkOut, int guests) {
        Property p = requireStay(propertyId, null);
        StaySettings s = settingsFor(propertyId);
        String problem = validateStay(p, s, checkIn, checkOut, guests);
        if (problem == null && isBusy(propertyId, checkIn, checkOut)) {
            problem = "Those dates aren't available";
        }
        return price(p, s, checkIn, checkOut, problem);
    }

    /** SHORT_STAY listings matching the usual filters that can take this party on these dates. */
    @Transactional(readOnly = true)
    public List<Property> availableAmong(List<Property> candidates, LocalDate checkIn, LocalDate checkOut, Integer guests) {
        if (candidates.isEmpty()) return candidates;
        List<Long> ids = candidates.stream().map(Property::getId).toList();
        Set<Long> busyIds = new java.util.HashSet<>();
        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            bookings.overlapping(ids, checkIn, checkOut, StayBookingStatus.HOLDING).forEach(b -> busyIds.add(b.getProperty().getId()));
            blocks.overlapping(ids, checkIn, checkOut).forEach(b -> busyIds.add(b.getPropertyId()));
        }
        Map<Long, StaySettings> settings = settingsRepo.findByPropertyIdIn(ids).stream()
                .collect(Collectors.toMap(StaySettings::getPropertyId, Function.identity()));
        int nights = checkIn != null && checkOut != null ? (int) ChronoUnit.DAYS.between(checkIn, checkOut) : 0;
        return candidates.stream()
                .filter(p -> !busyIds.contains(p.getId()))
                .filter(p -> {
                    StaySettings s = settings.getOrDefault(p.getId(), StaySettings.defaultsFor(p.getId()));
                    if (guests != null && guests > s.getMaxGuests()) return false;
                    return nights == 0 || (nights >= s.getMinNights() && nights <= s.getMaxNights());
                })
                .toList();
    }

    // ---------------------------------------------------------------- booking lifecycle

    @Transactional
    public StayDtos.BookingResponse book(Long propertyId, StayDtos.BookRequest r, AppUser guest) {
        if (guest == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to book");
        // Lock the listing so overlapping requests are checked one at a time.
        Property p = em.find(Property.class, propertyId, LockModeType.PESSIMISTIC_WRITE);
        if (p == null || p.getListingType() != ListingType.SHORT_STAY || p.getStatus() != PropertyStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This stay isn't open for booking");
        }
        if (isHost(p, guest)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't book your own listing");
        }
        StaySettings s = settingsFor(propertyId);
        String problem = validateStay(p, s, r.checkIn(), r.checkOut(), r.guests());
        if (problem != null) throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        if (isBusy(propertyId, r.checkIn(), r.checkOut())) {
            throw new ApiException(HttpStatus.CONFLICT, "Sorry, someone has just taken some of those nights. Please pick other dates.");
        }
        StayDtos.Quote q = price(p, s, r.checkIn(), r.checkOut(), null);
        StayBooking b = new StayBooking();
        b.setReference(newReference());
        b.setProperty(p);
        b.setGuest(guest);
        b.setHost(hostOf(p));
        b.setCheckIn(r.checkIn());
        b.setCheckOut(r.checkOut());
        b.setGuests(r.guests());
        b.setNights(q.nights());
        b.setNightlyRate(q.nightlyRate());
        b.setCleaningFee(q.cleaningFee());
        b.setServiceFee(q.serviceFee());
        b.setTotal(q.total());
        b.setCurrency(q.currency());
        b.setGuestMessage(blankToNull(r.message()));
        b.setStatus(s.isInstantBook() ? StayBookingStatus.AWAITING_PAYMENT : StayBookingStatus.PENDING);
        if (s.isInstantBook()) b.setDecidedAt(Instant.now());
        b.setHoldExpiresAt(Instant.now().plus(holdDuration));
        b = bookings.save(b);
        notifications.notify(b.getHost(), NotificationType.STAY_BOOKING, b.getId(),
                s.isInstantBook() ? "New booking " + b.getReference() : "Booking request " + b.getReference(),
                guest.getFullName() + " " + (s.isInstantBook() ? "booked" : "asked to book") + " " + p.getTitle()
                        + " for " + b.getNights() + " night" + (b.getNights() == 1 ? "" : "s") + " from " + b.getCheckIn() + ".");
        return toResponse(b, settingsFor(propertyId));
    }

    @Transactional
    public StayDtos.BookingResponse accept(Long bookingId, StayDtos.DecisionRequest r, AppUser actor) {
        StayBooking b = requireForHost(bookingId, actor);
        requireStatus(b, StayBookingStatus.PENDING);
        b.setStatus(StayBookingStatus.AWAITING_PAYMENT);
        b.setHostNote(r == null ? null : blankToNull(r.note()));
        b.setDecidedAt(Instant.now());
        b.setHoldExpiresAt(Instant.now().plus(holdDuration));
        notifications.notify(b.getGuest(), NotificationType.STAY_BOOKING, b.getId(), "Your stay is accepted",
                "The host accepted booking " + b.getReference() + " at " + b.getProperty().getTitle()
                        + ". Pay within " + holdDuration.toHours() + " hours to confirm your dates.");
        return toResponse(b, settingsFor(b.getProperty().getId()));
    }

    @Transactional
    public StayDtos.BookingResponse decline(Long bookingId, StayDtos.DecisionRequest r, AppUser actor) {
        StayBooking b = requireForHost(bookingId, actor);
        requireStatus(b, StayBookingStatus.PENDING);
        b.setStatus(StayBookingStatus.DECLINED);
        b.setHostNote(r == null ? null : blankToNull(r.note()));
        b.setDecidedAt(Instant.now());
        notifications.notify(b.getGuest(), NotificationType.STAY_BOOKING, b.getId(), "Booking request declined",
                "The host couldn't take booking " + b.getReference() + " at " + b.getProperty().getTitle() + ".");
        return toResponse(b, settingsFor(b.getProperty().getId()));
    }

    @Transactional
    public StayDtos.BookingResponse cancel(Long bookingId, StayDtos.DecisionRequest r, AppUser actor) {
        StayBooking b = bookings.findWithPartiesById(bookingId).orElseThrow(() -> notFound());
        boolean asGuest = b.getGuest().getId().equals(actor.getId());
        boolean asHost = !asGuest && (isHost(b.getProperty(), actor) || actor.hasPermission(Permission.ADMIN_OVERRIDE));
        if (!asGuest && !asHost) throw notFound();
        if (!b.getStatus().holdsDates()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking is already " + b.getStatus().name().toLowerCase().replace('_', ' '));
        }
        if (b.getStatus() == StayBookingStatus.CONFIRMED && !b.getCheckIn().isAfter(time.today()) && asGuest) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The stay has started; contact the host to change it");
        }
        b.setStatus(StayBookingStatus.CANCELLED);
        b.setCancelledAt(Instant.now());
        b.setCancelledBy(asGuest ? "GUEST" : "HOST");
        if (r != null && blankToNull(r.note()) != null) b.setHostNote(r.note().trim());
        AppUser other = asGuest ? b.getHost() : b.getGuest();
        notifications.notify(other, NotificationType.STAY_BOOKING, b.getId(), "Booking " + b.getReference() + " cancelled",
                (asGuest ? "The guest" : "The host") + " cancelled the stay at " + b.getProperty().getTitle()
                        + " (" + b.getCheckIn() + " to " + b.getCheckOut() + ")."
                        + (b.getPaidAt() != null ? " Refunds are arranged directly between guest and host." : ""));
        return toResponse(b, settingsFor(b.getProperty().getId()));
    }

    /** Host records payment received outside the app (cash, EcoCash, bank transfer). */
    @Transactional
    public StayDtos.BookingResponse markPaid(Long bookingId, StayDtos.MarkPaidRequest r, AppUser actor) {
        StayBooking b = requireForHost(bookingId, actor);
        if (b.getStatus() != StayBookingStatus.AWAITING_PAYMENT && b.getStatus() != StayBookingStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only unpaid bookings can be marked as paid");
        }
        confirm(b, r.method().trim());
        return toResponse(b, settingsFor(b.getProperty().getId()));
    }

    /** Guest pays online through the existing payment gateways. */
    @Transactional
    public PaymentDtos.CheckoutResponse pay(Long bookingId, String provider, AppUser guest) {
        StayBooking b = bookings.findWithPartiesById(bookingId).orElseThrow(() -> notFound());
        if (!b.getGuest().getId().equals(guest.getId())) throw notFound();
        requireStatus(b, StayBookingStatus.AWAITING_PAYMENT);
        Payment payment = payments.create(
                new PaymentDtos.CreatePaymentRequest(b.getHost().getId(), b.getProperty().getId(), null, b.getTotal(),
                        b.getCurrency(), provider, "Stay " + b.getReference() + ": " + b.getProperty().getTitle()),
                guest, PaymentPurpose.STAY_BOOKING, "stayBookingId=" + b.getId(), null);
        b.setPaymentId(payment.getId());
        return payments.initiateCheckout(payment.getId(), provider, guest);
    }

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        Payment payment = event.payment();
        if (payment.getPurposeType() != PaymentPurpose.STAY_BOOKING || payment.getMetadata() == null) return;
        String raw = payment.getMetadata().replace("stayBookingId=", "").trim();
        Long id;
        try {
            id = Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return;
        }
        bookings.findWithPartiesById(id).ifPresent(b -> {
            if (b.getStatus() == StayBookingStatus.AWAITING_PAYMENT || b.getStatus() == StayBookingStatus.PENDING
                    || b.getStatus() == StayBookingStatus.EXPIRED) {
                b.setPaymentId(payment.getId());
                confirm(b, "online");
            }
        });
    }

    @Transactional(readOnly = true)
    public List<StayDtos.BookingResponse> mine(AppUser guest) {
        return withSettings(bookings.findByGuestIdOrderByCheckInDesc(guest.getId()));
    }

    @Transactional(readOnly = true)
    public StayDtos.BookingResponse get(Long id, AppUser actor) {
        StayBooking b = bookings.findWithPartiesById(id).orElseThrow(() -> notFound());
        boolean allowed = b.getGuest().getId().equals(actor.getId()) || isHost(b.getProperty(), actor)
                || actor.hasPermission(Permission.ADMIN_OVERRIDE);
        if (!allowed) throw notFound();
        return toResponse(b, settingsFor(b.getProperty().getId()));
    }

    @Transactional(readOnly = true)
    public StayDtos.HostingOverview hosting(AppUser host) {
        List<StayDtos.BookingResponse> list = withSettings(bookings.findByHostIdOrderByCheckInDesc(host.getId()));
        List<Property> mine = new ArrayList<>(properties.forLandlord(host.getId()));
        properties.forAgent(host.getId()).stream().filter(p -> mine.stream().noneMatch(m -> m.getId().equals(p.getId()))).forEach(mine::add);
        List<Property> stays = mine.stream().filter(p -> p.getListingType() == ListingType.SHORT_STAY)
                .sorted(Comparator.comparing(Property::getId)).toList();
        Map<Long, StaySettings> settings = settingsRepo.findByPropertyIdIn(stays.stream().map(Property::getId).toList()).stream()
                .collect(Collectors.toMap(StaySettings::getPropertyId, Function.identity()));
        LocalDate today = time.today();
        List<StayDtos.HostedStay> hosted = stays.stream().map(p -> new StayDtos.HostedStay(
                p.getId(), p.getTitle(), p.getCity(), p.getPrice(), p.getCurrency(),
                toResponse(p, settings.getOrDefault(p.getId(), StaySettings.defaultsFor(p.getId()))),
                blocks.findByPropertyIdAndEndDateAfterOrderByStartDate(p.getId(), today).stream().map(StayDtos.BlockResponse::from).toList()
        )).toList();
        return new StayDtos.HostingOverview(list, hosted);
    }

    @Transactional
    public StayDtos.BlockResponse block(Long propertyId, StayDtos.BlockRequest r, AppUser actor) {
        Property p = em.find(Property.class, propertyId, LockModeType.PESSIMISTIC_WRITE);
        if (p == null || p.getListingType() != ListingType.SHORT_STAY) throw new ApiException(HttpStatus.NOT_FOUND, "Stay not found");
        requireHost(p, actor);
        if (!r.end().isAfter(r.start())) throw new ApiException(HttpStatus.BAD_REQUEST, "The end date must be after the start date");
        if (!bookings.overlapping(List.of(propertyId), r.start(), r.end(), StayBookingStatus.HOLDING).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Guests already hold some of those nights; cancel or decline them first");
        }
        StayBlock b = new StayBlock();
        b.setPropertyId(propertyId);
        b.setStartDate(r.start());
        b.setEndDate(r.end());
        b.setNote(blankToNull(r.note()));
        return StayDtos.BlockResponse.from(blocks.save(b));
    }

    @Transactional
    public void unblock(Long propertyId, Long blockId, AppUser actor) {
        Property p = requireStay(propertyId, actor);
        requireHost(p, actor);
        StayBlock b = blocks.findById(blockId).filter(x -> x.getPropertyId().equals(propertyId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Blocked dates not found"));
        blocks.delete(b);
    }

    /** Releases unanswered requests and unpaid holds; completes finished stays. */
    @Transactional
    public int housekeeping() {
        int changed = 0;
        for (StayBooking b : bookings.holdsExpiredBefore(Instant.now(),
                List.of(StayBookingStatus.PENDING.name(), StayBookingStatus.AWAITING_PAYMENT.name()))) {
            boolean wasRequest = b.getStatus() == StayBookingStatus.PENDING;
            b.setStatus(StayBookingStatus.EXPIRED);
            notifications.notify(b.getGuest(), NotificationType.STAY_BOOKING, b.getId(), "Booking " + b.getReference() + " expired",
                    wasRequest ? "The host didn't answer in time, so your dates were released."
                            : "Payment wasn't completed in time, so your dates were released.");
            changed++;
        }
        for (StayBooking b : bookings.finishedBy(time.today())) {
            b.setStatus(StayBookingStatus.COMPLETED);
            changed++;
        }
        return changed;
    }

    // ---------------------------------------------------------------- helpers

    private void confirm(StayBooking b, String via) {
        b.setStatus(StayBookingStatus.CONFIRMED);
        b.setPaidVia(via);
        b.setPaidAt(Instant.now());
        b.setHoldExpiresAt(null);
        String when = b.getCheckIn() + " to " + b.getCheckOut();
        notifications.notify(b.getGuest(), NotificationType.STAY_BOOKING, b.getId(), "Stay confirmed: " + b.getReference(),
                "You're booked at " + b.getProperty().getTitle() + ", " + when + ".");
        notifications.notify(b.getHost(), NotificationType.STAY_BOOKING, b.getId(), "Booking " + b.getReference() + " is paid",
                b.getGuest().getFullName() + " is confirmed for " + when + ".");
    }

    private String validateStay(Property p, StaySettings s, LocalDate checkIn, LocalDate checkOut, int guests) {
        if (checkIn == null || checkOut == null) return "Choose check-in and check-out dates";
        if (checkIn.isBefore(time.today())) return "Check-in can't be in the past";
        if (!checkOut.isAfter(checkIn)) return "Check-out must be after check-in";
        if (checkIn.isAfter(time.today().plusDays(MAX_CALENDAR_DAYS))) return "That's too far ahead to book";
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < s.getMinNights()) return "This stay needs at least " + s.getMinNights() + " night" + (s.getMinNights() == 1 ? "" : "s");
        if (nights > s.getMaxNights()) return "This stay allows at most " + s.getMaxNights() + " nights";
        if (guests < 1) return "At least one guest";
        if (guests > s.getMaxGuests()) return "This place sleeps up to " + s.getMaxGuests() + " guest" + (s.getMaxGuests() == 1 ? "" : "s");
        if (p.getPrice() == null || p.getPrice().signum() <= 0) return "The host hasn't set a nightly price yet";
        return null;
    }

    private StayDtos.Quote price(Property p, StaySettings s, LocalDate checkIn, LocalDate checkOut, String problem) {
        int nights = checkIn != null && checkOut != null && checkOut.isAfter(checkIn) ? (int) ChronoUnit.DAYS.between(checkIn, checkOut) : 0;
        BigDecimal rate = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal accommodation = rate.multiply(BigDecimal.valueOf(nights));
        BigDecimal cleaning = nights > 0 ? s.getCleaningFee().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal service = accommodation.multiply(serviceFeePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = accommodation.add(cleaning).add(service);
        return new StayDtos.Quote(problem == null, problem, nights, rate, accommodation, cleaning, service, total,
                p.getCurrency() == null ? "USD" : p.getCurrency(), s.isInstantBook());
    }

    private boolean isBusy(Long propertyId, LocalDate checkIn, LocalDate checkOut) {
        return !bookings.overlapping(List.of(propertyId), checkIn, checkOut, StayBookingStatus.HOLDING).isEmpty()
                || !blocks.overlapping(List.of(propertyId), checkIn, checkOut).isEmpty();
    }

    private Property requireStay(Long propertyId, AppUser viewer) {
        Property p = properties.requireVisible(propertyId, viewer);
        if (p.getListingType() != ListingType.SHORT_STAY) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This listing isn't a short stay");
        }
        return p;
    }

    private StaySettings settingsFor(Long propertyId) {
        return settingsRepo.findById(propertyId).orElseGet(() -> StaySettings.defaultsFor(propertyId));
    }

    private StayBooking requireForHost(Long bookingId, AppUser actor) {
        StayBooking b = bookings.findWithPartiesById(bookingId).orElseThrow(() -> notFound());
        if (!isHost(b.getProperty(), actor) && !actor.hasPermission(Permission.ADMIN_OVERRIDE)) throw notFound();
        return b;
    }

    private void requireHost(Property p, AppUser actor) {
        if (actor == null || (!isHost(p, actor) && !actor.hasPermission(Permission.ADMIN_OVERRIDE))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the host can manage this stay");
        }
    }

    private static boolean isHost(Property p, AppUser user) {
        return user != null && ((p.getLandlord() != null && p.getLandlord().getId().equals(user.getId()))
                || (p.getAgent() != null && p.getAgent().getId().equals(user.getId())));
    }

    private static AppUser hostOf(Property p) {
        return p.getAgent() != null ? p.getAgent() : p.getLandlord();
    }

    private static void requireStatus(StayBooking b, StayBookingStatus expected) {
        if (b.getStatus() != expected) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This booking is " + b.getStatus().name().toLowerCase().replace('_', ' '));
        }
    }

    private static void requireTime(String value, String label) {
        if (value == null || !value.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " must look like 14:00");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String newReference() {
        StringBuilder sb = new StringBuilder("ST-");
        for (int i = 0; i < 6; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    static List<StayDtos.BusyRange> merge(List<StayDtos.BusyRange> ranges) {
        List<StayDtos.BusyRange> sorted = ranges.stream().sorted(Comparator.comparing(StayDtos.BusyRange::start)).toList();
        List<StayDtos.BusyRange> merged = new ArrayList<>();
        for (StayDtos.BusyRange r : sorted) {
            if (!merged.isEmpty() && !r.start().isAfter(merged.getLast().end())) {
                StayDtos.BusyRange last = merged.removeLast();
                merged.add(new StayDtos.BusyRange(last.start(), r.end().isAfter(last.end()) ? r.end() : last.end()));
            } else {
                merged.add(r);
            }
        }
        return merged;
    }

    private List<StayDtos.BookingResponse> withSettings(List<StayBooking> list) {
        Map<Long, StaySettings> settings = settingsRepo.findByPropertyIdIn(
                list.stream().map(b -> b.getProperty().getId()).distinct().toList()).stream()
                .collect(Collectors.toMap(StaySettings::getPropertyId, Function.identity()));
        return list.stream().map(b -> toResponse(b, settings.getOrDefault(b.getProperty().getId(),
                StaySettings.defaultsFor(b.getProperty().getId())))).toList();
    }

    private StayDtos.SettingsResponse toResponse(Property p, StaySettings s) {
        return new StayDtos.SettingsResponse(p.getId(), p.getPrice(), p.getCurrency(), s.getMaxGuests(), s.getMinNights(),
                s.getMaxNights(), s.getCleaningFee(), s.getCheckInTime(), s.getCheckOutTime(), s.isInstantBook(), s.getHouseRules());
    }

    private StayDtos.BookingResponse toResponse(StayBooking b, StaySettings s) {
        Property p = b.getProperty();
        String cover = p.getPhotos().stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing((PropertyPhoto ph) -> !ph.isPrimaryPhoto()).thenComparingInt(PropertyPhoto::getSortOrder))
                .map(PropertyPhoto::getPhotoUrl).findFirst().orElse(null);
        boolean revealContacts = b.getStatus() == StayBookingStatus.CONFIRMED || b.getStatus() == StayBookingStatus.COMPLETED;
        return new StayDtos.BookingResponse(b.getId(), b.getReference(), b.getStatus(), p.getId(), p.getTitle(), p.getCity(),
                p.getSuburb(), cover, b.getCheckIn(), b.getCheckOut(), b.getNights(), b.getGuests(), b.getNightlyRate(),
                b.getCleaningFee(), b.getServiceFee(), b.getTotal(), b.getCurrency(), b.getGuestMessage(), b.getHostNote(),
                b.getHoldExpiresAt(), b.getPaidVia(), b.getCreatedAt(), s.getCheckInTime(), s.getCheckOutTime(),
                party(b.getGuest(), revealContacts), party(b.getHost(), revealContacts));
    }

    /** Phone and e-mail are shared once the stay is confirmed (paid). */
    private static StayDtos.Party party(AppUser u, boolean reveal) {
        return new StayDtos.Party(u.getId(), u.getFullName(), reveal ? u.getPhone() : null, reveal ? u.getEmail() : null);
    }
}
