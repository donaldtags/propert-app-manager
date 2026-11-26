package com.example.estatex.intake;

import static com.example.estatex.common.AuditService.entry;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.AuditService;
import com.example.estatex.common.BusinessTime;
import com.example.estatex.common.DocumentStore;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgAccessService.OrgContext;
import com.example.estatex.organisation.OrgPermission;
import com.example.estatex.property.ListingType;
import com.example.estatex.property.Property;
import com.example.estatex.property.PropertyRepository;
import com.example.estatex.property.PropertyStatus;
import com.example.estatex.user.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Listing intake: the condition inspection (with photos) and the price recommendation. */
@Service
public class IntakeService {
    /** Default inspection checklist; the device can add items. */
    static final List<IntakeDtos.InspectionItem> DEFAULT_ITEMS = List.of(
            item("ROOF", "Roof and gutters"), item("WALLS", "Walls (cracks, damp)"), item("PLUMBING", "Plumbing and geyser"),
            item("ELECTRICAL", "Electrical (DB board, wiring)"), item("WATER", "Water supply (borehole, tanks, council)"),
            item("POWER_BACKUP", "Solar / inverter / generator"), item("SECURITY", "Security (wall, gate, electric fence)"),
            item("KITCHEN", "Kitchen"), item("BATHROOMS", "Bathrooms"), item("FLOORS", "Floors"),
            item("WINDOWS_DOORS", "Windows and doors"), item("GROUNDS", "Garden and grounds"),
            item("OUTBUILDINGS", "Cottage / staff quarters / outbuildings"), item("PARKING", "Parking and driveway"));

    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final List<PropertyStatus> COMPARABLE_STATUSES = List.of(
            PropertyStatus.AVAILABLE, PropertyStatus.UNDER_OFFER, PropertyStatus.REPORTED_SOLD, PropertyStatus.OCCUPIED);

    private final OrgAccessService access;
    private final PropertyRepository properties;
    private final ListingInspectionRepository inspections;
    private final InspectionPhotoRepository photos;
    private final PricingRecommendationRepository recommendations;
    private final DocumentStore store;
    private final AuditService audit;
    private final BusinessTime time;
    private final ObjectMapper json;

    public IntakeService(OrgAccessService access, PropertyRepository properties, ListingInspectionRepository inspections,
                         InspectionPhotoRepository photos, PricingRecommendationRepository recommendations, DocumentStore store,
                         AuditService audit, BusinessTime time, ObjectMapper json) {
        this.access = access;
        this.properties = properties;
        this.inspections = inspections;
        this.photos = photos;
        this.recommendations = recommendations;
        this.store = store;
        this.audit = audit;
        this.time = time;
        this.json = json;
    }

    // -- inspection ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public IntakeDtos.InspectionResponse inspection(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        Property p = listingIn(ctx, propertyId);
        return response(inspections.findByPropertyId(p.getId()).orElse(null), p.getId());
    }

    @Transactional
    public IntakeDtos.InspectionResponse saveInspection(String slug, Long propertyId, IntakeDtos.SaveInspectionRequest r, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        Property p = listingIn(ctx, propertyId);
        ListingInspection i = inspections.findByPropertyId(p.getId()).orElse(null);
        if (i == null) {
            i = new ListingInspection();
            i.setOrganisationId(ctx.organisation().getId());
            i.setPropertyId(p.getId());
        } else if (r.baseVersion() != null && !Objects.equals(r.baseVersion(), i.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Someone else saved this inspection since you started. Their version was kept; re-open it to add your changes.");
        }
        i.setItemsJson(json.writeValueAsString(r.items()));
        i.setStandSizeM2(r.standSizeM2());
        i.setFloorAreaM2(r.floorAreaM2());
        i.setNotes(r.notes());
        i.setInspectedOn(r.inspectedOn() == null ? time.today() : r.inspectedOn());
        i.setInspectedByUserId(user.getId());
        i.setUpdatedAt(Instant.now());
        inspections.saveAndFlush(i);
        return response(i, p.getId());
    }

    @Transactional
    public InspectionPhoto addPhoto(String slug, Long propertyId, MultipartFile file, String caption, String itemKey,
                                    String clientId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        if (clientId != null && !clientId.isBlank()) {
            InspectionPhoto existing = photos.findByClientId(clientId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        Property p = listingIn(ctx, propertyId);
        if (file == null || file.isEmpty() || !PHOTO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add a JPEG, PNG or WebP photo.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photos must be under 10 MB.");
        }
        String key = "inspections/" + ctx.organisation().getId() + "/" + p.getId() + "/" + UUID.randomUUID()
                + (file.getContentType().equals("image/png") ? ".png" : file.getContentType().equals("image/webp") ? ".webp" : ".jpg");
        store.put(key, file);
        InspectionPhoto photo = new InspectionPhoto();
        photo.setClientId(clientId == null || clientId.isBlank() ? null : clientId);
        photo.setOrganisationId(ctx.organisation().getId());
        photo.setPropertyId(p.getId());
        photo.setStorageKey(key);
        photo.setContentType(file.getContentType());
        photo.setCaption(caption == null || caption.isBlank() ? null : caption.trim());
        photo.setItemKey(itemKey == null || itemKey.isBlank() ? null : itemKey.trim());
        photo.setUploadedByUserId(user.getId());
        return photos.save(photo);
    }

    @Transactional(readOnly = true)
    public PhotoFile photo(String slug, Long photoId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        InspectionPhoto photo = photos.findById(photoId)
                .filter(x -> x.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found"));
        return new PhotoFile(store.locate(photo.getStorageKey()), photo.getContentType());
    }

    public record PhotoFile(DocumentStore.Located location, String contentType) {
    }

    @Transactional
    public void deletePhoto(String slug, Long photoId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        InspectionPhoto photo = photos.findById(photoId)
                .filter(x -> x.getOrganisationId().equals(ctx.organisation().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found"));
        store.delete(photo.getStorageKey());
        photos.delete(photo);
    }

    // -- pricing ----------------------------------------------------------------------------

    /**
     * Comparable ASKING prices from listings on the platform: same city, listing type and
     * currency, bedrooms within one, preferring the same suburb when there are at least three.
     * Achieved prices (Phase 4) aren't included yet, and the response says so.
     */
    @Transactional(readOnly = true)
    public IntakeDtos.PricingSuggestion pricing(String slug, Long propertyId, AppUser user) {
        OrgContext ctx = access.require(user, slug, OrgPermission.ORG_VIEW);
        Property p = listingIn(ctx, propertyId);
        long asking = cents(p.getPrice());
        List<Property> candidates = properties.findTop100ByListingTypeAndCityIgnoreCaseAndBedroomsBetweenAndStatusInAndIdNotOrderByCreatedAtDesc(
                p.getListingType(), p.getCity(), Math.max(0, p.getBedrooms() - 1), p.getBedrooms() + 1, COMPARABLE_STATUSES, p.getId());
        List<Property> sameCurrency = candidates.stream()
                .filter(c -> c.getPrice() != null && Objects.equals(c.getCurrency(), p.getCurrency())).toList();
        List<Property> sameSuburb = sameCurrency.stream().filter(c -> c.getSuburb().equalsIgnoreCase(p.getSuburb())).toList();
        boolean suburbOnly = sameSuburb.size() >= 3;
        List<Property> used = (suburbOnly ? sameSuburb : sameCurrency).stream().limit(20).toList();

        List<Long> prices = used.stream().map(c -> cents(c.getPrice())).sorted().toList();
        Long low = percentile(prices, 25);
        Long median = percentile(prices, 50);
        Long high = percentile(prices, 75);
        boolean enough = prices.size() >= 3;
        Long suggested = enough ? round(median, p.getListingType()) : null;
        String basis = prices.isEmpty()
                ? "No comparable listings yet in " + p.getCity() + " with " + p.getBedrooms() + " bedrooms (±1)."
                : prices.size() + " comparable asking price" + (prices.size() == 1 ? "" : "s") + " in "
                + (suburbOnly ? p.getSuburb() : p.getCity() + " (not enough in " + p.getSuburb() + ")")
                + ", " + (p.getBedrooms() - 1) + "–" + (p.getBedrooms() + 1) + " bedrooms. Asking prices, not achieved sale prices.";

        List<IntakeDtos.Comparable> comps = used.stream()
                .sorted(Comparator.comparing((Property c) -> !c.getSuburb().equalsIgnoreCase(p.getSuburb())))
                .map(c -> new IntakeDtos.Comparable(c.getId(), c.getTitle(), c.getSuburb(), c.getCity(), c.getBedrooms(),
                        c.getBathrooms(), cents(c.getPrice()), c.getCurrency(), c.getStatus().name(),
                        c.getTitleType() == null ? null : c.getTitleType().name(), c.getSuburb().equalsIgnoreCase(p.getSuburb()),
                        "Asking price, EstateX listing"))
                .toList();
        IntakeDtos.SavedRecommendation latest = recommendations.findFirstByPropertyIdOrderByCreatedAtDesc(p.getId())
                .map(IntakeService::saved).orElse(null);
        return new IntakeDtos.PricingSuggestion(p.getId(), asking, p.getCurrency(), prices.size(), low, median, high, suggested,
                enough, basis, comps, latest);
    }

    @Transactional
    public IntakeDtos.SavedRecommendation saveRecommendation(String slug, Long propertyId, IntakeDtos.SaveRecommendationRequest r,
                                                             AppUser user) {
        IntakeDtos.PricingSuggestion s = pricing(slug, propertyId, user);
        OrgContext ctx = access.require(user, slug, OrgPermission.LISTING_EDIT);
        PricingRecommendation rec = new PricingRecommendation();
        rec.setOrganisationId(ctx.organisation().getId());
        rec.setPropertyId(propertyId);
        rec.setRecommendedCents(r.recommendedCents());
        rec.setLowCents(s.lowCents());
        rec.setMedianCents(s.medianCents());
        rec.setHighCents(s.highCents());
        rec.setCurrency(s.currency());
        rec.setComparableCount(s.comparableCount());
        rec.setComparablesJson(json.writeValueAsString(s.comparables()));
        rec.setRationale(r.rationale());
        rec.setCreatedByUserId(user.getId());
        recommendations.save(rec);
        audit.record(user, entry("PRICING_RECOMMENDED", "Property", propertyId).organisation(rec.getOrganisationId())
                .money(r.recommendedCents(), s.currency())
                .metadata(Map.of("comparables", s.comparableCount(), "askingCents", s.askingCents())));
        return saved(rec);
    }

    /** Latest saved recommendation, for the seller report. */
    @Transactional(readOnly = true)
    public PricingRecommendation latestRecommendation(Long propertyId) {
        return recommendations.findFirstByPropertyIdOrderByCreatedAtDesc(propertyId).orElse(null);
    }

    // -- helpers ----------------------------------------------------------------------------

    private IntakeDtos.InspectionResponse response(ListingInspection i, Long propertyId) {
        List<IntakeDtos.PhotoResponse> photoList = photos.findByPropertyIdOrderByCreatedAtAsc(propertyId).stream()
                .map(ph -> new IntakeDtos.PhotoResponse(ph.getId(), ph.getClientId(), ph.getCaption(), ph.getItemKey(),
                        ph.getContentType(), ph.getCreatedAt()))
                .toList();
        if (i == null) {
            return new IntakeDtos.InspectionResponse(DEFAULT_ITEMS, null, null, null, null, null, null, photoList, 0, 0);
        }
        List<IntakeDtos.InspectionItem> items = i.getItemsJson() == null ? new ArrayList<>()
                : json.readValue(i.getItemsJson(), new TypeReference<List<IntakeDtos.InspectionItem>>() { });
        int rated = (int) items.stream().filter(x -> x.condition() != null).count();
        int poor = (int) items.stream().filter(x -> x.condition() == InspectionCondition.POOR).count();
        return new IntakeDtos.InspectionResponse(items, i.getStandSizeM2(), i.getFloorAreaM2(), i.getNotes(), i.getInspectedOn(),
                i.getInspectedByUserId(), i.getVersion(), photoList, rated, poor);
    }

    private static IntakeDtos.SavedRecommendation saved(PricingRecommendation r) {
        return new IntakeDtos.SavedRecommendation(r.getId(), r.getRecommendedCents(), r.getLowCents(), r.getMedianCents(),
                r.getHighCents(), r.getComparableCount(), r.getRationale(), r.getCreatedByUserId(), r.getCreatedAt());
    }

    private Property listingIn(OrgContext ctx, Long propertyId) {
        return properties.findById(propertyId)
                .filter(p -> ctx.organisation().getId().equals(p.getOrganisationId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    static long cents(BigDecimal amount) {
        return amount == null ? 0 : amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** Nearest-rank percentile of a sorted list. */
    static Long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) {
            return null;
        }
        int rank = (int) Math.ceil(pct / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    /** Sale prices to the nearest US$1,000; rents to the nearest US$10. */
    static Long round(Long cents, ListingType type) {
        if (cents == null) {
            return null;
        }
        long step = type == ListingType.SALE ? 100_000 : 1_000;
        return Math.round((double) cents / step) * step;
    }

    private static IntakeDtos.InspectionItem item(String key, String label) {
        return new IntakeDtos.InspectionItem(key, label, null, null);
    }
}
