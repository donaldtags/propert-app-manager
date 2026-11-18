package com.example.estatex.property;

import com.example.estatex.common.ApiException;
import com.example.estatex.common.ObjectStorageService;
import com.example.estatex.user.AppUser;
import com.example.estatex.user.Permission;
import com.example.estatex.user.UserRole;
import com.example.estatex.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.example.estatex.organisation.OrgAccessService;
import com.example.estatex.organisation.OrgPermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PropertyService {
    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);
    private static final long MAX_PHOTO_SIZE = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final PropertyRepository properties;
    private final PropertyPhotoRepository photos;
    private final PropertyInquiryRepository inquiries;
    private final UserService users;
    private final ObjectStorageService objectStorage;
    private final PropertyBillingService billing;
    private final GeocodingService geocoding;
    private final OrgAccessService orgAccess;
    private final ApplicationEventPublisher events;
    private final Path photoStorageRoot;
    private final String publicBaseUrl;

    public PropertyService(
            PropertyRepository properties,
            PropertyPhotoRepository photos,
            PropertyInquiryRepository inquiries,
            UserService users,
            ObjectStorageService objectStorage,
            PropertyBillingService billing,
            GeocodingService geocoding,
            OrgAccessService orgAccess,
            ApplicationEventPublisher events,
            @Value("${app.storage.property-photos:storage/property-photos}") String photoStorageRoot,
            @Value("${app.public-base-url:http://localhost:8081}") String publicBaseUrl
    ) {
        this.properties = properties;
        this.photos = photos;
        this.inquiries = inquiries;
        this.users = users;
        this.objectStorage = objectStorage;
        this.billing = billing;
        this.geocoding = geocoding;
        this.orgAccess = orgAccess;
        this.events = events;
        this.photoStorageRoot = Path.of(photoStorageRoot);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Property create(PropertyDtos.CreatePropertyRequest request) {
        return create(request, null, null);
    }

    /**
     * Creates an independent listing ({@code organisationId == null}: live immediately and billed
     * per listing, as before) or an organisation listing, which starts as DRAFT and only goes live
     * through the workspace publish flow once every compliance gate is met. Organisation listings
     * aren't billed per listing.
     */
    @Transactional
    public Property create(PropertyDtos.CreatePropertyRequest request, Long organisationId, TitleType titleType) {
        AppUser landlord = users.require(request.landlordId());
        boolean organisationListing = organisationId != null;
        if (!organisationListing && !landlord.hasPermission(Permission.PROPERTY_CREATE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "landlordId must belong to a landlord, agent, developer, or private seller");
        }

        Property property = new Property();
        property.setTitle(request.title());
        property.setDescription(request.description());
        property.setListingType(request.listingType());
        property.setStatus(organisationListing ? PropertyStatus.DRAFT : PropertyStatus.AVAILABLE);
        property.setOrganisationId(organisationId);
        property.setTitleType(titleType);
        property.setCity(request.city());
        property.setSuburb(request.suburb());
        property.setAddress(request.address());
        property.setCountry(request.country() == null || request.country().isBlank() ? "Zimbabwe" : request.country());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setPrice(request.price());
        property.setCurrency(request.currency() == null || request.currency().isBlank() ? "USD" : request.currency());
        Coordinates coordinates = coordinatesFor(request.address(), request.suburb(), request.city(), property.getCountry(),
                request.latitude(), request.longitude());
        property.setLatitude(coordinates.latitude());
        property.setLongitude(coordinates.longitude());
        property.setDiasporaFriendly(request.diasporaFriendly());
        property.setEscrowRequired(request.escrowRequired());
        property.setSolarInstalled(request.solarInstalled());
        property.setBackupPower(request.backupPower());
        property.setWaterSource(request.waterSource());
        property.setFurnished(request.furnished());
        property.setInternetAvailable(request.internetAvailable());
        property.setSecurityFeatures(request.securityFeatures());
        property.setParkingAvailable(request.parkingAvailable());
        property.setPetsAllowed(request.petsAllowed());
        property.setVirtualTourUrl(request.virtualTourUrl());
        property.setLandlord(landlord);
        if (request.agentId() != null) {
            AppUser agent = users.require(request.agentId());
            if (!agent.getRoles().contains(UserRole.AGENT)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "agentId must belong to an agent user");
            }
            property.setAgent(agent);
        }
        Property saved = properties.save(property);
        if (!organisationListing) {
            billing.chargeForNewListing(saved, landlord);
        }
        savePhotoUrls(saved, requestPhotoUrls(request));
        return saved;
    }

    @Transactional
    public Property update(Long id, PropertyDtos.UpdatePropertyRequest request, AppUser currentUser) {
        Property property = require(id);
        requireOwnerOrAdmin(property, currentUser);
        if (property.isOrganisationListing() && request.status() == PropertyStatus.AVAILABLE
                && property.getStatus() != PropertyStatus.AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Agency listings go live through the workspace publish step, once every compliance check is met.");
        }
        boolean priceChanged = request.price() != null
                && (property.getPrice() == null || request.price().compareTo(property.getPrice()) != 0);
        if (request.title() != null) property.setTitle(request.title());
        if (request.description() != null) property.setDescription(request.description());
        if (request.listingType() != null) property.setListingType(request.listingType());
        boolean locationTextChanged = changed(request.address(), property.getAddress())
                || changed(request.suburb(), property.getSuburb())
                || changed(request.city(), property.getCity());
        if (request.city() != null) property.setCity(request.city());
        if (request.suburb() != null) property.setSuburb(request.suburb());
        if (request.address() != null) property.setAddress(request.address());
        if (request.country() != null) property.setCountry(request.country());
        if (request.bedrooms() != null) property.setBedrooms(request.bedrooms());
        if (request.bathrooms() != null) property.setBathrooms(request.bathrooms());
        if (request.price() != null) property.setPrice(request.price());
        if (request.currency() != null) property.setCurrency(request.currency());
        if (request.latitude() != null && request.longitude() != null) {
            property.setLatitude(request.latitude());
            property.setLongitude(request.longitude());
        } else if (locationTextChanged) {
            // The address moved but no pin came with it: re-locate from the new address.
            Coordinates coordinates = coordinatesFor(property.getAddress(), property.getSuburb(), property.getCity(),
                    property.getCountry(), null, null);
            property.setLatitude(coordinates.latitude());
            property.setLongitude(coordinates.longitude());
        }
        if (request.diasporaFriendly() != null) property.setDiasporaFriendly(request.diasporaFriendly());
        if (request.escrowRequired() != null) property.setEscrowRequired(request.escrowRequired());
        if (request.solarInstalled() != null) property.setSolarInstalled(request.solarInstalled());
        if (request.backupPower() != null) property.setBackupPower(request.backupPower());
        if (request.waterSource() != null) property.setWaterSource(request.waterSource());
        if (request.furnished() != null) property.setFurnished(request.furnished());
        if (request.internetAvailable() != null) property.setInternetAvailable(request.internetAvailable());
        if (request.securityFeatures() != null) property.setSecurityFeatures(request.securityFeatures());
        if (request.parkingAvailable() != null) property.setParkingAvailable(request.parkingAvailable());
        if (request.petsAllowed() != null) property.setPetsAllowed(request.petsAllowed());
        if (request.virtualTourUrl() != null) property.setVirtualTourUrl(request.virtualTourUrl());
        if (request.status() != null) property.setStatus(request.status());
        if (property.isOrganisationListing() && (priceChanged || locationTextChanged)) {
            List<String> fields = new java.util.ArrayList<>();
            if (priceChanged) fields.add("price");
            if (locationTextChanged) fields.add("address");
            events.publishEvent(new PropertyMaterialChangeEvent(property.getId(), fields, currentUser));
        }
        return property;
    }

    @Transactional
    public Property deletePhoto(Long propertyId, Long photoId, AppUser currentUser) {
        Property property = require(propertyId);
        requireOwnerOrAdmin(property, currentUser);
        PropertyPhoto photo = property.getPhotos().stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found"));
        if (photo.getStorageKey() != null) {
            if (objectStorage.isConfigured()) {
                objectStorage.delete(photo.getStorageKey());
            } else {
                try {
                    Files.deleteIfExists(Path.of(photo.getStorageKey()));
                } catch (IOException ignored) {
                    // best-effort cleanup; the DB row is still removed either way
                }
            }
        }
        property.getPhotos().remove(photo);
        photos.delete(photo);
        return property;
    }

    @Transactional(readOnly = true)
    public PropertyBillingDtos.PropertyBillingResponse billingStatus(Long id, AppUser currentUser) {
        Property property = require(id);
        requireOwnerOrAdmin(property, currentUser);
        return billing.describe(property);
    }

    private void requireOwnerOrAdmin(Property property, AppUser currentUser) {
        if (currentUser == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to edit listings");
        }
        if (property.isOrganisationListing()
                && orgAccess.can(currentUser, property.getOrganisationId(), OrgPermission.LISTING_EDIT)) {
            return;
        }
        boolean isOwner = property.getLandlord().getId().equals(currentUser.getId())
                || (property.getAgent() != null && property.getAgent().getId().equals(currentUser.getId()));
        boolean isAdmin = currentUser.hasPermission(Permission.ADMIN_OVERRIDE);
        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the listing's landlord or agent can edit it");
        }
    }

    /** Called right after creation when the landlord's plan doesn't include escrow - keeps the
     * property record honest so the "Escrow Protected" badge never promises something the
     * landlord's plan can't actually deliver. */
    @Transactional
    public Property forceDisableEscrow(Property property) {
        property.setEscrowRequired(false);
        return properties.save(property);
    }

    /**
     * Bytes of a photo this platform stored itself (local disk inside the photo root, or our S3
     * bucket). Photos that are only external URLs are never fetched server-side (no SSRF).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<byte[]> readStoredPhoto(Long propertyId) {
        Property property = require(propertyId);
        for (PropertyPhoto photo : property.getPhotos()) {
            String key = photo.getStorageKey();
            if (key == null || key.startsWith("http://") || key.startsWith("https://")) {
                continue;
            }
            try {
                if (objectStorage.isConfigured()) {
                    return java.util.Optional.of(objectStorage.getBytes(key));
                }
                Path root = photoStorageRoot.toAbsolutePath().normalize();
                Path file = Path.of(key).toAbsolutePath().normalize();
                if (file.startsWith(root) && Files.isRegularFile(file)) {
                    return java.util.Optional.of(Files.readAllBytes(file));
                }
            } catch (Exception e) {
                log.debug("Could not read stored photo {} for property {}: {}", photo.getId(), propertyId, e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }

    /** Used by the agency title-verification flow, which is what "verified listing" means for org listings. */
    @Transactional
    public void setVerification(Property property, VerificationStatus status) {
        property.setVerificationStatus(status);
        property.setVerifiedAt(status == VerificationStatus.VERIFIED ? Instant.now() : null);
    }

    /**
     * Public read. An organisation listing that hasn't been published yet (DRAFT) is invisible
     * (404) to everyone except the listing's own staff, so unapproved listings never leak.
     */
    @Transactional(readOnly = true)
    public Property requireVisible(Long id, AppUser viewer) {
        Property property = require(id);
        if (property.isOrganisationListing() && property.getStatus() == PropertyStatus.DRAFT
                && !orgAccess.can(viewer, property.getOrganisationId(), OrgPermission.ORG_VIEW)
                && (viewer == null || !viewer.hasPermission(Permission.ADMIN_OVERRIDE))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Property not found");
        }
        return property;
    }

    public Property require(Long id) {
        return properties.findWithPhotosById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Property not found"));
    }

    /** Internal callers (AI search, rent pricing comparables) don't paginate - bound them to a
     * sane page so a broad query can never pull the entire table into memory. MAX_PAGE_SIZE is a
     * hard ceiling shared by both the paginated browse endpoint and legacy "give me everything"
     * callers, so no query - however it's invoked - can ever pull the whole table into memory. */
    private static final int INTERNAL_SEARCH_LIMIT = 50;
    private static final int MAX_PAGE_SIZE = 500;

    public List<Property> search(
            ListingType listingType,
            String city,
            String suburb,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            Integer bathrooms,
            Boolean diasporaFriendly
    ) {
        return search(listingType, city, suburb, minPrice, maxPrice, bedrooms, bathrooms, diasporaFriendly,
                null, null, null, null, null, null, null, null, null, null);
    }

    public List<Property> search(
            ListingType listingType,
            String city,
            String suburb,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            Integer bathrooms,
            Boolean diasporaFriendly,
            Boolean solarInstalled,
            Boolean backupPower,
            WaterSource waterSource,
            Boolean furnished,
            Boolean internetAvailable,
            Boolean securityFeatures,
            Boolean parkingAvailable,
            Boolean petsAllowed,
            Boolean verifiedOnly,
            Boolean escrowAvailable
    ) {
        return searchPage(listingType, city, suburb, minPrice, maxPrice, bedrooms, bathrooms, diasporaFriendly,
                solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
                parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable, 0, INTERNAL_SEARCH_LIMIT)
                .getContent();
    }

    public Page<Property> searchPage(
            ListingType listingType,
            String city,
            String suburb,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer bedrooms,
            Integer bathrooms,
            Boolean diasporaFriendly,
            Boolean solarInstalled,
            Boolean backupPower,
            WaterSource waterSource,
            Boolean furnished,
            Boolean internetAvailable,
            Boolean securityFeatures,
            Boolean parkingAvailable,
            Boolean petsAllowed,
            Boolean verifiedOnly,
            Boolean escrowAvailable,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return properties.search(listingType, blankToNull(city), blankToNull(suburb), minPrice, maxPrice, bedrooms, bathrooms,
                diasporaFriendly, solarInstalled, backupPower, waterSource, furnished, internetAvailable, securityFeatures,
                parkingAvailable, petsAllowed, verifiedOnly, escrowAvailable, pageable);
    }

    public List<Property> forLandlord(Long landlordId) {
        return properties.findByLandlord(users.require(landlordId));
    }

    public List<Property> forAgent(Long agentId) {
        return properties.findByAgent(users.require(agentId));
    }

    @Transactional(readOnly = true)
    public java.util.Map<PropertyStatus, Long> statusCounts() {
        java.util.EnumMap<PropertyStatus, Long> counts = new java.util.EnumMap<>(PropertyStatus.class);
        for (PropertyStatus status : PropertyStatus.values()) {
            counts.put(status, properties.countByStatus(status));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Property> recentlyListed() {
        return properties.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void submitInquiry(Long id, PropertyDtos.InquiryRequest request) {
        Property property = requireVisible(id, null);
        PropertyInquiry inquiry = new PropertyInquiry();
        inquiry.setProperty(property);
        inquiry.setName(request.name());
        inquiry.setEmail(request.email());
        inquiry.setPhone(request.phone());
        inquiry.setMessage(request.message());
        inquiries.save(inquiry);
        if (property.isOrganisationListing()) {
            events.publishEvent(new PropertyInquirySubmittedEvent(property.getId(), property.getOrganisationId(),
                    request.name(), request.email(), request.phone(), request.message()));
        }
        log.info("Inquiry for property '{}' (id={}): from {} <{}> phone={} - {}",
                property.getTitle(), id, request.name(), request.email(), request.phone(), request.message());
    }

    @Transactional(readOnly = true)
    public List<PropertyDtos.InquiryResponse> inquiriesFor(AppUser currentUser) {
        return inquiries.findForOwner(currentUser.getId()).stream()
                .map(PropertyDtos.InquiryResponse::from)
                .toList();
    }

    @Transactional
    public Property uploadPhotos(Long id, List<MultipartFile> files, AppUser currentUser) {
        Property property = require(id);
        requireOwnerOrAdmin(property, currentUser);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one photo is required");
        }
        if (!objectStorage.isConfigured()) {
            try {
                Files.createDirectories(photoStorageRoot.resolve(String.valueOf(id)));
            } catch (IOException ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not prepare photo storage");
            }
        }

        int nextSortOrder = property.getPhotos().size();
        for (MultipartFile file : files) {
            validatePhoto(file);
            String originalName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "photo"
                    : Path.of(file.getOriginalFilename()).getFileName().toString();
            String storageName = UUID.randomUUID() + "-" + originalName;
            String storageKey;
            if (objectStorage.isConfigured()) {
                storageKey = id + "/" + storageName;
                objectStorage.put(storageKey, file);
            } else {
                Path target = photoStorageRoot.resolve(String.valueOf(id)).resolve(storageName);
                try {
                    Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded photo");
                }
                storageKey = target.toString();
            }
            PropertyPhoto photo = new PropertyPhoto();
            photo.setProperty(property);
            photo.setStorageKey(storageKey);
            photo.setPhotoUrl(publicBaseUrl + "/uploads/property-photos/" + id + "/" + storageName);
            photo.setSortOrder(nextSortOrder++);
            photo.setPrimaryPhoto(property.getPhotos().isEmpty() && nextSortOrder == 1);
            photos.save(photo);
            property.getPhotos().add(photo);
        }
        return property;
    }

    @Transactional
    public Property verify(Long id, PropertyDtos.VerifyPropertyRequest request) {
        AppUser verifier = users.require(request.verifierId());
        if (!verifier.hasPermission(Permission.PROPERTY_VERIFY)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only agents or admins can verify properties");
        }
        Property property = require(id);
        property.setVerificationStatus(VerificationStatus.VERIFIED);
        property.setVerifiedAt(Instant.now());
        return property;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> requestPhotoUrls(PropertyDtos.CreatePropertyRequest request) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        addAll(urls, request.photoUrls());
        addAll(urls, request.imageUrls());
        addAll(urls, request.photos());
        return urls.stream().toList();
    }

    private void addAll(Set<String> urls, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(urls::add);
    }

    private void savePhotoUrls(Property property, List<String> urls) {
        int sortOrder = property.getPhotos().size();
        for (String url : urls) {
            PropertyPhoto photo = new PropertyPhoto();
            photo.setProperty(property);
            photo.setPhotoUrl(url);
            photo.setStorageKey(url);
            photo.setSortOrder(sortOrder++);
            photo.setPrimaryPhoto(property.getPhotos().isEmpty());
            photos.save(photo);
            property.getPhotos().add(photo);
        }
    }

    private void validatePhoto(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded photo is empty");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Photo size must be 8MB or less");
        }
        if (!ALLOWED_PHOTO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PNG, JPEG, and WebP photos are allowed");
        }
    }

    public List<GeocodingService.GeocodeResult> geocode(String address, String suburb, String city, String country) {
        return geocoding.candidates(address, suburb, city, country, 5);
    }

    private static boolean changed(String requested, String current) {
        return requested != null && !requested.trim().equalsIgnoreCase(current == null ? "" : current.trim());
    }

    /** Owner-supplied pin first, then the ArcGIS geocoder, then a rough suburb centroid. */
    Coordinates coordinatesFor(String address, String suburb, String city, String country,
                               BigDecimal latitude, BigDecimal longitude) {
        if (latitude != null && longitude != null) {
            return new Coordinates(latitude, longitude);
        }
        Optional<GeocodingService.GeocodeResult> geocoded = geocoding.geocode(address, suburb, city, country);
        if (geocoded.isPresent()) {
            return new Coordinates(geocoded.get().latitude(), geocoded.get().longitude());
        }
        String key = (suburb == null ? "" : suburb.trim().toLowerCase()) + ","
                + (city == null ? "" : city.trim().toLowerCase());
        return switch (key) {
            case "borrowdale,harare" -> new Coordinates(new BigDecimal("-17.742"), new BigDecimal("31.096"));
            case "avondale,harare" -> new Coordinates(new BigDecimal("-17.796"), new BigDecimal("31.035"));
            case "mount pleasant,harare" -> new Coordinates(new BigDecimal("-17.764"), new BigDecimal("31.053"));
            case "newlands,harare" -> new Coordinates(new BigDecimal("-17.803"), new BigDecimal("31.071"));
            case "hillside,bulawayo" -> new Coordinates(new BigDecimal("-20.187"), new BigDecimal("28.606"));
            case "victoria falls town," -> new Coordinates(new BigDecimal("-17.932"), new BigDecimal("25.831"));
            case "victoria falls town,victoria falls" -> new Coordinates(new BigDecimal("-17.932"), new BigDecimal("25.831"));
            case "murambi,mutare" -> new Coordinates(new BigDecimal("-18.970"), new BigDecimal("32.669"));
            default -> new Coordinates(latitude, longitude);
        };
    }

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
